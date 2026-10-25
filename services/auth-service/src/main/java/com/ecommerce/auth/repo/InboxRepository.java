package com.ecommerce.auth.repo;
import com.ecommerce.auth.model.InboxReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InboxRepository extends JpaRepository<InboxReceipt,String> {}
