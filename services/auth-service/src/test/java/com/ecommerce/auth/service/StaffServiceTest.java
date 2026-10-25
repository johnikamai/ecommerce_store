package com.ecommerce.auth.service;

import com.ecommerce.auth.model.*;
import com.ecommerce.auth.repo.StaffProfileRepository;
import com.ecommerce.auth.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffServiceTest {

    private UserRepository userRepository;
    private StaffProfileRepository staffProfileRepository;
    private PasswordEncoder passwordEncoder;
    private StaffNotificationService notificationService;
    private StaffActivityService activityService;
    private StaffService staffService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        staffProfileRepository = mock(StaffProfileRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        notificationService = mock(StaffNotificationService.class);
        activityService = mock(StaffActivityService.class);
        staffService = new StaffService(userRepository, staffProfileRepository, passwordEncoder,
                notificationService, activityService);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(staffProfileRepository.save(any(StaffProfile.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
    }

    private StaffProfile profile(StaffStatus status) {
        User user = new User();
        user.setId(7L);
        user.setUsername("mary");
        user.setEmail("mary@example.com");
        user.setPassword("hash");
        user.setRole(com.ecommerce.auth.model.Role.STAFF);
        user.setEnabled(status == StaffStatus.APPROVED);
        StaffProfile profile = new StaffProfile();
        profile.setId(1L);
        profile.setUser(user);
        profile.setFullName("Applicant One");
        profile.setDepartment(Department.INVENTORY);
        profile.setStatus(status);
        profile.setPermissions(new HashSet<>());
        return profile;
    }

    @Test
    void applyRejectsDuplicateActiveApplication() {
        User existing = new User();
        userRepository = mock(UserRepository.class);
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("dup@example.com")).thenReturn(Optional.of(existing));
        existing.setRole(com.ecommerce.auth.model.Role.STAFF);
        existing.setId(5L);
        StaffProfile approved = new StaffProfile();
        approved.setStatus(StaffStatus.APPROVED);
        when(staffProfileRepository.findByUserId(5L)).thenReturn(Optional.of(approved));
        staffService = new StaffService(userRepository, staffProfileRepository, passwordEncoder,
                notificationService, activityService);

        com.ecommerce.auth.model.StaffApplicationRequest req =
                new com.ecommerce.auth.model.StaffApplicationRequest();
        req.setFullName("Dup User");
        req.setEmail("dup@example.com");
        req.setDepartment(com.ecommerce.auth.model.Department.INVENTORY);
        req.setPassword("Passw0rd!");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> staffService.apply(req));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void applyRejectsWeakPassword() {
        com.ecommerce.auth.model.StaffApplicationRequest req =
                new com.ecommerce.auth.model.StaffApplicationRequest();
        req.setFullName("Weak User");
        req.setEmail("weak@example.com");
        req.setDepartment(com.ecommerce.auth.model.Department.INVENTORY);
        req.setPassword("weak");

        assertThrows(IllegalArgumentException.class, () -> staffService.apply(req));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void approveEnablesUserAndGrantsDepartmentPermissions() {
        StaffProfile profile = profile(StaffStatus.PENDING);
        when(staffProfileRepository.findById(1L)).thenReturn(Optional.of(profile));

        com.ecommerce.auth.model.StaffDecisionRequest decision =
                new com.ecommerce.auth.model.StaffDecisionRequest();
        decision.setAction("APPROVE");

        StaffProfile result = staffService.decide(1L, decision, "admin");

        assertEquals(StaffStatus.APPROVED, result.getStatus());
        assertTrue(result.getUser().isEnabled());
        assertTrue(result.getPermissions().contains(com.ecommerce.auth.model.Permission.INVENTORY_UPDATE));
        verify(notificationService).notify(any(), any(), any());
        verify(activityService).record(eq("admin"), eq("STAFF_APPROVE"), any(), eq(1L), any());
    }

    @Test
    void suspendDisablesUser() {
        StaffProfile profile = profile(StaffStatus.APPROVED);
        when(staffProfileRepository.findById(1L)).thenReturn(Optional.of(profile));

        com.ecommerce.auth.model.StaffDecisionRequest decision =
                new com.ecommerce.auth.model.StaffDecisionRequest();
        decision.setAction("SUSPEND");
        decision.setReason("policy breach");

        StaffProfile result = staffService.decide(1L, decision, "admin");

        assertEquals(StaffStatus.SUSPENDED, result.getStatus());
        assertFalse(result.getUser().isEnabled());
        assertEquals("policy breach", result.getDecisionReason());
    }

    @Test
    void unknownActionIsRejected() {
        StaffProfile profile = profile(StaffStatus.PENDING);
        when(staffProfileRepository.findById(1L)).thenReturn(Optional.of(profile));

        com.ecommerce.auth.model.StaffDecisionRequest decision =
                new com.ecommerce.auth.model.StaffDecisionRequest();
        decision.setAction("EXPLODE");

        assertThrows(IllegalArgumentException.class, () -> staffService.decide(1L, decision, "admin"));
    }
}
