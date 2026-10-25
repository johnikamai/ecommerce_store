package com.ecommerce.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.*;
import java.math.BigDecimal;
import java.util.*;

@Component
public class ProductClient {
    private final RestTemplate http;
    private final String key;
    public ProductClient(RestTemplate http, @Value("${services.internal-key}") String key) { this.http=http; this.key=key; }
    public Optional<ProductView> findById(Long id) {
        try { return Optional.ofNullable(http.getForObject("http://product-service/api/products/"+id, ProductView.class)); }
        catch (HttpClientErrorException.NotFound missing) { return Optional.empty(); }
    }
    public void applyPrice(Long approvalId, ProductView product, BigDecimal previousPrice) {
        HttpHeaders headers=new HttpHeaders(); headers.set("X-Service-Key",key);
        // A stable operation ID lets product-service retry safely after a lost response.
        http.exchange("http://product-service/internal/products/"+product.getId()+"/price", HttpMethod.PUT,
            new HttpEntity<>(Map.of("operationId","staff-approval-"+approvalId,"price",product.getPrice(),"expectedPrice",previousPrice),headers), Void.class);
    }
}
