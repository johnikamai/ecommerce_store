package com.ecommerce.auth.repo;

import com.ecommerce.auth.model.Role;
import com.ecommerce.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    /** Used to refuse demoting the final admin, which would leave nobody in charge. */
    long countByRole(Role role);
}
