package com.ecommerce.common;

import org.springframework.security.core.Authentication;

public class CustomerAccess {
    public Long currentCustomerId(Authentication auth) {
        return auth!=null && auth.getDetails() instanceof Identity identity ? identity.customerId() : null;
    }
    public boolean isAdmin(Authentication auth) {
        return auth!=null && auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));
    }
    public boolean canAccess(Long id,Authentication auth) {
        return isAdmin(auth) || (id!=null && id.equals(currentCustomerId(auth)));
    }
}
