package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Order;
import com.ecommerce.ecommerce_system.model.OrderStatus;
import com.ecommerce.ecommerce_system.model.OrderStatusEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Serialization contract for order tracking.
 *
 * Tracking data is served exclusively by GET /api/orders/{id}/tracking. These
 * tests pin that down: if the fields ever creep back onto the order JSON, the two
 * sources drift and every order list response pays for a scan-history query it
 * never renders.
 */
class OrderSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static Order shippedOrder() {
        Order order = new Order();
        order.setId(42L);
        order.setStatus(OrderStatus.SHIPPED);
        order.setTrackingNumber("SEABC1234567890");
        order.setCarrier("ShopEase Logistics");
        order.setExpectedDelivery(LocalDateTime.now().plusDays(4));
        order.setShippedAt(LocalDateTime.now());
        order.setOrderDate(LocalDateTime.now());
        order.addStatusEvent(new OrderStatusEvent(
                null, order, OrderStatus.PLACED, "Order placed", "Bengaluru", LocalDateTime.now()));
        return order;
    }

    @Test
    void orderJsonOmitsTrackingFields() throws Exception {
        String json = mapper.writeValueAsString(shippedOrder());

        assertFalse(json.contains("trackingNumber"), "tracking number must come from the tracking endpoint");
        assertFalse(json.contains("carrier"), "carrier must come from the tracking endpoint");
        assertFalse(json.contains("expectedDelivery"));
        assertFalse(json.contains("shippedAt"));
    }

    @Test
    void orderJsonOmitsScanHistory() throws Exception {
        String json = mapper.writeValueAsString(shippedOrder());

        // The tracker reads events from the endpoint; embedding them here would
        // add a query per order on every list response.
        assertFalse(json.contains("statusEvents"));
        assertFalse(json.contains("Order placed"), "scan notes belong to the tracking endpoint");
    }

    @Test
    void orderJsonKeepsCommerceFields() throws Exception {
        String json = mapper.writeValueAsString(shippedOrder());

        // The point of the change is de-duplication, not losing data: everything
        // the orders list actually renders must still be present.
        assertTrue(json.contains("\"id\""));
        assertTrue(json.contains("SHIPPED"));
        assertTrue(json.contains("orderDate"));
    }

    @Test
    void trackingViewStillCarriesEverythingOrderNoLongerDoes() {
        // Guards the hand-off. trackingFor reads only the Order it is given, so
        // it can be exercised without any repository wiring.
        Order order = shippedOrder();
        order.setDeliveredAt(LocalDateTime.now());

        Map<String, Object> view = new OrderService().trackingFor(order);

        assertEquals("SEABC1234567890", view.get("trackingNumber"));
        assertEquals("ShopEase Logistics", view.get("carrier"));
        assertNotNull(view.get("expectedDelivery"));
        assertNotNull(view.get("shippedAt"));
        assertNotNull(view.get("events"), "scan history moves to the tracking endpoint");
        assertNotNull(view.get("steps"), "milestone rail is derived, so it can only live here");
    }

    @Test
    void unreachedStepsAreOmittedRatherThanReturnedEmpty() {
        Order order = new Order();
        order.setId(7L);
        order.setStatus(OrderStatus.SHIPPED);
        order.setOrderDate(LocalDateTime.now());
        // Reached PLACED, then jumped straight to SHIPPED with no PACKED scan.
        order.addStatusEvent(new OrderStatusEvent(
                null, order, OrderStatus.PLACED, "Order placed", null, LocalDateTime.now()));
        order.addStatusEvent(new OrderStatusEvent(
                null, order, OrderStatus.SHIPPED, "Handed to courier", "Bengaluru", LocalDateTime.now()));

        Map<String, Object> view = new OrderService().trackingFor(order);

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> steps = (java.util.List<Map<String, Object>>) view.get("steps");
        java.util.List<String> statuses = steps.stream().map(s -> (String) s.get("status")).toList();

        assertEquals(java.util.List.of("PLACED", "SHIPPED", "DELIVERED"), statuses);
        assertFalse(statuses.contains("PACKED"), "a skipped waypoint must not render an empty step");
    }

    @Test
    void statusEventNeverSerializesItsParentOrder() throws Exception {
        Order order = shippedOrder();
        OrderStatusEvent event = order.getStatusEvents().get(0);

        String json = mapper.writeValueAsString(event);

        // Without this the back-reference guard, an event would drag the whole
        // order (and its items) along with it.
        assertFalse(json.contains("\"order\""));
        assertTrue(json.contains("PLACED"));
        assertTrue(json.contains("Order placed"));
    }
}
