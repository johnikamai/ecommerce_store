package com.ecommerce.product.repo;
import com.ecommerce.product.model.ProductPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductPurchaseRepository extends JpaRepository<ProductPurchase,String> {boolean existsByCustomerIdAndProductIdAndQuantityGreaterThan(Long customerId,Long productId,Long quantity);}
