package com.ecommerce.auth.service;

import com.ecommerce.auth.model.*;
import com.ecommerce.auth.repo.StaffProfileRepository;
import com.ecommerce.auth.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Owns the staff account lifecycle: public applications, admin invitations,
 * approval decisions, department/permission management and status changes.
 *
 * Approval is what flips a {@link User} to enabled and grants PERM_* authorities
 * (via CustomUserDetailsService); suspending/deactivating disables the account again.
 */
@Service
public class StaffService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");

    private final UserRepository userRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffNotificationService notificationService;
    private final StaffActivityService activityService;

    public StaffService(UserRepository userRepository,
                        StaffProfileRepository staffProfileRepository,
                        PasswordEncoder passwordEncoder,
                        StaffNotificationService staffNotificationService,
                        StaffActivityService staffActivityService) {
        this.userRepository = userRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = staffNotificationService;
        this.activityService = staffActivityService;
    }

    // ---------------------------------------------------------------- applications

    @Transactional
    public StaffProfile apply(StaffApplicationRequest req) {
        validate(req);

        Optional<User> existingUser = userRepository.findByEmail(req.getEmail());

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (user.getRole() != Role.STAFF) {
                throw new IllegalArgumentException("This email is already registered to a customer account");
            }
            Optional<StaffProfile> existingProfile = staffProfileRepository.findByUserId(user.getId());
            if (existingProfile.isPresent()) {
                StaffStatus status = existingProfile.get().getStatus();
                if (status == StaffStatus.PENDING || status == StaffStatus.APPROVED || status == StaffStatus.SUSPENDED) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "An active staff application or account already exists for this email");
                }
                // REJECTED / MORE_INFO / DEACTIVATED may apply again.
                User user2 = existingProfile.get().getUser();
                user2.setPassword(passwordEncoder.encode(req.getPassword()));
                user2.setEnabled(false);
                userRepository.save(user2);

                StaffProfile profile = existingProfile.get();
                applyApplicationFields(profile, req);
                profile.setStatus(StaffStatus.PENDING);
                profile.setPermissions(new HashSet<>());
                profile.setDecisionReason(null);
                profile.setDecidedBy(null);
                profile.setDecidedAt(null);
                profile.setUpdatedAt(LocalDateTime.now());
                StaffProfile saved = staffProfileRepository.save(profile);
                activityService.record(profile.getUser().getUsername(), "STAFF_REAPPLIED", "STAFF_PROFILE", saved.getId(),
                        "Re-applied for " + req.getDepartment());
                return saved;
            }
            // Staff user without a profile (legacy): attach a profile as PENDING.
            User legacy = existingUser.get();
            legacy.setPassword(passwordEncoder.encode(req.getPassword()));
            legacy.setEnabled(false);
            userRepository.save(legacy);
            return saveNewProfile(legacy, req, false);
        }

        // Brand new applicant. Never trust a client-supplied role.
        User user = new User();
        user.setUsername(resolveUsername(req.getEmail()));
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole(Role.STAFF);
        user.setEnabled(false); // enabled only once approved
        userRepository.save(user);
        return saveNewProfile(user, req, false);
    }

    @Transactional
    public StaffProfile createInvite(StaffInviteRequest req, String adminUsername) {
        if (req.getPassword() == null) req.setPassword(generateTempPassword());
        StaffApplicationRequest application = new StaffApplicationRequest();
        application.setFullName(req.getFullName());
        application.setEmail(req.getEmail());
        application.setPhone(req.getPhone());
        application.setDepartment(req.getDepartment());
        application.setExperience(req.getExperience());
        application.setReason(req.getReason());
        application.setPassword(req.getPassword());

        User user = userRepository.findByEmail(req.getEmail()).orElse(null);
        if (user != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists");
        }
        user = new User();
        user.setUsername(resolveUsername(req.getEmail()));
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole(Role.STAFF);
        user.setEnabled(true);
        userRepository.save(user);

        StaffProfile profile = saveNewProfile(user, application, true);
        profile.setStatus(StaffStatus.APPROVED);
        profile.setInvited(true);
        profile.setDecidedBy(adminUsername);
        profile.setDecidedAt(LocalDateTime.now());
        profile.setPermissions(new HashSet<>(profile.getDepartment().defaultPermissions()));
        profile.setUpdatedAt(LocalDateTime.now());
        StaffProfile saved = staffProfileRepository.save(profile);

        activityService.record(adminUsername, "STAFF_INVITED", "STAFF_PROFILE", saved.getId(),
                "Invited " + user.getUsername() + " as " + profile.getDepartment());
        notificationService.notify(user, "Welcome to ShopEase! Your " + profile.getDepartment()
                + " staff account is ready. Please change your password after signing in.", "ACCOUNT");
        return saved;
    }

    private StaffProfile saveNewProfile(User user, StaffApplicationRequest req, boolean invited) {
        StaffProfile profile = new StaffProfile();
        profile.setUser(user);
        profile.setFullName(req.getFullName());
        profile.setPhone(req.getPhone());
        profile.setDepartment(req.getDepartment());
        profile.setExperience(req.getExperience());
        profile.setReason(req.getReason());
        profile.setStatus(invited ? StaffStatus.APPROVED : StaffStatus.PENDING);
        profile.setPermissions(new HashSet<>());
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        StaffProfile saved = staffProfileRepository.save(profile);
        activityService.record(user.getUsername(), invited ? "STAFF_INVITED" : "STAFF_APPLIED",
                "STAFF_PROFILE", saved.getId(), "Department: " + req.getDepartment());
        return saved;
    }

    private void applyApplicationFields(StaffProfile profile, StaffApplicationRequest req) {
        profile.setFullName(req.getFullName());
        profile.setPhone(req.getPhone());
        profile.setDepartment(req.getDepartment());
        profile.setExperience(req.getExperience());
        profile.setReason(req.getReason());
    }

    // ---------------------------------------------------------------- reads

    public List<StaffProfile> list(StaffStatus status) {
        return status == null ? staffProfileRepository.findAllByOrderByCreatedAtDesc()
                : staffProfileRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public List<StaffProfile> search(String query) {
        if (query == null || query.isBlank()) return list(null);
        return staffProfileRepository
                .findByFullNameContainingIgnoreCaseOrUserUsernameContainingIgnoreCaseOrUserEmailContainingIgnoreCase(
                        query, query, query);
    }

    /** Approved staff, used to populate task-assignment pickers. */
    public List<StaffProfile> approved() {
        return staffProfileRepository.findByStatusOrderByCreatedAtDesc(StaffStatus.APPROVED);
    }

    public StaffProfile getById(Long id) {
        return staffProfileRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Staff profile not found: " + id));
    }

    public Optional<StaffProfile> findByUsername(String username) {
        return staffProfileRepository.findByUserUsername(username);
    }

    public Optional<StaffProfile> findByEmail(String email) {
        return userRepository.findByEmail(email).flatMap(u -> staffProfileRepository.findByUserId(u.getId()));
    }

    public java.util.Map<String, Object> summary() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("total", staffProfileRepository.count());
        map.put("pending", staffProfileRepository.countByStatus(StaffStatus.PENDING));
        map.put("approved", staffProfileRepository.countByStatus(StaffStatus.APPROVED));
        map.put("suspended", staffProfileRepository.countByStatus(StaffStatus.SUSPENDED));
        map.put("rejected", staffProfileRepository.countByStatus(StaffStatus.REJECTED));
        map.put("deactivated", staffProfileRepository.countByStatus(StaffStatus.DEACTIVATED));
        map.put("moreInfo", staffProfileRepository.countByStatus(StaffStatus.MORE_INFO));
        return map;
    }

    // ---------------------------------------------------------------- decisions

    @Transactional
    public StaffProfile decide(Long id, StaffDecisionRequest req, String adminUsername) {
        StaffProfile profile = getById(id);
        String action = req.getAction() == null ? "" : req.getAction().trim().toUpperCase();
        User user = profile.getUser();

        switch (action) {
            case "APPROVE" -> {
                if (req.getDepartment() != null) profile.setDepartment(req.getDepartment());
                profile.setStatus(StaffStatus.APPROVED);
                Set<Permission> perms = req.getPermissions() != null && !req.getPermissions().isEmpty()
                        ? req.getPermissions()
                        : profile.getDepartment().defaultPermissions();
                profile.setPermissions(new HashSet<>(perms));
                user.setEnabled(true);
                notificationService.notify(user, "Your ShopEase staff application for "
                        + profile.getDepartment() + " was approved. Welcome aboard!", "ACCOUNT");
            }
            case "REJECT" -> {
                profile.setStatus(StaffStatus.REJECTED);
                profile.setPermissions(new HashSet<>());
                user.setEnabled(false);
                notificationService.notify(user, "Your ShopEase staff application was not approved."
                        + (req.getReason() != null ? " Reason: " + req.getReason() : ""), "ACCOUNT");
            }
            case "REQUEST_INFO" -> {
                profile.setStatus(StaffStatus.MORE_INFO);
                user.setEnabled(false);
                notificationService.notify(user, "We need more information about your ShopEase staff application: "
                        + (req.getReason() == null ? "please contact the admin team." : req.getReason()), "ACCOUNT");
            }
            case "SUSPEND" -> {
                profile.setStatus(StaffStatus.SUSPENDED);
                user.setEnabled(false);
                notificationService.notify(user, "Your ShopEase staff account has been suspended"
                        + (req.getReason() != null ? ": " + req.getReason() : "."), "ACCOUNT");
            }
            case "REACTIVATE" -> {
                profile.setStatus(StaffStatus.APPROVED);
                if (profile.getPermissions() == null || profile.getPermissions().isEmpty()) {
                    profile.setPermissions(new HashSet<>(profile.getDepartment().defaultPermissions()));
                }
                user.setEnabled(true);
                notificationService.notify(user, "Your ShopEase staff account has been reactivated.", "ACCOUNT");
            }
            case "DEACTIVATE" -> {
                profile.setStatus(StaffStatus.DEACTIVATED);
                profile.setPermissions(new HashSet<>());
                user.setEnabled(false);
                notificationService.notify(user, "Your ShopEase staff account has been deactivated.", "ACCOUNT");
            }
            default -> throw new IllegalArgumentException("Unknown action: " + req.getAction());
        }

        userRepository.save(user);
        profile.setDecidedBy(adminUsername);
        profile.setDecidedAt(LocalDateTime.now());
        if (req.getReason() != null) profile.setDecisionReason(req.getReason());
        profile.setUpdatedAt(LocalDateTime.now());
        StaffProfile saved = staffProfileRepository.save(profile);

        activityService.record(adminUsername, "STAFF_" + action, "STAFF_PROFILE", saved.getId(),
                "Status -> " + saved.getStatus() + (req.getReason() != null ? " (" + req.getReason() + ")" : ""));
        return saved;
    }

    @Transactional
    public StaffProfile updateRole(Long id, Department department, Set<Permission> permissions, String adminUsername) {
        StaffProfile profile = getById(id);
        if (department != null) profile.setDepartment(department);
        if (permissions != null) profile.setPermissions(new HashSet<>(permissions));
        profile.setUpdatedAt(LocalDateTime.now());
        StaffProfile saved = staffProfileRepository.save(profile);
        activityService.record(adminUsername, "STAFF_UPDATED", "STAFF_PROFILE", saved.getId(),
                "Department/permissions updated");
        notificationService.notify(profile.getUser(), "Your role or permissions were updated by an administrator.", "ACCOUNT");
        return saved;
    }

    // ---------------------------------------------------------------- helpers

    private String resolveUsername(String email) {
        String base = email == null ? "staff" : email.toLowerCase();
        Optional<User> clash = userRepository.findByUsername(base);
        if (clash.isEmpty()) return base;
        int i = 1;
        String candidate = base + "_" + i;
        while (userRepository.findByUsername(candidate).isPresent()) {
            i++;
            candidate = base + "_" + i;
        }
        return candidate;
    }

    private void validate(StaffApplicationRequest req) {
        validateCommon(req.getFullName(), req.getEmail(), req.getPassword(), req.getDepartment());
    }

    private void validateCommon(String fullName, String email, String password, Department department) {
        if (fullName == null || fullName.trim().length() < 2 || fullName.length() > 120) {
            throw new IllegalArgumentException("Please enter a valid full name");
        }
        if (email == null || email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Please enter a valid email address");
        }
        if (department == null) {
            throw new IllegalArgumentException("Please choose a department");
        }
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException(
                    "Password too weak — use at least 8 characters with an uppercase letter, a lowercase letter, a number and a special character");
        }
    }

    private String generateTempPassword() {
        return "Staff@" + (100000 + new java.security.SecureRandom().nextInt(900000));
    }
}
