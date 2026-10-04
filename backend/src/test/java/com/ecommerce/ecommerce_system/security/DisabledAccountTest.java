package com.ecommerce.ecommerce_system.security;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class DisabledAccountTest {
    @Test void existingTokenCannotAuthenticateDisabledAccount() throws Exception {
        JwtUtil jwt = new JwtUtil("test-only-key-for-disabled-accounts-32-bytes");
        CustomUserDetailsService users = mock(CustomUserDetailsService.class);
        when(users.loadUserByUsername("ruby")).thenReturn(User.withUsername("ruby").password("hash").roles("CUSTOMER").disabled(true).build());
        JwtAuthFilter filter = new JwtAuthFilter();
        ReflectionTestUtils.setField(filter,"jwtUtil",jwt); ReflectionTestUtils.setField(filter,"userDetailsService",users);
        MockHttpServletRequest request = new MockHttpServletRequest(); request.addHeader("Authorization","Bearer " + jwt.generateToken("ruby","CUSTOMER","hash"));
        SecurityContextHolder.clearContext();
        try {
            filter.doFilter(request,new MockHttpServletResponse(),new MockFilterChain());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
