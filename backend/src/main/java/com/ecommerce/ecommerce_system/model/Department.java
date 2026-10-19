package com.ecommerce.ecommerce_system.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Staff departments. Each department maps to the default set of {@link Permission}s
 * granted when a staff member is approved. Admins may still grant or revoke individual
 * permissions afterwards.
 */
public enum Department {
    PRODUCT_MANAGER,
    INVENTORY,
    ORDER_MANAGER,
    CUSTOMER_SUPPORT,
    DELIVERY,
    MARKETING;

    /** Permissions every staff member receives regardless of department. */
    public static final Set<Permission> BASELINE = EnumSet.of(Permission.TASK_VIEW, Permission.PRODUCT_VIEW);

    public Set<Permission> defaultPermissions() {
        Set<Permission> perms = EnumSet.copyOf(BASELINE);
        switch (this) {
            case PRODUCT_MANAGER -> perms.addAll(EnumSet.of(
                    Permission.PRODUCT_CREATE, Permission.PRODUCT_EDIT, Permission.PRICE_CHANGE_REQUEST));
            case INVENTORY -> perms.addAll(EnumSet.of(
                    Permission.INVENTORY_VIEW, Permission.INVENTORY_UPDATE));
            case ORDER_MANAGER -> perms.addAll(EnumSet.of(
                    Permission.ORDER_VIEW, Permission.ORDER_FULFILL));
            case CUSTOMER_SUPPORT -> perms.addAll(EnumSet.of(
                    Permission.SUPPORT_VIEW, Permission.SUPPORT_REPLY, Permission.ORDER_VIEW));
            case DELIVERY -> perms.addAll(EnumSet.of(
                    Permission.DELIVERY_UPDATE, Permission.ORDER_VIEW));
            case MARKETING -> perms.add( Permission.PRICE_CHANGE_REQUEST);
        }
        return perms;
    }
}
