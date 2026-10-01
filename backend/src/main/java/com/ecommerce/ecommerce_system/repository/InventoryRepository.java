package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(Long productId);
}