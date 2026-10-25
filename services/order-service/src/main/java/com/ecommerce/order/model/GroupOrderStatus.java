package com.ecommerce.order.model;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a group order.
 *
 * OPEN          cart is editable, members may join/add items
 * LOCKED        host froze the cart; no item edits
 * AWAITING_PAYMENT  reserved for individual-online mode (disabled until a
 *                   gateway supports per-member charges)
 * CONFIRMED     combined parent order placed with ShopEase
 * PROCESSING/SHIPPED/DELIVERED/CANCELLED  mirror the linked order once placed
 */
public enum GroupOrderStatus {
    OPEN,
    LOCKED,
    AWAITING_PAYMENT,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    private static final Map<GroupOrderStatus, Set<GroupOrderStatus>> ALLOWED = Map.of(
            OPEN, Set.of(LOCKED, CANCELLED),
            LOCKED, Set.of(OPEN, AWAITING_PAYMENT, CONFIRMED, CANCELLED),
            AWAITING_PAYMENT, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(PROCESSING, SHIPPED, DELIVERED, CANCELLED),
            PROCESSING, Set.of(SHIPPED, DELIVERED, CANCELLED),
            SHIPPED, Set.of(DELIVERED, CANCELLED),
            DELIVERED, Set.of(),
            CANCELLED, Set.of()
    );

    public boolean canTransitionTo(GroupOrderStatus next) {
        return next != null && ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    public Set<GroupOrderStatus> allowedNext() {
        return ALLOWED.getOrDefault(this, Set.of());
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    /** True while the group cart can still be edited (before the parent order exists). */
    public boolean isPrePlacement() {
        return this == OPEN || this == LOCKED || this == AWAITING_PAYMENT;
    }
}
