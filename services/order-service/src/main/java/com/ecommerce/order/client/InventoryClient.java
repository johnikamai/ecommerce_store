package com.ecommerce.order.client;
import com.ecommerce.order.model.*;
import com.ecommerce.common.ServiceClient;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class InventoryClient {
    private final ServiceClient http;public InventoryClient(ServiceClient http){this.http=http;}
    public void reserve(String key,Order order){var items=order.getOrderItems().stream().map(i->Map.of("productId",i.getProduct().getId(),"quantity",i.getQuantity(),"expectedPrice",i.getUnitPrice())).toList();http.post("http://product-service/internal/inventory/reservations",Map.of("operationId",key,"customerId",order.getCustomer().getId(),"items",items),Map.class);}
    public void commit(String key){http.post("http://product-service/internal/inventory/reservations/"+key+"/commit",Map.of(),Map.class);}
    public void release(String key){http.post("http://product-service/internal/inventory/reservations/"+key+"/release",Map.of(),Map.class);}
    public void adjust(Long productId,int delta,String key){http.post("http://product-service/internal/inventory/adjust",Map.of("operationId",key,"productId",productId,"delta",delta),Map.class);}
    public void reserveCoupon(String key,Order order){if(order.getCouponCode()!=null&&!order.getCouponCode().isBlank())http.post("http://payment-service/internal/coupons/reservations",Map.of("operationId",key,"code",order.getCouponCode(),"subtotal",order.getOrderItems().stream().map(OrderItem::getLineTotal).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add),"expectedDiscount",order.getDiscountAmount()),Map.class);}
    public void commitCoupon(String key,Order order){if(order.getCouponCode()!=null&&!order.getCouponCode().isBlank())http.post("http://payment-service/internal/coupons/reservations/"+key+"/commit",Map.of(),Map.class);}
    public void releaseCoupon(String key,Order order){if(order.getCouponCode()!=null&&!order.getCouponCode().isBlank())http.post("http://payment-service/internal/coupons/reservations/"+key+"/release",Map.of(),Map.class);}
}
