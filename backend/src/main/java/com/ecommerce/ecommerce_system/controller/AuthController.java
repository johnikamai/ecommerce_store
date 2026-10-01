package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.AuthRequest;
import com.ecommerce.ecommerce_system.model.Customer;
import com.ecommerce.ecommerce_system.model.Role;
import com.ecommerce.ecommerce_system.model.User;
import com.ecommerce.ecommerce_system.repository.CustomerRepository;
import com.ecommerce.ecommerce_system.repository.UserRepository;
import com.ecommerce.ecommerce_system.security.JwtUtil;
import com.ecommerce.ecommerce_system.service.MailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Simple email shape check (e.g. name@domain.com).
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    // Strong password: 8+ chars with upper, lower, digit and special character.
    private static final java.util.regex.Pattern PASSWORD_PATTERN =
            java.util.regex.Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");

    // Demo OTP store (in-memory, reset on restart). A real app would persist
    // these and deliver them over SMS/email.
    private static final Map<String, OtpEntry> OTP_STORE = new ConcurrentHashMap<>();
    private static final Duration OTP_TTL = Duration.ofMinutes(5);

    private static class OtpEntry {
        final String code;
        final Instant expiresAt;

        OtpEntry(String code, Instant expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MailService mailService;

    // Shared response shape for both register and login so the frontend
    // always knows the signed-in customer's profile id.
    private Map<String, Object> buildAuthResponse(User user) {
        Customer customer = user.getCustomerId() != null
                ? customerRepository.findById(user.getCustomerId()).orElse(null)
                : null;

        Map<String, Object> body = new HashMap<>();
        body.put("token", jwtUtil.generateToken(user.getUsername(), user.getRole().name()));
        body.put("username", user.getUsername());
        body.put("role", user.getRole());
        body.put("customerId", customer != null ? customer.getId() : null);
        body.put("customerName", customer != null ? customer.getName() : null);
        return body;
    }

    // Generates a 6-digit code, stores it keyed by username. There is no real
    // SMS/email gateway, so the code is logged to the backend console and also
    // returned as devOtp so the demo UI can show it.
    private String sendOtp(String username) {
        String code = String.format("%06d", new java.util.Random().nextInt(1_000_000));
        OTP_STORE.put(username, new OtpEntry(code, Instant.now().plus(OTP_TTL)));
        System.out.println("[OTP] For " + username + ": " + code + " (valid " + OTP_TTL.toMinutes() + " minutes)");
        return code;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest req) {
        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already taken");
        }
        if (req.getEmail() == null || !EMAIL_PATTERN.matcher(req.getEmail()).matches()) {
            return ResponseEntity.badRequest().body("Please enter a valid email address");
        }
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Email already registered");
        }
        if (req.getPassword() == null || !PASSWORD_PATTERN.matcher(req.getPassword()).matches()) {
            return ResponseEntity.badRequest().body(
                    "Password too weak — use at least 8 characters with an uppercase letter, a lowercase letter, a number and a special character");
        }

        // Self-registration always creates a normal customer. A client-supplied
        // role is never trusted (otherwise anyone could sign up as ADMIN).
        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole(Role.CUSTOMER);
        user.setEnabled(false); // pending until the OTP is verified
        userRepository.save(user);

        // Deliver the OTP to their email. When SMTP credentials are configured
        // this sends a real email; otherwise it falls back to the on-screen demo.
        String otp = sendOtp(req.getUsername());
        boolean emailed = mailService.sendOtp(req.getEmail(), otp);

        Map<String, Object> body = new HashMap<>();
        body.put("message", emailed
                ? "We emailed an OTP to " + req.getEmail() + " — enter it to activate your account."
                : "Registration started. (Demo mode — no email sent, use the code shown below.)");
        body.put("otpRequired", true);
        body.put("emailSentTo", req.getEmail());
        body.put("emailDelivered", emailed);
        body.put("devOtp", otp); // shown only for demo/troubleshooting
        return ResponseEntity.ok(body);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String otp = req.get("otp");

        User user = userRepository.findByUsername(username == null ? "" : username).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body("No registration found for that username");
        }

        OtpEntry entry = OTP_STORE.get(username);
        if (entry == null || entry.expiresAt.isBefore(Instant.now())) {
            return ResponseEntity.badRequest().body("OTP expired — request a new one");
        }
        if (entry.code == null || !entry.code.equals(otp)) {
            return ResponseEntity.badRequest().body("Incorrect OTP — try again");
        }
        OTP_STORE.remove(username);

        // Identity confirmed: activate the account and create the linked
        // customer profile (wishlist, orders, points, referral code...).
        user.setEnabled(true);

        Customer customer = new Customer();
        customer.setName(username);
        customer.setEmail(user.getEmail() != null ? user.getEmail() : username + "@shop.ecom");
        customer.setLoyaltyPoints(0);
        customer.setTier("BRONZE");
        customer.setReferralCode(generateReferralCode(username));
        customer.setReferralRewarded(false);
        customer = customerRepository.save(customer);

        user.setCustomerId(customer.getId());
        userRepository.save(user);

        return ResponseEntity.ok(buildAuthResponse(user));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody Map<String, String> req) {
        String username = req.get("username") == null ? "" : req.get("username");
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body("No registration found for that username");
        }
        String otp = sendOtp(username);
        boolean emailed = user.getEmail() != null && mailService.sendOtp(user.getEmail(), otp);
        return ResponseEntity.ok(Map.of(
                "message", emailed ? "New OTP emailed to you" : "New OTP generated (demo mode)",
                "devOtp", otp
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
            );
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Account not verified yet — enter the OTP you received during registration");
        } catch (BadCredentialsException e) {
            // Both "wrong password" and "username doesn't exist" surface as bad
            // credentials. Tell the user which one it actually is.
            if (userRepository.findByUsername(req.getUsername()).isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Username not registered — please create an account first");
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Incorrect password — please try again");
        } catch (InternalAuthenticationServiceException e) {
            if (userRepository.findByUsername(req.getUsername()).isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Username not registered — please create an account first");
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Something went wrong while signing in — try again");
        }

        User user = userRepository.findByUsername(req.getUsername()).orElseThrow();
        return ResponseEntity.ok(buildAuthResponse(user));
    }

    // Signed-in user changes their password (needs the current one to confirm).
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> req) {
        String identifier = req.get("username") == null ? "" : req.get("username").trim();
        String current = req.get("currentPassword");
        String newPassword = req.get("newPassword");

        if (identifier.isBlank() || current == null || current.isBlank()) {
            return ResponseEntity.badRequest().body("Username and current password are required");
        }
        if (newPassword == null || !PASSWORD_PATTERN.matcher(newPassword).matches()) {
            return ResponseEntity.badRequest().body(
                    "Password too weak — use at least 8 characters with an uppercase letter, a lowercase letter, a number and a special character");
        }

        User user = userRepository.findByUsername(identifier).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body("No account found for that username");
        }

        // Confirm the current password before allowing the change.
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifier, current)
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Password changed — use it next time you sign in"));
    }

    // "Forgot password" step 1: look the account up by username OR email,
    // then send a reset code to the account's email (same 6-digit OTP channel
    // as registration, so Brevo delivery works automatically).
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> req) {
        String identifier = req.get("username") == null ? "" : req.get("username");
        if (identifier.isBlank()) {
            return ResponseEntity.badRequest().body("Enter your username or email");
        }

        User user = userRepository.findByUsername(identifier)
                .orElseGet(() -> userRepository.findByEmail(identifier).orElse(null));
        if (user == null) {
            return ResponseEntity.badRequest().body("No account found for that username or email");
        }
        if (!user.isEnabled()) {
            return ResponseEntity.badRequest().body("Account not verified yet — complete the OTP from registration first");
        }

        String code = String.format("%06d", new java.util.Random().nextInt(1_000_000));
        OTP_STORE.put("RESET:" + user.getUsername(), new OtpEntry(code, Instant.now().plus(OTP_TTL)));
        System.out.println("[OTP] Reset for " + user.getUsername() + ": " + code);
        boolean emailed = user.getEmail() != null && mailService.sendOtp(user.getEmail(), code);

        return ResponseEntity.ok(Map.of(
                "message", emailed ? "We emailed a reset code to " + user.getEmail() : "Reset code generated (demo mode)",
                "emailSentTo", user.getEmail(),
                "emailDelivered", emailed,
                "devOtp", code
        ));
    }

    // "Forgot password" step 2: confirm the reset code, then set the new password.
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> req) {
        String identifier = req.get("username") == null ? "" : req.get("username");
        String otp = req.get("otp") == null ? "" : req.get("otp");
        String newPassword = req.get("newPassword");

        User user = userRepository.findByUsername(identifier)
                .orElseGet(() -> userRepository.findByEmail(identifier).orElse(null));
        if (user == null) {
            return ResponseEntity.badRequest().body("No account found for that username or email");
        }

        OtpEntry entry = OTP_STORE.get("RESET:" + user.getUsername());
        if (entry == null || entry.expiresAt.isBefore(Instant.now())) {
            return ResponseEntity.badRequest().body("Reset code expired — request a new one");
        }
        if (entry.code == null || !entry.code.equals(otp)) {
            return ResponseEntity.badRequest().body("Incorrect reset code — try again");
        }
        if (newPassword == null || !PASSWORD_PATTERN.matcher(newPassword).matches()) {
            return ResponseEntity.badRequest().body(
                    "Password too weak — use at least 8 characters with an uppercase letter, a lowercase letter, a number and a special character");
        }

        OTP_STORE.remove("RESET:" + user.getUsername());
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Password updated — you can sign in now"));
    }

    // Build a unique referral code, e.g. "RUPA-A1B2C3" (mirrors CustomerController).
    private String generateReferralCode(String name) {
        String base = (name == null || name.isBlank()) ? "USER" : name.trim().toUpperCase().replaceAll("[^A-Z0-9]", "").substring(0, Math.min(4, name.trim().length()));
        if (base.isEmpty()) {
            base = "USER";
        }
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        String code = base + "-" + suffix;
        while (customerRepository.findByReferralCode(code).isPresent()) {
            suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
            code = base + "-" + suffix;
        }
        return code;
    }
}