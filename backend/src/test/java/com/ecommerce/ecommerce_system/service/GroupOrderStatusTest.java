package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.GroupOrderStatus;
import com.ecommerce.ecommerce_system.model.GroupShareStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lifecycle state machines. A group order and each member's share can only
 * move along the declared edges — a stray frontend call can't, for example,
 * jump an OPEN cart straight to DELIVERED or mark an OWED share CONFIRMED
 * without the member first reporting it paid.
 */
class GroupOrderStatusTest {

    @Test
    void happyPathLifecycle() {
        assertTrue(GroupOrderStatus.OPEN.canTransitionTo(GroupOrderStatus.LOCKED));
        assertTrue(GroupOrderStatus.LOCKED.canTransitionTo(GroupOrderStatus.CONFIRMED));
        assertTrue(GroupOrderStatus.CONFIRMED.canTransitionTo(GroupOrderStatus.PROCESSING));
        assertTrue(GroupOrderStatus.PROCESSING.canTransitionTo(GroupOrderStatus.SHIPPED));
        assertTrue(GroupOrderStatus.SHIPPED.canTransitionTo(GroupOrderStatus.DELIVERED));
        assertTrue(GroupOrderStatus.DELIVERED.isTerminal());
        assertTrue(GroupOrderStatus.CANCELLED.isTerminal());
    }

    @Test
    void canUnlockBackToOpen() {
        assertTrue(GroupOrderStatus.LOCKED.canTransitionTo(GroupOrderStatus.OPEN));
    }

    @Test
    void cannotSkipStates() {
        assertFalse(GroupOrderStatus.OPEN.canTransitionTo(GroupOrderStatus.CONFIRMED));
        assertFalse(GroupOrderStatus.OPEN.canTransitionTo(GroupOrderStatus.DELIVERED));
        assertFalse(GroupOrderStatus.CONFIRMED.canTransitionTo(GroupOrderStatus.OPEN));
        assertFalse(GroupOrderStatus.SHIPPED.canTransitionTo(GroupOrderStatus.CONFIRMED));
    }

    @Test
    void prePlacementCoversTheEditableWindow() {
        assertTrue(GroupOrderStatus.OPEN.isPrePlacement());
        assertTrue(GroupOrderStatus.LOCKED.isPrePlacement());
        assertTrue(GroupOrderStatus.AWAITING_PAYMENT.isPrePlacement());
        assertFalse(GroupOrderStatus.CONFIRMED.isPrePlacement());
        assertFalse(GroupOrderStatus.CANCELLED.isPrePlacement());
    }

    // ------------------------------------------------------- share status

    @Test
    void offlineSettlementLane() {
        assertTrue(GroupShareStatus.OWED.canTransitionTo(GroupShareStatus.REPORTED_PAID));
        assertTrue(GroupShareStatus.REPORTED_PAID.canTransitionTo(GroupShareStatus.CONFIRMED_RECEIVED));
        assertTrue(GroupShareStatus.CONFIRMED_RECEIVED.isSettled());
        assertFalse(GroupShareStatus.OWED.canTransitionTo(GroupShareStatus.CONFIRMED_RECEIVED));
        assertFalse(GroupShareStatus.OWED.canTransitionTo(GroupShareStatus.PAID));
    }

    @Test
    void nothingLetsAnUnpaidShareBecomePaidWithoutAGateway() {
        assertFalse(GroupShareStatus.REPORTED_PAID.canTransitionTo(GroupShareStatus.PAID));
        assertFalse(GroupShareStatus.CONFIRMED_RECEIVED.canTransitionTo(GroupShareStatus.PAID));
        assertFalse(GroupShareStatus.PENDING.canTransitionTo(GroupShareStatus.CONFIRMED_RECEIVED));
    }

    @Test
    void gatewayLaneIsFutureProof() {
        assertTrue(GroupShareStatus.PENDING.canTransitionTo(GroupShareStatus.PAYMENT_INITIATED));
        assertTrue(GroupShareStatus.PAYMENT_INITIATED.canTransitionTo(GroupShareStatus.PAID));
        assertTrue(GroupShareStatus.PAYMENT_INITIATED.canTransitionTo(GroupShareStatus.FAILED));
        assertTrue(GroupShareStatus.FAILED.canTransitionTo(GroupShareStatus.PENDING));
        assertTrue(GroupShareStatus.PAID.canTransitionTo(GroupShareStatus.REFUNDED));
        assertTrue(GroupShareStatus.REFUNDED.isTerminal());
    }
}
