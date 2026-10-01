package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByName(String name);
}
