package com.ecommerce.order.model;

import java.util.Map;
import java.util.Set;

/**
 * Settlement state of one member's share of a group bill.
 *
 * Online lane (future gateway): PENDING -> PAYMENT_INITIATED -> PAID (or FAILED),
 * PAID -> REFUNDED.
 *
 * Offline lane (Host Pays, implemented today): a friend's share starts OWED;
 * they report paying the host (REPORTED_PAID) and the host confirms receipt
 * (CONFIRMED_RECEIVED). PAID is reachable from OWED only when a real gateway
 * settles the share; nothing in the app lets a frontend button claim PAID.
 */
public enum GroupShareStatus {
    PENDING,
    PAYMENT_INITIATED,
    PAID,
    FAILED,
    REFUNDED,
    OWED,
    REPORTED_PAID,
    CONFIRMED_RECEIVED;

    private static final Map<GroupShareStatus, Set<GroupShareStatus>> ALLOWED = Map.of(
            PENDING, Set.of(PAYMENT_INITIATED, PAID, FAILED, OWED),
            PAYMENT_INITIATED, Set.of(PAID, FAILED, PENDING),
            PAID, Set.of(REFUNDED),
            FAILED, Set.of(PENDING, PAYMENT_INITIATED),
            REFUNDED, Set.of(),
            OWED, Set.of(REPORTED_PAID),
            REPORTED_PAID, Set.of(CONFIRMED_RECEIVED),
            CONFIRMED_RECEIVED, Set.of()
    );

    public boolean canTransitionTo(GroupShareStatus next) {
        return next != null && ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    /** True once the member's share is fully settled (nothing left to collect). */
    public boolean isSettled() {
        return this == PAID || this == CONFIRMED_RECEIVED;
    }

    /** True when no further settlement transitions are possible. */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
