package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByName(String name);

    @Query("select p.category, count(p) from Product p group by p.category")
    List<Object[]> countByCategory();
}
