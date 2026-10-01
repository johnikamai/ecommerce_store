package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByCustomerId(Long customerId);

    void deleteByCustomerId(Long customerId);
}