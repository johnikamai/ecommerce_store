package com.ecommerce.ecommerce_system.service;
import com.ecommerce.ecommerce_system.model.OtpChallenge;
import com.ecommerce.ecommerce_system.repository.OtpChallengeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
@Service
public class OtpService {
    private final OtpChallengeRepository repository;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    public OtpService(OtpChallengeRepository repository, PasswordEncoder encoder) {
        this.repository = repository; this.encoder = encoder;
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public String issue(String key) {
        Instant now = Instant.now();
        OtpChallenge c = repository.findForUpdate(key).orElseGet(OtpChallenge::new);
        if (c.getNextIssueAt() != null && c.getNextIssueAt().isAfter(now)) return null;
        String code = String.format("%06d", random.nextInt(1_000_000));
        c.setChallengeKey(key); c.setCodeHash(encoder.encode(code));
        c.setExpiresAt(now.plusSeconds(300)); c.setNextIssueAt(now.plusSeconds(60));
        c.setAttempts(0); repository.save(c); return code;
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean verify(String key, String code) {
        OtpChallenge c = repository.findForUpdate(key).orElse(null);
        if (c == null || c.getCodeHash() == null || c.getExpiresAt().isBefore(Instant.now()) || c.getAttempts() >= 5) return false;
        c.setAttempts(c.getAttempts() + 1);
        boolean valid = code != null && code.matches("[0-9]{6}") && encoder.matches(code, c.getCodeHash());
        if (valid || c.getAttempts() >= 5) c.setCodeHash(null);
        repository.save(c); return valid;
    }
}
