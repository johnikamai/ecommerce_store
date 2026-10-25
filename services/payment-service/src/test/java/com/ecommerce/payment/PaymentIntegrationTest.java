package com.ecommerce.payment;
import com.ecommerce.payment.model.*;
import com.ecommerce.payment.repo.*;
import com.ecommerce.payment.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:payments-test;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","eureka.client.enabled=false","services.internal-key=test-only-internal-key-at-least-32-bytes","services.outbox.delay=1000000","app.payments.demo-enabled=true"})
class PaymentIntegrationTest {
    @Autowired PaymentService service;@Autowired PaymentRepository payments;@Autowired CouponRepository coupons;@Autowired ReceiptService receipts;
    Order order(long id,String mode){var order=new Order();order.setId(id);order.setPaymentMethod(mode);order.setTotalAmount(new BigDecimal("249.00"));order.setMerchandiseTotal(new BigDecimal("200.00"));order.setShippingAmount(new BigDecimal("49.00"));var customer=new Customer();customer.setId(77L);customer.setName("Test shopper");customer.setEmail("test@example.test");order.setCustomer(customer);var product=new Product();product.setId(8L);product.setName("Snapshot product");var item=new OrderItem();item.setProduct(product);item.setQuantity(2);item.setUnitPrice(new BigDecimal("100.00"));item.setLineTotal(new BigDecimal("200.00"));order.setOrderItems(List.of(item));return order;}
    @Test void cashLedgerCreationAndReceiptSnapshotAreIdempotent(){
        var snapshot=order(901L,"CASH");var first=service.create(snapshot,"test");var second=service.create(snapshot,"test");assertEquals(first.getId(),second.getId());assertEquals(PaymentStatus.PENDING,second.getPaymentStatus());
        var stored=payments.findById(first.getId()).orElseThrow();assertEquals("Test shopper",stored.getOrder().getCustomer().getName());assertEquals("Snapshot product",stored.getOrder().getOrderItems().get(0).getProduct().getName());assertNotNull(receipts.build(stored));
        service.status(first.getId(),PaymentStatus.PAID,"cash-receipt","test");service.cancel(snapshot.getId(),"cancel");service.cancel(snapshot.getId(),"cancel");assertEquals(PaymentStatus.REFUNDED,payments.findById(first.getId()).orElseThrow().getPaymentStatus());
        assertThrows(ResponseStatusException.class,()->service.status(first.getId(),PaymentStatus.PENDING,null,"test"));
    }
    @Test void cardAndUpiAreExplicitDemoRecords(){
        var card=service.create(order(902L,"CARD"),"test");var upi=service.create(order(903L,"UPI"),"test");assertEquals(PaymentStatus.PAID,card.getPaymentStatus());assertTrue(card.getTransactionRef().startsWith("MOCK-"));assertEquals(PaymentStatus.PAID,upi.getPaymentStatus());
    }
    Coupon coupon(){var coupon=new Coupon();coupon.setCode("TEST"+UUID.randomUUID().toString().replace("-",""));coupon.setDiscountType(CouponType.PERCENT);coupon.setDiscountValue(new BigDecimal("10.00"));coupon.setMaxUses(1);return coupons.saveAndFlush(coupon);}
    PaymentService.CouponRequest request(String id,Coupon coupon){return new PaymentService.CouponRequest(id,coupon.getCode(),new BigDecimal("100.00"),new BigDecimal("10.00"));}
    @Test void couponRetriesAndCompensationDoNotDoubleSpendQuota(){
        var coupon=coupon();var request=request(UUID.randomUUID().toString(),coupon);service.reserve(request);service.reserve(request);assertEquals(1,coupons.findById(coupon.getId()).orElseThrow().getTimesUsed());
        assertThrows(ResponseStatusException.class,()->service.reserve(request(UUID.randomUUID().toString(),coupon)));service.finishCoupon(request.operationId(),false);service.finishCoupon(request.operationId(),false);assertEquals(0,coupons.findById(coupon.getId()).orElseThrow().getTimesUsed());
        var committed=request(UUID.randomUUID().toString(),coupon);service.reserve(committed);service.finishCoupon(committed.operationId(),true);service.finishCoupon(committed.operationId(),false);assertEquals(1,coupons.findById(coupon.getId()).orElseThrow().getTimesUsed());
    }
    @Test void lastCouponUseCannotBeSpentByTwoConcurrentCheckouts()throws Exception{
        var coupon=coupon();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);List<Future<Boolean>> attempts=new ArrayList<>();
        try{for(int i=0;i<2;i++)attempts.add(pool.submit(()->{start.await();try{service.reserve(request(UUID.randomUUID().toString(),coupon));return true;}catch(ResponseStatusException exhausted){return false;}}));start.countDown();int accepted=0;for(var attempt:attempts)if(attempt.get(20,TimeUnit.SECONDS))accepted++;assertEquals(1,accepted);assertEquals(1,coupons.findById(coupon.getId()).orElseThrow().getTimesUsed());}finally{pool.shutdownNow();}
    }
}
