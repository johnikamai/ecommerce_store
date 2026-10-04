package com.ecommerce.ecommerce_system.repository;
import com.ecommerce.ecommerce_system.model.OtpChallenge;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface OtpChallengeRepository extends JpaRepository<OtpChallenge,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OtpChallenge o where o.challengeKey = :key")
    Optional<OtpChallenge> findForUpdate(@Param("key") String key);
}
