package com.ecommerce.order.repo;
import com.ecommerce.order.model.Product;
import com.ecommerce.common.ServiceClient;
import org.springframework.stereotype.Component;
import java.util.Optional;
@Component
public class ProductRepository {
    private final ServiceClient http;public ProductRepository(ServiceClient http){this.http=http;}
    public Optional<Product> findById(Long id){return id==null?Optional.empty():http.get("http://product-service/api/products/"+id,Product.class);}
}
