package com.ecommerce.payment.service;
import com.ecommerce.payment.model.*;
import com.ecommerce.payment.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class PaymentService {
    public record CouponRequest(String operationId,String code,BigDecimal subtotal,BigDecimal expectedDiscount){}
    private final PaymentRepository payments;private final CouponRepository coupons;private final CouponReservationRepository reservations;private final GroupPaymentRepository groupPayments;
    @Value("${app.payments.demo-enabled:false}")private boolean demoEnabled;
    public PaymentService(PaymentRepository payments,CouponRepository coupons,CouponReservationRepository reservations,GroupPaymentRepository groupPayments){this.payments=payments;this.coupons=coupons;this.reservations=reservations;this.groupPayments=groupPayments;}
    private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    @Transactional
    public Payment create(Order order,String actor){
        if(order==null||order.getId()==null||order.getCustomer()==null||order.getCustomer().getId()==null||order.getTotalAmount()==null||order.getTotalAmount().signum()<0)throw new IllegalArgumentException("Complete order snapshot required");
        var existing=payments.lockOrder(order.getId());
        if(existing.isPresent()){var p=existing.get();if(p.getAmount().compareTo(order.getTotalAmount())!=0||!p.getCustomerId().equals(order.getCustomer().getId()))throw conflict("Payment snapshot differs from existing order");return p;}
        PaymentMode mode=PaymentMode.valueOf(order.getPaymentMethod()==null?"CASH":order.getPaymentMethod());
        if(mode!=PaymentMode.CASH&&!demoEnabled)throw new IllegalArgumentException("Online demo payments are disabled");
        var payment=new Payment();payment.setOrder(order);payment.setAmount(order.getTotalAmount());payment.setPaymentMode(mode);payment.setPaymentStatus(mode==PaymentMode.CASH?PaymentStatus.PENDING:PaymentStatus.PAID);
        if(mode!=PaymentMode.CASH)payment.setTransactionRef("MOCK-"+order.getId()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase());
        payment.setStatusUpdatedBy(actor);payment.setStatusUpdatedAt(LocalDateTime.now());payment.getOrder().setPaymentStatus(payment.getPaymentStatus());return payments.saveAndFlush(payment);
    }
    @Transactional
    public Payment status(Long id,PaymentStatus target,String reference,String actor){
        var payment=payments.lock(id).orElseThrow();var current=payment.getPaymentStatus();
        if(target==null)throw new IllegalArgumentException("Payment status required");
        if(target!=current&&!current.canTransitionTo(target))throw conflict("Cannot change payment from "+current+" to "+target);
        payment.setPaymentStatus(target);if(reference!=null&&!reference.isBlank())payment.setTransactionRef(reference.trim());payment.setStatusUpdatedBy(actor);payment.setStatusUpdatedAt(LocalDateTime.now());if(payment.getOrder()!=null)payment.getOrder().setPaymentStatus(target);return payments.saveAndFlush(payment);
    }
    @Transactional
    public Optional<Payment> cancel(Long orderId,String actor){
        return payments.lockOrder(orderId).map(payment->{
            var current=payment.getPaymentStatus();if(current==PaymentStatus.PAID)payment.setPaymentStatus(PaymentStatus.REFUNDED);else if(current==PaymentStatus.PENDING||current==PaymentStatus.FAILED)payment.setPaymentStatus(PaymentStatus.CANCELLED);
            payment.setStatusUpdatedBy(actor);payment.setStatusUpdatedAt(LocalDateTime.now());if(payment.getOrder()!=null)payment.getOrder().setPaymentStatus(payment.getPaymentStatus());return payments.saveAndFlush(payment);
        });
    }
    @Transactional
    public void snapshot(Order snapshot){
        payments.lockOrder(snapshot.getId()).ifPresent(payment->{
            if(payment.getAmount().compareTo(snapshot.getTotalAmount())!=0||!payment.getCustomerId().equals(snapshot.getCustomer().getId()))throw conflict("Financial snapshot cannot be changed");
            if(payment.getOrder()==null||snapshot.getVersion()>=payment.getOrder().getVersion()){snapshot.setPaymentStatus(payment.getPaymentStatus());payment.setOrder(snapshot);payments.save(payment);}
        });
    }
    public BigDecimal discount(Coupon coupon,BigDecimal subtotal){
        if(subtotal==null||subtotal.signum()<0)throw new IllegalArgumentException("Valid subtotal required");
        if(!coupon.isRedeemable())throw conflict("Coupon is no longer valid");
        if(coupon.getMinimumOrderAmount()!=null&&subtotal.compareTo(coupon.getMinimumOrderAmount())<0)throw conflict("Coupon minimum order is not met");
        return (coupon.getDiscountType()==CouponType.PERCENT?subtotal.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP):coupon.getDiscountValue()).min(subtotal);
    }
    private String hash(CouponRequest r){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((r.code().trim().toUpperCase()+":"+r.subtotal().stripTrailingZeros().toPlainString()+":"+r.expectedDiscount().stripTrailingZeros().toPlainString()).getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalArgumentException("Invalid coupon reservation");}}
    @Transactional
    public CouponReservation reserve(CouponRequest request){
        if(request.operationId()==null||!request.operationId().matches("[A-Za-z0-9:_-]{1,128}")||request.code()==null||request.subtotal()==null||request.expectedDiscount()==null)throw new IllegalArgumentException("Complete coupon reservation required");
        var coupon=coupons.findByCodeIgnoreCaseForUpdate(request.code().trim()).orElseThrow(()->new IllegalArgumentException("Coupon not found"));var existing=reservations.lock(request.operationId());String fingerprint=hash(request);
        if(existing.isPresent()){var receipt=existing.get();if(!receipt.getRequestFingerprint().equals(fingerprint)||receipt.getState().equals("RELEASED"))throw conflict("Coupon operation ID was already used");return receipt;}
        BigDecimal actual=discount(coupon,request.subtotal());if(actual.compareTo(request.expectedDiscount())!=0)throw conflict("Coupon discount changed; get a new quote");
        coupon.setTimesUsed(coupon.getTimesUsed()+1);coupons.save(coupon);var receipt=new CouponReservation();receipt.setId(request.operationId());receipt.setCouponId(coupon.getId());receipt.setDiscount(actual);receipt.setState("RESERVED");receipt.setRequestFingerprint(fingerprint);return reservations.saveAndFlush(receipt);
    }
    @Transactional
    public CouponReservation finishCoupon(String id,boolean commit){
        var known=reservations.findById(id).orElseThrow();var coupon=coupons.lock(known.getCouponId()).orElseThrow();var receipt=reservations.lock(id).orElseThrow();
        if(receipt.getState().equals("RESERVED")){if(!commit){coupon.setTimesUsed(Math.max(0,coupon.getTimesUsed()-1));coupons.save(coupon);}receipt.setState(commit?"COMMITTED":"RELEASED");reservations.save(receipt);}return receipt;
    }
    @Transactional
    public GroupPayment saveGroup(GroupPayment payment){
        if(payment.getGroupOrderId()==null||payment.getMember()==null||payment.getMember().getId()==null||payment.getAmount()==null||payment.getAmount().signum()<0)throw new IllegalArgumentException("Complete group payment required");
        var existing=groupPayments.findByGroupOrderIdAndMemberId(payment.getGroupOrderId(),payment.getMember().getId());
        if(existing.isPresent()){
            var old=existing.get();if(old.getAmount().compareTo(payment.getAmount())!=0)throw conflict("A settled group share cannot change amount");
            if(old.getStatus()!=payment.getStatus()&&!old.getStatus().canTransitionTo(payment.getStatus()))throw conflict("Invalid group payment transition");
            old.setStatus(payment.getStatus());old.setTransactionRef(payment.getTransactionRef());old.setStatusUpdatedBy(payment.getStatusUpdatedBy());old.setStatusUpdatedAt(LocalDateTime.now());return groupPayments.saveAndFlush(old);
        }
        return groupPayments.saveAndFlush(payment);
    }
}
