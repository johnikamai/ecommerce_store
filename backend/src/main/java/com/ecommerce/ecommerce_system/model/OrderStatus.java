package com.ecommerce.ecommerce_system.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    PLACED,
    PACKED,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    /**
     * Legal moves for an order. An order only ever moves forward through the
     * fulfilment flow; DELIVERED and CANCELLED are final so a closed order can
     * never be reopened.
     *
     * PACKED and OUT_FOR_DELIVERY were added for order tracking. They are
     * optional waypoints rather than mandatory steps: PLACED may still go
     * straight to SHIPPED, which is what happens for digital goods or when a
     * warehouse ships without a packing step. The customer-facing tracker
     * renders whichever of these the order actually passed through.
     *
     * Lives on the enum rather than in OrderService so the admin console can
     * render exactly the options the backend will accept, from one definition.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            PLACED, Set.of(PACKED, SHIPPED, DELIVERED, CANCELLED),
            PACKED, Set.of(SHIPPED, CANCELLED),
            SHIPPED, Set.of(OUT_FOR_DELIVERY, DELIVERED, CANCELLED),
            OUT_FOR_DELIVERY, Set.of(DELIVERED, CANCELLED),
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
                    case PACKED -> 0;
                    case SHIPPED -> 1;
                    case OUT_FOR_DELIVERY -> 2;
                    case DELIVERED -> 3;
                    case CANCELLED -> 4;
                    case PLACED -> 5;
                }))
                .toList();
    }

    /** True once the parcel is no longer with the warehouse. */
    public boolean isInTransit() {
        return this == SHIPPED || this == OUT_FOR_DELIVERY;
    }

    /**
     * The happy-path milestones a tracker shows, in order. PACKED and
     * OUT_FOR_DELIVERY are included only when the order actually reached them,
     * so a shipment that skipped packing does not render an empty step.
     */
    public static List<OrderStatus> fulfilmentPath() {
        return List.of(PLACED, PACKED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED);
    }
}