package com.ecommerce.order.service;

import com.ecommerce.order.model.*;
import com.ecommerce.order.repo.*;
import com.ecommerce.order.client.InventoryClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;

/** Durable checkout intent plus idempotent remote steps; retries never create a second order. */
@Service
public class CheckoutCoordinator {
    private final CheckoutAttemptRepository attempts;private final OrderRepository orders;private final OrderPricingService pricing;
    private final InventoryClient inventory;private final PaymentRepository payments;private final OrderHydrator hydrator;
    private final NotificationService notifications;private final OrderEvents events;private final ObjectMapper json;private final TransactionTemplate transaction;
    private final OperationJournal journal;private final org.springframework.beans.factory.ObjectProvider<ReturnService> returns;
    public CheckoutCoordinator(CheckoutAttemptRepository attempts,OrderRepository orders,OrderPricingService pricing,InventoryClient inventory,PaymentRepository payments,OrderHydrator hydrator,NotificationService notifications,OrderEvents events,ObjectMapper json,PlatformTransactionManager manager,OperationJournal journal,org.springframework.beans.factory.ObjectProvider<ReturnService> returns){
        this.attempts=attempts;this.orders=orders;this.pricing=pricing;this.inventory=inventory;this.payments=payments;this.hydrator=hydrator;this.notifications=notifications;this.events=events;this.json=json;
        this.journal=journal;this.returns=returns;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    private String encode(Object object){try{return json.writeValueAsString(object);}catch(Exception error){throw new IllegalArgumentException("Invalid checkout payload",error);}}
    private Order snapshot(CheckoutAttempt attempt){try{var order=json.readValue(attempt.getSnapshot(),Order.class);order.getOrderItems().forEach(i->i.setOrder(order));return order;}catch(Exception error){throw new IllegalStateException("Stored checkout snapshot is invalid",error);}}
    private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalArgumentException("Invalid checkout payload",error);}}
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Order place(OrderRequest request){
        String key=request.getOperationId()==null?"checkout:"+UUID.randomUUID():request.getOperationId();if(!key.matches("[A-Za-z0-9:_-]{1,128}"))throw new IllegalArgumentException("Invalid checkout operation ID");request.setOperationId(key);String fingerprint=hash(request);
        CheckoutAttempt attempt=attempts.findById(key).orElse(null);
        if(attempt==null){
            Order prepared=pricing.prepareOrder(request);if(request.getExpectedTotal()==null||prepared.getTotalAmount().compareTo(request.getExpectedTotal())!=0)throw new IllegalArgumentException("Checkout total changed. Review the updated quote and try again.");
            String preparedJson=encode(prepared);
            attempt=transaction.execute(s->{var existing=attempts.lock(key);if(existing.isPresent())return existing.get();var row=new CheckoutAttempt();row.setId(key);row.setRequestHash(fingerprint);row.setSnapshot(preparedJson);return attempts.saveAndFlush(row);});
        }
        if(!attempt.getRequestHash().equals(fingerprint))throw new ResponseStatusException(HttpStatus.CONFLICT,"Checkout operation ID was used with different inputs");
        if(attempt.getState().equals("FAILED"))throw new ResponseStatusException(HttpStatus.CONFLICT,attempt.getError());
        try{resume(attempt.getId());}catch(ResponseStatusException failure){defer(attempt.getId(),failure);throw failure;}
        var finalState=attempts.findById(key).orElseThrow();if(finalState.getState().equals("FAILED"))throw new ResponseStatusException(HttpStatus.CONFLICT,finalState.getError());
        return hydrator.load(finalState.getOrderId());
    }
    private void defer(String id,RuntimeException failure){transaction.executeWithoutResult(s->{var a=attempts.lock(id).orElseThrow();if(!a.getState().equals("COMPLETE")&&!a.getState().equals("FAILED")){a.setAttempts(a.getAttempts()+1);a.setNextAttemptAt(Instant.now().plusSeconds(Math.min(60,5L*a.getAttempts())));a.setError("Required service operation pending");attempts.save(a);}});}
    private void reject(String id,String error){transaction.executeWithoutResult(s->{var a=attempts.lock(id).orElseThrow();a.setState("FAILED");a.setError(error);attempts.save(a);});}
    private void resume(String key){
        var attempt=attempts.findById(key).orElseThrow();if(attempt.getState().equals("COMPLETE")||attempt.getState().equals("FAILED"))return;
        if(attempt.getKind().equals("CANCEL")){resumeCancel(attempt);return;}
        if(attempt.getKind().equals("PAYMENT_STATUS")){resumePayment(attempt);return;}
        if(attempt.getKind().equals("RETURN_PROCESS")||attempt.getKind().equals("RETURN_REFUND")){
            try{var body=json.readTree(attempt.getSnapshot());if(attempt.getKind().equals("RETURN_PROCESS"))returns.getObject().process(body.path("returnId").asLong(),body.path("approve").asBoolean());else returns.getObject().markRefunded(body.path("returnId").asLong());return;}catch(java.io.IOException invalid){throw new IllegalStateException(invalid);}
        }
        Order prepared=snapshot(attempt);
        if(attempt.getOrderId()==null){
            try{inventory.reserve(key,prepared);}catch(ResponseStatusException failure){if(failure.getStatusCode().is4xxClientError())reject(key,"Stock or price changed; review a new quote");throw failure;}
            try{inventory.reserveCoupon(key,prepared);}catch(ResponseStatusException failure){if(failure.getStatusCode().is4xxClientError()){inventory.release(key);reject(key,"Coupon changed; review a new quote");}throw failure;}
            transaction.executeWithoutResult(s->{
                var work=attempts.lock(key).orElseThrow();if(work.getOrderId()!=null)return;
                var order=orders.findByCheckoutKey(key).orElseGet(()->{var fresh=snapshot(work);fresh.setCheckoutKey(key);fresh.setReservationId(key);fresh.setPaymentStatus(PaymentStatus.PENDING);fresh.addStatusEvent(new OrderStatusEvent(null,fresh,OrderStatus.PLACED,"Order placed",fresh.getShippingAddress(),LocalDateTime.now()));return orders.saveAndFlush(fresh);});
                work.setOrderId(order.getId());attempts.saveAndFlush(work);
            });
            attempt=attempts.findById(key).orElseThrow();
        }
        Order order=hydrator.load(attempt.getOrderId());Payment payment=payments.create(order);
        inventory.commitCoupon(key,order);inventory.commit(key);
        Long orderId=order.getId();
        transaction.executeWithoutResult(s->{var work=attempts.lock(key).orElseThrow();if(work.getState().equals("COMPLETE"))return;
            var saved=orders.lock(orderId).orElseThrow();if(saved.getStatus()==OrderStatus.CANCELLED||work.getState().equals("FAILED")){work.setState("FAILED");work.setError("Order was cancelled");attempts.save(work);return;}saved.setPaymentStatus(payment.getPaymentStatus());orders.saveAndFlush(saved);
            var customer=prepared.getCustomer();notifications.orderPlaced(customer,saved.getId(),saved.getTotalAmount().toPlainString());notifications.paymentMade(customer,saved.getId(),payment.getPaymentMode().name(),payment.getPaymentStatus().name());events.updated(saved);
            work.setState("COMPLETE");work.setError(null);attempts.save(work);
        });
    }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Order cancel(Long orderId,Long customerId,boolean admin){
        var order=hydrator.load(orderId);if(!order.getCustomerId().equals(customerId))throw new IllegalArgumentException("You can only cancel your own order");
        if(order.getStatus()==OrderStatus.CANCELLED)return order;
        if(!admin&&order.getStatus()!=OrderStatus.PLACED)throw new IllegalStateException("Only PLACED orders may be cancelled");
        if(admin&&!order.getStatus().canTransitionTo(OrderStatus.CANCELLED))throw new IllegalStateException("Order cannot be cancelled from "+order.getStatus());
        String key="cancel:"+orderId;String stored=encode(order);
        transaction.executeWithoutResult(s->{if(attempts.lock(key).isEmpty()){var row=new CheckoutAttempt();row.setId(key);row.setKind("CANCEL");row.setRequestHash(hash(Map.of("orderId",orderId)));row.setOrderId(orderId);row.setSnapshot(stored);attempts.saveAndFlush(row);}});
        try{resume(key);}catch(ResponseStatusException unavailable){defer(key,unavailable);throw unavailable;}
        return hydrator.load(orderId);
    }
    private void resumeCancel(CheckoutAttempt attempt){
        var old=snapshot(attempt);var payment=payments.cancel(old.getId());
        if(old.getReservationId()!=null)inventory.commitCoupon(old.getReservationId(),old);
        if(old.getStatus()==OrderStatus.PLACED||old.getStatus()==OrderStatus.PACKED){
            if(old.getReservationId()!=null)inventory.release(old.getReservationId());
            else for(var item:old.getOrderItems())inventory.adjust(item.getProductId(),item.getQuantity(),"cancel:"+old.getId()+":item:"+item.getId());
        }
        transaction.executeWithoutResult(s->{var work=attempts.lock(attempt.getId()).orElseThrow();if(work.getState().equals("COMPLETE"))return;
            var order=orders.lock(old.getId()).orElseThrow();order.setStatus(OrderStatus.CANCELLED);order.setPaymentStatus(payment!=null?payment.getPaymentStatus():order.getPaymentStatus()==PaymentStatus.PAID?PaymentStatus.REFUNDED:PaymentStatus.CANCELLED);order.setRewardsAwarded(false);
            order.addStatusEvent(new OrderStatusEvent(null,order,OrderStatus.CANCELLED,"Cancelled by customer",order.getShippingAddress(),LocalDateTime.now()));orders.saveAndFlush(order);events.updated(order);
            notifications.orderCancelled(old.getCustomer(),order.getId());if(order.getPaymentStatus()==PaymentStatus.REFUNDED)notifications.refundProcessed(old.getCustomer(),order.getId(),order.getTotalAmount().toPlainString());
            work.setState("COMPLETE");attempts.save(work);
            if(old.getReservationId()!=null)attempts.lock(old.getReservationId()).ifPresent(original->{if(original.getState().equals("PENDING")){original.setState("FAILED");original.setError("Order was cancelled");attempts.save(original);}});
        });
    }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Payment paymentStatus(Long id,PaymentStatus status,String reference,String actor){
        var payment=payments.findById(id).orElseThrow();var body=new LinkedHashMap<String,Object>();body.put("paymentId",id);body.put("status",status.name());body.put("reference",reference);body.put("actor",actor);
        String key="payment:"+id+":"+payment.getVersion()+":"+status.name();journal.begin(key,"PAYMENT_STATUS",body);
        resume(key);return payments.findById(id).orElseThrow();
    }
    private void resumePayment(CheckoutAttempt attempt){
        try{
            var body=json.readTree(attempt.getSnapshot());var payment=payments.findById(body.path("paymentId").asLong()).orElseThrow();payment.setPaymentStatus(PaymentStatus.valueOf(body.path("status").asText()));payment.setTransactionRef(body.path("reference").isNull()?null:body.path("reference").asText());payment.setStatusUpdatedBy(body.path("actor").asText());
            var updated=payments.save(payment);var profile=hydrator.load(updated.getOrder().getId()).getCustomer();
            transaction.executeWithoutResult(s->{var work=attempts.lock(attempt.getId()).orElseThrow();if(work.getState().equals("COMPLETE"))return;var order=orders.lock(updated.getOrder().getId()).orElseThrow();boolean changed=order.getPaymentStatus()!=updated.getPaymentStatus();order.setPaymentStatus(updated.getPaymentStatus());if(updated.getPaymentStatus()==PaymentStatus.REFUNDED)order.setRewardsAwarded(false);orders.saveAndFlush(order);events.updated(order);if(changed)notifications.paymentMade(profile,order.getId(),updated.getPaymentMode().name(),updated.getPaymentStatus().name());work.setState("COMPLETE");attempts.save(work);});
        }catch(java.io.IOException invalid){throw new IllegalStateException(invalid);}
    }
    @Scheduled(fixedDelayString="${services.checkout.retry-delay:5000}")
    public void recover(){for(var work:attempts.findTop20ByStateAndNextAttemptAtLessThanEqualOrderById("PENDING",Instant.now())){try{resume(work.getId());}catch(RuntimeException retry){defer(work.getId(),retry);}}}
}
