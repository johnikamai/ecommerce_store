package com.ecommerce.ecommerce_system.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    PLACED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /**
     * Legal moves for an order. An order only ever moves forward through the
     * fulfilment flow; DELIVERED and CANCELLED are final so a closed order can
     * never be reopened.
     *
     * Lives on the enum rather than in OrderService so the admin console can
     * render exactly the options the backend will accept, from one definition.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            PLACED, Set.of(SHIPPED, DELIVERED, CANCELLED),
            SHIPPED, Set.of(DELIVERED, CANCELLED),
            DELIVERED, Set.of(),
            CANCELLED, Set.of()
    );

    public boolean canTransitionTo(OrderStatus next) {
        return next != null && ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }

    public Set<OrderStatus> allowedNext() {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of());
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    /** Ordered for dropdown rendering: in-stock, in-flow, then the destructive option. */
    public List<OrderStatus> allowedNextOrdered() {
        return allowedNext().stream()
                .sorted(java.util.Comparator.comparingInt(s -> switch (s) {
                    case SHIPPED -> 0;
                    case DELIVERED -> 1;
                    case CANCELLED -> 2;
                    case PLACED -> 3;
                }))
                .toList();
    }
}