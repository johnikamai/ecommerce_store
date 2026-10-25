package com.ecommerce.product.repo;
import com.ecommerce.product.model.OrderStatus;
import org.springframework.stereotype.Component;
import java.util.List;
/** Locally owned projections updated by order events; no order entities/tables here. */
@Component
public class OrderItemRepository {
    private final ProductSalesRepository sales;private final ProductPurchaseRepository purchases;
    public OrderItemRepository(ProductSalesRepository sales,ProductPurchaseRepository purchases){this.sales=sales;this.purchases=purchases;}
    public List<Object[]> unitsSoldByProduct(){return sales.aggregate();}
    public boolean existsByProductIdAndOrder_CustomerIdAndOrder_StatusNot(Long productId,Long customerId,OrderStatus status){return purchases.existsByCustomerIdAndProductIdAndQuantityGreaterThan(customerId,productId,0L);}
}
