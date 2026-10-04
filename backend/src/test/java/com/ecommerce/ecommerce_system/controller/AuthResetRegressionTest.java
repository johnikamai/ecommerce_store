package com.ecommerce.ecommerce_system.controller;
import com.ecommerce.ecommerce_system.model.User;
import com.ecommerce.ecommerce_system.repository.UserRepository;
import com.ecommerce.ecommerce_system.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class AuthResetRegressionTest {
    @Mock UserRepository users;
    @Mock OtpService otp;
    @Mock MailService mail;
    @InjectMocks AuthController controller;
    @Test void mailFailureNeverReturnsResetCode() {
        User user = new User(); user.setUsername("ruby"); user.setEmail("ruby@example.test"); user.setEnabled(true);
        when(users.findByUsername("ruby")).thenReturn(Optional.of(user));
        when(otp.issue("RESET:ruby")).thenReturn("123456");
        when(mail.sendOtp("ruby@example.test", "123456")).thenReturn(false);
        var response = controller.forgotPassword(Map.of("username", "ruby"));
        assertEquals(503, response.getStatusCode().value());
        assertFalse(String.valueOf(response.getBody()).contains("123456"));
        assertFalse(String.valueOf(response.getBody()).contains("devOtp"));
    }
}
