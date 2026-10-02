package com.ecommerce.ecommerce_system.model;

import java.util.Map;
import java.util.Set;

public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED;

    /**
     * Legal moves for a payment record.
     *
     * Money only ever moves forward: a failed or pending payment can be
     * confirmed later, a confirmed payment can be refunded, and a refunded
     * payment is final - un-refunding it would silently resurrect revenue.
     */
    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
            PENDING, Set.of(PAID, FAILED),
            PAID, Set.of(REFUNDED),
            FAILED, Set.of(PENDING, PAID),
            REFUNDED, Set.of()
    );

    public boolean canTransitionTo(PaymentStatus next) {
        return next != null && ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }

    public Set<PaymentStatus> allowedNext() {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of());
    }
}