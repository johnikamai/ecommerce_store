package com.ecommerce.order.repo;
import com.ecommerce.order.model.*;
import com.ecommerce.common.ServiceClient;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class PaymentRepository {
    private final ServiceClient http;public PaymentRepository(ServiceClient http){this.http=http;}
    public Optional<Payment> findByOrderId(Long id){return http.get("http://payment-service/internal/payments/order/"+id,Payment.class);}
    public Optional<Payment> findById(Long id){return http.get("http://payment-service/internal/payments/"+id,Payment.class);}
    public Payment create(Order order){return http.post("http://payment-service/internal/payments/order",order,Payment.class);}
    public Payment save(Payment payment){if(payment.getId()==null)return create(payment.getOrder());var body=new LinkedHashMap<String,String>();body.put("status",payment.getPaymentStatus().name());body.put("transactionRef",payment.getTransactionRef());body.put("actor",payment.getStatusUpdatedBy()==null?"order-service":payment.getStatusUpdatedBy());return http.put("http://payment-service/internal/payments/"+payment.getId()+"/status",body,Payment.class);}
    public Payment cancel(Long orderId){return http.post("http://payment-service/internal/payments/order/"+orderId+"/cancel",Map.of(),Payment.class);}
    public void snapshot(Order order){http.put("http://payment-service/internal/payments/order/snapshot",order,Void.class);}
}
