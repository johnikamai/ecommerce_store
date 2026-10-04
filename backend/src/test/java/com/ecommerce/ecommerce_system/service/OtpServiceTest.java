package com.ecommerce.ecommerce_system.service;
import com.ecommerce.ecommerce_system.model.OtpChallenge;
import com.ecommerce.ecommerce_system.repository.OtpChallengeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
class OtpServiceTest {
    @Test void codeIsHashedPersistentSingleUseAndRateLimited() {
        OtpChallengeRepository repository = mock(OtpChallengeRepository.class);
        final OtpChallenge[] saved = {null};
        when(repository.findForUpdate("ruby")).thenAnswer(i -> Optional.ofNullable(saved[0]));
        when(repository.save(any())).thenAnswer(i -> saved[0] = i.getArgument(0));
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        OtpService service = new OtpService(repository, encoder);
        String code = service.issue("ruby"); assertTrue(code.matches("[0-9]{6}"));
        assertNotEquals(code, saved[0].getCodeHash()); assertTrue(encoder.matches(code, saved[0].getCodeHash()));
        assertNull(service.issue("ruby"));
        // A different service instance reads the same persisted challenge after a restart.
        OtpService restarted = new OtpService(repository, encoder);
        assertTrue(restarted.verify("ruby", code)); assertFalse(service.verify("ruby", code));
    }
    @Test void fiveGuessesLockEvenTheCorrectCodeAndExpiredCodeFails() {
        OtpChallengeRepository repository = mock(OtpChallengeRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        OtpChallenge c = new OtpChallenge(); c.setCodeHash(encoder.encode("123456")); c.setExpiresAt(Instant.now().plusSeconds(60));
        when(repository.findForUpdate("ruby")).thenReturn(Optional.of(c));
        OtpService service = new OtpService(repository, encoder);
        for (int i=0;i<5;i++) assertFalse(service.verify("ruby", "999999"));
        assertFalse(service.verify("ruby", "123456"));
        c.setAttempts(0); c.setCodeHash(encoder.encode("123456")); c.setExpiresAt(Instant.now().minusSeconds(1));
        assertFalse(service.verify("ruby", "123456"));
    }
}
