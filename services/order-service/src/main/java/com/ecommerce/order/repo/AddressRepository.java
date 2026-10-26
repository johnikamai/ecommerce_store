package com.ecommerce.order.repo;

import com.ecommerce.order.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByCustomerId(Long customerId);

    void deleteByCustomerId(Long customerId);
}