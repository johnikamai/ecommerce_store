package com.ecommerce.ecommerce_system.controller;
import com.ecommerce.ecommerce_system.model.AuthRequest;
import com.ecommerce.ecommerce_system.model.User;
import com.ecommerce.ecommerce_system.repository.UserRepository;
import com.ecommerce.ecommerce_system.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class AuthResetRegressionTest {
    @Mock UserRepository users;
    @Mock OtpService otp;
    @Mock MailService mail;
    @Spy BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @InjectMocks AuthController controller;
    @Test void mailFailureNeverReturnsResetCode() {
        User user = new User(); user.setUsername("ruby"); user.setEmail("ruby@example.test"); user.setEnabled(true);
        when(users.findByUsername("ruby")).thenReturn(Optional.of(user));
        when(otp.issue("RESET:ruby")).thenReturn("123456");
        when(mail.isConfigured()).thenReturn(true); // a real gateway exists...
        when(mail.sendOtp("ruby@example.test", "123456")).thenReturn(false); // ...but delivery fails
        var response = controller.forgotPassword(Map.of("username", "ruby"));
        assertEquals(503, response.getStatusCode().value());
        assertFalse(String.valueOf(response.getBody()).contains("123456"));
        assertFalse(String.valueOf(response.getBody()).contains("devOtp"));
    }

    @Test void demoModeSurfacesResetCodeOnScreen() {
        User user = new User(); user.setUsername("ruby"); user.setEmail("ruby@example.test"); user.setEnabled(true);
        when(users.findByUsername("ruby")).thenReturn(Optional.of(user));
        when(otp.issue("RESET:ruby")).thenReturn("654321");
        when(mail.isConfigured()).thenReturn(false); // demo: no gateway configured
        var response = controller.forgotPassword(Map.of("username", "ruby"));
        assertEquals(200, response.getStatusCode().value());
        String body = String.valueOf(response.getBody());
        assertTrue(body.contains("devOtp"));
        assertTrue(body.contains("654321"));
        assertTrue(body.contains("emailDelivered"));
    }

    @Test void demoModeSurfacesRegisterCodeOnScreen() {
        when(users.findByUsername("mam")).thenReturn(Optional.empty());
        when(otp.issue("mam")).thenReturn("111222");
        when(mail.isConfigured()).thenReturn(false);
        AuthRequest req = new AuthRequest();
        req.setUsername("mam"); req.setEmail("mam@example.test"); req.setPassword("StrongPass1!");
        var response = controller.register(req);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(String.valueOf(response.getBody()).contains("devOtp"));
        assertTrue(String.valueOf(response.getBody()).contains("111222"));
    }

    @Test void configuredGatewayStill503AndHidesCodeOnRegister() {
        when(users.findByUsername("mam")).thenReturn(Optional.empty());
        when(otp.issue("mam")).thenReturn("111222");
        when(mail.isConfigured()).thenReturn(true);
        when(mail.sendOtp("mam@example.test", "111222")).thenReturn(false);
        AuthRequest req = new AuthRequest();
        req.setUsername("mam"); req.setEmail("mam@example.test"); req.setPassword("StrongPass1!");
        var response = controller.register(req);
        assertEquals(503, response.getStatusCode().value());
        assertFalse(String.valueOf(response.getBody()).contains("devOtp"));
    }
}
