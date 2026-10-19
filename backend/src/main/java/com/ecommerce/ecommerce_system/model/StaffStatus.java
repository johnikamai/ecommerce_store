package com.ecommerce.ecommerce_system.model;

/** Lifecycle state of a staff account. Only APPROVED accounts may authenticate. */
public enum StaffStatus {
    PENDING,
    MORE_INFO,
    APPROVED,
    REJECTED,
    SUSPENDED,
    DEACTIVATED
}
