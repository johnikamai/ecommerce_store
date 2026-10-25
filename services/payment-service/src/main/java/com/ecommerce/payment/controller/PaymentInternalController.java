package com.ecommerce.payment.controller;
import com.ecommerce.payment.model.*;
import com.ecommerce.payment.repo.*;
import com.ecommerce.payment.service.PaymentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;
@RestController @RequestMapping("/internal")
public class PaymentInternalController {
    private final PaymentService service;private final PaymentRepository payments;private final CouponRepository coupons;private final GroupPaymentRepository groupPayments;
    public PaymentInternalController(PaymentService service,PaymentRepository payments,CouponRepository coupons,GroupPaymentRepository groupPayments){this.service=service;this.payments=payments;this.coupons=coupons;this.groupPayments=groupPayments;}
    @PostMapping("/events")
    @org.springframework.transaction.annotation.Transactional
    public void event(@RequestBody com.fasterxml.jackson.databind.JsonNode event){
        if(!event.path("kind").asText().equals("ORDER_STATUS"))throw new IllegalArgumentException("Unknown payment event");var data=event.path("payload");
        payments.lockOrder(data.path("orderId").asLong()).ifPresent(payment->{var order=payment.getOrder();if(order!=null&&data.path("version").asLong()>=order.getVersion()){order.setVersion(data.path("version").asLong());order.setStatus(OrderStatus.valueOf(data.path("status").asText()));order.setPaymentStatus(payment.getPaymentStatus());payments.save(payment);}});
    }
    @PostMapping("/payments/order")public Payment create(@RequestBody Order order){return service.create(order,"checkout");}
    @GetMapping("/payments/order/{id}")public ResponseEntity<?> order(@PathVariable Long id){return payments.findByOrderId(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());}
    @GetMapping("/payments/{id}")public ResponseEntity<?> payment(@PathVariable Long id){return payments.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());}
    @PostMapping("/payments/order/{id}/cancel")public ResponseEntity<?> cancel(@PathVariable Long id){return service.cancel(id,"order-cancel").map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());}
    @PutMapping("/payments/{id}/status")public Payment status(@PathVariable Long id,@RequestBody Map<String,String> body){return service.status(id,PaymentStatus.valueOf(body.get("status")),body.get("transactionRef"),body.getOrDefault("actor","order-service"));}
    @PutMapping("/payments/order/snapshot")public void snapshot(@RequestBody Order order){service.snapshot(order);}
    @GetMapping("/coupons/{code}")public ResponseEntity<?> coupon(@PathVariable String code){return coupons.findByCodeIgnoreCase(code).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());}
    @PostMapping("/coupons/reservations")public Object reserve(@RequestBody PaymentService.CouponRequest body){return service.reserve(body);}
    @PostMapping("/coupons/reservations/{id}/commit")public Object commit(@PathVariable String id){return service.finishCoupon(id,true);}
    @PostMapping("/coupons/reservations/{id}/release")public Object release(@PathVariable String id){return service.finishCoupon(id,false);}
    @PostMapping("/group-payments")public GroupPayment group(@RequestBody GroupPayment body){return service.saveGroup(body);}
    @GetMapping("/group-payments/group/{id}")public Object groups(@PathVariable Long id){return groupPayments.findByGroupOrderId(id);}
    @GetMapping("/group-payments/group/{id}/member/{memberId}")public ResponseEntity<?> member(@PathVariable Long id,@PathVariable Long memberId){return groupPayments.findByGroupOrderIdAndMemberId(id,memberId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());}
}
