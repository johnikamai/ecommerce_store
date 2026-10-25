package com.ecommerce.auth.repo;

import com.ecommerce.auth.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Customer c where c.id=:id")
    java.util.Optional<Customer> lock(@org.springframework.data.repository.query.Param("id")Long id);
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByReferralCode(String referralCode);
}
