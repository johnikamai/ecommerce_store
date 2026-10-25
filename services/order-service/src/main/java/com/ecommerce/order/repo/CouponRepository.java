package com.ecommerce.order.repo;
import com.ecommerce.order.model.Coupon;
import com.ecommerce.common.ServiceClient;
import org.springframework.stereotype.Component;
import java.util.Optional;
@Component
public class CouponRepository {
    private final ServiceClient http;public CouponRepository(ServiceClient http){this.http=http;}
    public Optional<Coupon> findByCodeIgnoreCase(String code){return http.get("http://payment-service/internal/coupons/"+java.net.URLEncoder.encode(code,java.nio.charset.StandardCharsets.UTF_8),Coupon.class);}
}
