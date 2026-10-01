package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByReferralCode(String referralCode);
}