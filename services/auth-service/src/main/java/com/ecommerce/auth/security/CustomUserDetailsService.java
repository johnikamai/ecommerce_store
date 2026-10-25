package com.ecommerce.auth.security;

import com.ecommerce.auth.model.Permission;
import com.ecommerce.auth.model.Role;
import com.ecommerce.auth.model.StaffProfile;
import com.ecommerce.auth.model.StaffStatus;
import com.ecommerce.auth.model.User;
import com.ecommerce.auth.repo.StaffProfileRepository;
import com.ecommerce.auth.repo.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final StaffProfileRepository staffProfileRepository;

    public CustomUserDetailsService(UserRepository userRepository,
                                    StaffProfileRepository staffProfileRepository) {
        this.userRepository = userRepository;
        this.staffProfileRepository = staffProfileRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user: " + username));

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        // disabled until the registration OTP is verified (customers) or until a
        // staff application is approved (see below).
        boolean enabled = user.isEnabled();

        // Approved staff receive fine-grained PERM_* authorities from their profile.
        // A staff account whose application has not been approved (or that has been
        // suspended/deactivated) is treated as disabled so existing tokens stop working.
        if (user.getRole() == Role.STAFF) {
            Optional<StaffProfile> profile = staffProfileRepository.findByUserUsername(username);
            if (profile.isPresent()) {
                StaffProfile staff = profile.get();
                enabled = enabled && staff.getStatus() == StaffStatus.APPROVED;
                for (Permission permission : staff.getPermissions()) {
                    authorities.add(new SimpleGrantedAuthority(permission.authority()));
                }
            }
            // No profile => legacy staff account (role set directly by an admin):
            // keep its pre-existing access rather than locking the account out.
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                enabled,
                true, // accountNonExpired
                true, // credentialsNonExpired
                true, // accountNonLocked
                authorities
        );
    }
}
