package com.ecommerce.product.repo;
import com.ecommerce.product.model.ProductSales;
import org.springframework.data.jpa.repository.*;
import java.util.List;
public interface ProductSalesRepository extends JpaRepository<ProductSales,Long> {
    @Query("select s.productId,s.unitsSold from ProductSales s")List<Object[]> aggregate();
}
