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

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Simple email shape check (e.g. name@domain.com).
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    // Strong password: 8+ chars with upper, lower, digit and special character.
    private static final java.util.regex.Pattern PASSWORD_PATTERN =
            java.util.regex.Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");

    @Autowired private com.ecommerce.ecommerce_system.service.OtpService otpService;

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
        body.put("token", jwtUtil.generateToken(user.getUsername(), user.getRole().name(), user.getPassword()));
        body.put("username", user.getUsername());
        body.put("role", user.getRole());
        body.put("customerId", customer != null ? customer.getId() : null);
        body.put("customerName", customer != null ? customer.getName() : null);
        return body;
    }

    private String sendOtp(String username) {
        String code = otpService.issue(username);
        if (code == null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait 60 seconds before requesting another code");
        return code;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest req) {
        if (req.getUsername() == null || !req.getUsername().matches("[A-Za-z0-9_]{3,40}")) {
            return ResponseEntity.badRequest().body("Username must contain 3–40 letters, numbers or underscores");
        }
        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already taken");
        }
        if (req.getEmail() == null || req.getEmail().length() > 254 || !EMAIL_PATTERN.matcher(req.getEmail()).matches()) {
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

        // Deliver the code by email; provider failure must never expose the code.
        String otp = sendOtp(req.getUsername());
        boolean emailed = mailService.sendOtp(req.getEmail(), otp);

        if (!emailed) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Verification email could not be sent. Please use Resend code shortly.");
        return ResponseEntity.ok(Map.of("message", "Verification code emailed", "otpRequired", true,
                "emailSentTo", req.getEmail(), "emailDelivered", true));
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String otp = req.get("otp");

        User user = userRepository.findByUsername(username == null ? "" : username).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body("No registration found for that username");
        }

        if (user.isEnabled()) return ResponseEntity.badRequest().body("Account is already verified");
        if (!otpService.verify(username, otp)) return ResponseEntity.badRequest().body("Invalid or expired code. Request a new one after five attempts.");

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

        // Optional referral link. An unknown or malformed code is ignored rather
        // than rejected: someone signing up should not be blocked because a friend
        // mistyped their code, and the bonus simply does not apply. The referrer
        // is credited later, on the referred customer's first order.
        String referralInput = req.get("referralCode");
        if (referralInput != null && !referralInput.trim().isEmpty()) {
            String wanted = referralInput.trim().toUpperCase();
            customerRepository.findByReferralCode(wanted)
                    .filter(referrer -> referrer.getId() != null)
                    .ifPresent(customer::setReferredBy);
        }

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
        if (user.isEnabled()) return ResponseEntity.badRequest().body("Account is already verified");
        String otp = sendOtp(username);
        boolean emailed = user.getEmail() != null && mailService.sendOtp(user.getEmail(), otp);
        if (!emailed) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Verification email could not be sent. Try again shortly.");
        return ResponseEntity.ok(Map.of("message", "New code emailed", "emailDelivered", true));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        // Accept either the username or the email as the identifier — people
        // forget usernames but always remember the email they signed up with.
        String identifier = req.getUsername() == null ? "" : req.getUsername().trim();
        User user = userRepository.findByUsername(identifier)
                .orElseGet(() -> userRepository.findByEmail(identifier).orElse(null));

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Username or email not registered — please create an account first");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), req.getPassword())
            );
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Account not verified yet — enter the OTP you received during registration");
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Incorrect password — please try again");
        } catch (InternalAuthenticationServiceException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Something went wrong while signing in — try again");
        }

        return ResponseEntity.ok(buildAuthResponse(user));
    }

    // Signed-in user changes their password (needs the current one to confirm).
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> req, org.springframework.security.core.Authentication auth) {
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

        User user = userRepository.findByUsername(identifier)
                .orElseGet(() -> userRepository.findByEmail(identifier).orElse(null));
        if (user == null) {
            return ResponseEntity.badRequest().body("No account found for that username or email");
        }

        if (auth == null || !auth.getName().equals(user.getUsername())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        // Confirm the current password before allowing the change.
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), current)
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

        String code = sendOtp("RESET:" + user.getUsername());
        boolean emailed = user.getEmail() != null && mailService.sendOtp(user.getEmail(), code);
        if (!emailed) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Reset email could not be sent. Try again shortly.");
        return ResponseEntity.ok(Map.of("message", "Reset code emailed", "emailDelivered", true));
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

        if (newPassword == null || !PASSWORD_PATTERN.matcher(newPassword).matches()) {
            return ResponseEntity.badRequest().body(
                    "Password too weak — use at least 8 characters with an uppercase letter, a lowercase letter, a number and a special character");
        }

        if (!otpService.verify("RESET:" + user.getUsername(), otp)) return ResponseEntity.badRequest().body("Invalid or expired reset code. Request a new one after five attempts.");
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Password updated — you can sign in now"));
    }

    @PostMapping("/email/request")
    public ResponseEntity<?> requestEmail(@RequestBody Map<String,String> body, org.springframework.security.core.Authentication auth) {
        String email = body.get("email");
        if (email == null || email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) return ResponseEntity.badRequest().body("Enter a valid email");
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        if (!passwordEncoder.matches(body.getOrDefault("currentPassword", ""), user.getPassword())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Current password is incorrect");
        if (userRepository.findByEmail(email).isPresent() || customerRepository.findByEmail(email).isPresent()) return ResponseEntity.badRequest().body("Email already in use");
        String code = sendOtp("EMAIL:" + auth.getName());
        // Bind the challenge to the exact requested address; changing addresses requires another issuance.
        emailChangeRepository.save(new com.ecommerce.ecommerce_system.model.EmailChange(auth.getName(), email));
        if (!mailService.sendOtp(email, code)) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Verification email could not be sent");
        return ResponseEntity.ok(Map.of("message", "Code sent to your new email"));
    }

    @Autowired private com.ecommerce.ecommerce_system.repository.EmailChangeRepository emailChangeRepository;

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/email/confirm")
    public ResponseEntity<?> confirmEmail(@RequestBody Map<String,String> body, org.springframework.security.core.Authentication auth) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        com.ecommerce.ecommerce_system.model.EmailChange change = emailChangeRepository.findById(auth.getName()).orElse(null);
        if (change == null || !change.getEmail().equals(body.get("email"))) return ResponseEntity.badRequest().body("Request a code for this email first");
        if (!otpService.verify("EMAIL:" + auth.getName(), body.get("otp"))) return ResponseEntity.badRequest().body("Invalid or expired code");
        if (userRepository.findByEmail(change.getEmail()).isPresent() || customerRepository.findByEmail(change.getEmail()).isPresent()) return ResponseEntity.badRequest().body("Email already in use");
        user.setEmail(change.getEmail()); userRepository.save(user);
        if (user.getCustomerId() != null) {
            Customer customer = customerRepository.findById(user.getCustomerId()).orElseThrow();
            customer.setEmail(change.getEmail()); customerRepository.save(customer);
        }
        emailChangeRepository.delete(change);
        return ResponseEntity.ok(Map.of("message", "Email verified and updated"));
    }

    // Build a unique referral code, e.g. "RUPA-A1B2C3" (mirrors CustomerController).
    private String generateReferralCode(String name) {
        String cleaned = name == null ? "" : name.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        String base = cleaned.substring(0, Math.min(4, cleaned.length()));
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