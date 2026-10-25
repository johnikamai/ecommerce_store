package com.ecommerce.auth.repo;
import com.ecommerce.auth.model.EmailChange;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EmailChangeRepository extends JpaRepository<EmailChange,String> {}
