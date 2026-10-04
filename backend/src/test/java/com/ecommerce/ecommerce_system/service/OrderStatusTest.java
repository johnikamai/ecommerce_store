package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.OrderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The order status machine, which both the admin console and the customer
 * tracker depend on.
 *
 * The two new fulfilment states (PACKED, OUT_FOR_DELIVERY) exist for tracking,
 * so the rules worth pinning down are that they are optional shortcuts rather
 * than mandatory gates, and that a closed order can never be reopened.
 */
class OrderStatusTest {

    @Test
    void placedCanSkipStraightToShipped() {
        // Warehouses that do not have a packing step, and digital goods, must
        // not be forced through PACKED just because the state exists.
        assertTrue(OrderStatus.PLACED.canTransitionTo(OrderStatus.SHIPPED));
    }

    @Test
    void placedCanBePackedOrCancelled() {
        assertTrue(OrderStatus.PLACED.canTransitionTo(OrderStatus.PACKED));
        assertTrue(OrderStatus.PLACED.canTransitionTo(OrderStatus.CANCELLED));
    }

    @Test
    void packedMustShipBeforeDelivering() {
        assertTrue(OrderStatus.PACKED.canTransitionTo(OrderStatus.SHIPPED));
        assertTrue(OrderStatus.PACKED.canTransitionTo(OrderStatus.CANCELLED));
        // PACKED -> OUT_FOR_DELIVERY would skip the courier handover entirely.
        assertFalse(OrderStatus.PACKED.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY));
    }

    @Test
    void shippedAdvancesThroughOutForDelivery() {
        assertTrue(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY));
        assertTrue(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED));
        assertTrue(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.DELIVERED));
    }

    @Test
    void orderNeverMovesBackwards() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                if (from.ordinal() >= to.ordinal()) {
                    assertFalse(from.canTransitionTo(to),
                            from + " must not be able to move back to " + to);
                }
            }
        }
    }

    @Test
    void deliveredAndCancelledAreFinal() {
        assertTrue(OrderStatus.DELIVERED.isTerminal());
        assertTrue(OrderStatus.CANCELLED.isTerminal());
        assertFalse(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.SHIPPED));
        assertFalse(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PLACED));
    }

    @Test
    void nullTargetIsNeverAValidTransition() {
        for (OrderStatus from : OrderStatus.values()) {
            assertFalse(from.canTransitionTo(null));
        }
    }

    @Test
    void adminOptionsAreOrderedForDisplay() {
        // The console renders these in order, so the sequence matters: a staff
        // member should never be offered "Delivered" before "Packed".
        // OUT_FOR_DELIVERY is absent from PLACED's options on purpose - the
        // parcel has to reach the courier before it can be out for delivery.
        assertEquals(
                List.of(OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.CANCELLED),
                OrderStatus.PLACED.allowedNextOrdered());

        // From SHIPPED the courier handover step does become available.
        assertEquals(
                List.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, OrderStatus.CANCELLED),
                OrderStatus.SHIPPED.allowedNextOrdered());
    }

    @Test
    void inTransitOnlyWhileTheParcelIsMoving() {
        assertFalse(OrderStatus.PLACED.isInTransit(), "still in the warehouse");
        assertFalse(OrderStatus.PACKED.isInTransit(), "still in the warehouse");
        assertTrue(OrderStatus.SHIPPED.isInTransit());
        assertTrue(OrderStatus.OUT_FOR_DELIVERY.isInTransit());
        assertFalse(OrderStatus.DELIVERED.isInTransit());
        assertFalse(OrderStatus.CANCELLED.isInTransit());
    }

    @Test
    void fulfilmentPathCoversEveryHappyPathMilestone() {
        assertEquals(
                List.of(OrderStatus.PLACED, OrderStatus.PACKED, OrderStatus.SHIPPED,
                        OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED),
                OrderStatus.fulfilmentPath());
    }
}
