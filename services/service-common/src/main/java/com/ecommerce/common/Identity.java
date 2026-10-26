package com.ecommerce.common;

import java.util.List;

public record Identity(String username, Long userId, Long customerId, String role, List<String> authorities) {}
