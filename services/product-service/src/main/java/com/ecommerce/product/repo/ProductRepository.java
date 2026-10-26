package com.ecommerce.product.repo;

import com.ecommerce.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id=:id")
    Optional<Product> lock(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Product> findByName(String name);

    @Query("select p.category, count(p) from Product p group by p.category")
    List<Object[]> countByCategory();
}
