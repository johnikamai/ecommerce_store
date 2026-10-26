package com.ecommerce.auth.security;

import com.ecommerce.auth.model.Department;
import com.ecommerce.auth.model.Role;
import com.ecommerce.auth.model.StaffProfile;
import com.ecommerce.auth.model.StaffStatus;
import com.ecommerce.auth.model.User;
import com.ecommerce.auth.repo.StaffProfileRepository;
import com.ecommerce.auth.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Verifies that staff authorities and the enabled flag follow the staff lifecycle. */
class StaffPermissionTest {

    private User staffUser(boolean enabled) {
        User user = new User();
        user.setUsername("mary");
        user.setPassword("hash");
        user.setRole(Role.STAFF);
        user.setEnabled(enabled);
        return user;
    }

    private StaffProfile profile(StaffStatus status) {
        StaffProfile profile = new StaffProfile();
        profile.setUser(staffUser(true));
        profile.setFullName("Mary Staff");
        profile.setDepartment(Department.INVENTORY);
        profile.setStatus(status);
        profile.setPermissions(new java.util.HashSet<>(Department.INVENTORY.defaultPermissions()));
        return profile;
    }

    private CustomUserDetailsService service(User staff, Optional<StaffProfile> profile) {
        UserRepository users = mock(UserRepository.class);
        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        when(users.findByUsername("mary")).thenReturn(Optional.of(staff));
        when(profiles.findByUserUsername("mary")).thenReturn(profile);
        return new CustomUserDetailsService(users, profiles);
    }

    @Test
    void approvedStaffGetsDepartmentPermissions() {
        UserDetails details = service(staffUser(true), Optional.of(profile(StaffStatus.APPROVED)))
                .loadUserByUsername("mary");

        assertTrue(details.isEnabled());
        List<String> authorities = details.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        assertTrue(authorities.contains("ROLE_STAFF"));
        assertTrue(authorities.contains("PERM_INVENTORY_UPDATE"));
        assertTrue(authorities.contains("PERM_TASK_VIEW"));
        assertFalse(authorities.contains("PERM_PRICE_CHANGE_REQUEST"));
    }

    @Test
    void pendingStaffCannotAuthenticate() {
        UserDetails details = service(staffUser(false), Optional.of(profile(StaffStatus.PENDING)))
                .loadUserByUsername("mary");
        assertFalse(details.isEnabled());
    }

    @Test
    void suspendedStaffIsDisabledEvenIfUserRowIsEnabled() {
        UserDetails details = service(staffUser(true), Optional.of(profile(StaffStatus.SUSPENDED)))
                .loadUserByUsername("mary");
        assertFalse(details.isEnabled(), "a suspended staff account must not authenticate");
    }

    @Test
    void legacyStaffWithoutProfileKeepsAccessWithoutPermissions() {
        UserDetails details = service(staffUser(true), Optional.empty()).loadUserByUsername("mary");

        assertTrue(details.isEnabled());
        List<String> authorities = details.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        assertEquals(List.of("ROLE_STAFF"), authorities);
    }
}
