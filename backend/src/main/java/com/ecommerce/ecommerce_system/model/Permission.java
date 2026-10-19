package com.ecommerce.ecommerce_system.model;

/**
 * Fine-grained staff capabilities. These are granted to an APPROVED staff account
 * (either from the department defaults or explicitly by an admin) and surfaced to
 * Spring Security as authorities named {@code PERM_<name>}.
 */
public enum Permission {
    PRODUCT_VIEW,
    PRODUCT_CREATE,
    PRODUCT_EDIT,
    PRICE_CHANGE_REQUEST,
    INVENTORY_VIEW,
    INVENTORY_UPDATE,
    ORDER_VIEW,
    ORDER_FULFILL,
    SUPPORT_VIEW,
    SUPPORT_REPLY,
    DELIVERY_UPDATE,
    TASK_VIEW,
    TASK_MANAGE,
    STAFF_MANAGE,
    REFUND_APPROVE;

    public static final String AUTHORITY_PREFIX = "PERM_";

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }
}
