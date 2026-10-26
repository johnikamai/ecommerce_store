package com.ecommerce.order.service;
import com.ecommerce.order.model.*;
import com.ecommerce.order.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
@Service
public class OrderService {
    private static final String DEFAULT_CARRIER="ShopEase Logistics";private static final int TRANSIT_DAYS=4;
    private final OrderRepository orderRepository;private final OrderPricingService pricing;private final CheckoutCoordinator checkout;private final NotificationService notificationService;private final OrderEvents events;
    public OrderService(OrderRepository orders,OrderPricingService pricing,CheckoutCoordinator checkout,NotificationService notificationService,OrderEvents events){this.orderRepository=orders;this.pricing=pricing;this.checkout=checkout;this.notificationService=notificationService;this.events=events;}
    public boolean isDemoPaymentsEnabled(){return pricing.isDemoPaymentsEnabled();}
    public Order placeOrder(OrderRequest request){return checkout.place(request);}
    public Map<String,Object> quote(OrderRequest request){return pricing.quote(request);}
    public Order cancelOrder(Long orderId,Long customerId){return checkout.cancel(orderId,customerId,false);}
    @Transactional
    public void reverseRewards(Order order){order.setRewardsAwarded(false);orderRepository.saveAndFlush(order);events.updated(order);}
    @Transactional
    public Order updateStatus(Long id,OrderStatus next){
        if(next==OrderStatus.CANCELLED){var current=orderRepository.findById(id).orElseThrow();return checkout.cancel(id,current.getCustomerId(),true);}
        var order=orderRepository.lock(id).orElseThrow();var previous=order.getStatus();
        if(!previous.canTransitionTo(next))throw new IllegalArgumentException("Cannot change order from "+previous+" to "+next+". Allowed next: "+previous.allowedNextOrdered());
        order.setStatus(next);applyTrackingMilestone(order,previous,next);
        if(next==OrderStatus.SHIPPED)notificationService.shipped(order.getCustomer(),id);
        if(next==OrderStatus.DELIVERED){order.setRewardsAwarded(true);notificationService.delivered(order.getCustomer(),id);}
        orderRepository.saveAndFlush(order);events.updated(order);return order;
    }
    private void applyTrackingMilestone(Order order, OrderStatus previous, OrderStatus next) {
        LocalDateTime now = LocalDateTime.now();

        if (next == OrderStatus.SHIPPED) {
            if (order.getShippedAt() == null) {
                order.setShippedAt(now);
            }
            // A consignment number is issued by the courier at handover. Generate
            // one only if the warehouse has not supplied its own.
            if (isBlank(order.getTrackingNumber())) {
                order.setTrackingNumber(generateTrackingNumber());
            }
            if (isBlank(order.getCarrier())) {
                order.setCarrier(DEFAULT_CARRIER);
            }
            if (order.getExpectedDelivery() == null) {
                order.setExpectedDelivery(now.plusDays(TRANSIT_DAYS));
            }
        }

        if (next == OrderStatus.DELIVERED && order.getDeliveredAt() == null) {
            order.setDeliveredAt(now);
        }

        order.addStatusEvent(new OrderStatusEvent(
                null,
                order,
                next,
                describeMilestone(next, order),
                order.getShippingAddress(),
                now
        ));
    }
    private static String describeMilestone(OrderStatus status, Order order) {
        return switch (status) {
            case PLACED -> "Order placed";
            case PACKED -> "Packed at warehouse";
            case SHIPPED -> "Handed to " + (isBlank(order.getCarrier()) ? DEFAULT_CARRIER : order.getCarrier());
            case OUT_FOR_DELIVERY -> "Out for delivery";
            case DELIVERED -> "Delivered";
            case CANCELLED -> "Order cancelled";
        };
    }
    @Transactional
    public Order assignTracking(Long orderId, String carrier, String trackingNumber, LocalDateTime expectedDelivery) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (isBlank(trackingNumber)) {
            throw new IllegalArgumentException("Tracking number is required");
        }
        if (!isBlank(order.getTrackingNumber()) && !order.getTrackingNumber().equalsIgnoreCase(trackingNumber.trim())) {
            throw new IllegalArgumentException("Order #" + orderId + " already has tracking number "
                    + order.getTrackingNumber());
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Order #" + orderId + " is " + order.getStatus()
                    + " and cannot be handed to a carrier");
        }

        order.setTrackingNumber(trackingNumber.trim());
        if (!isBlank(carrier)) {
            order.setCarrier(carrier.trim());
        }
        if (expectedDelivery != null) {
            order.setExpectedDelivery(expectedDelivery);
        }
        if (order.getShippedAt() == null) {
            order.setShippedAt(LocalDateTime.now());
            if (order.getExpectedDelivery() == null) {
                order.setExpectedDelivery(LocalDateTime.now().plusDays(TRANSIT_DAYS));
            }
        }
        order.addStatusEvent(new OrderStatusEvent(
                null,
                order,
                order.getStatus(),
                "Tracking updated: " + order.getTrackingNumber(),
                order.getShippingAddress(),
                LocalDateTime.now()
        ));

        orderRepository.saveAndFlush(order);events.updated(order);return order;
    }
    @Transactional(readOnly=true)
    public Map<String, Object> trackingFor(Order order) {
        List<OrderStatusEvent> events = new ArrayList<>(order.getStatusEvents());
        events.sort(Comparator.comparing(OrderStatusEvent::getCreatedAt));

        // Latest scan per milestone, so a status that was toggled more than once
        // shows the most recent handover rather than the first.
        Map<OrderStatus, OrderStatusEvent> scanByStatus = new LinkedHashMap<>();
        for (OrderStatusEvent event : events) {
            scanByStatus.put(event.getStatus(), event);
        }

        // PLACED is always recorded at checkout, but fall back to the order date
        // so an order placed before this feature shipped still has a start.
        List<Map<String, Object>> steps = new ArrayList<>();
        for (OrderStatus milestone : OrderStatus.fulfilmentPath()) {
            OrderStatusEvent scan = scanByStatus.get(milestone);
            boolean reached = scan != null;
            if (milestone == OrderStatus.PLACED && !reached) {
                reached = true;
            }
            // PACKED and OUT_FOR_DELIVERY are optional waypoints; drop the ones
            // this order skipped instead of rendering a step that never happened.
            if (!reached && milestone != OrderStatus.PLACED && milestone != OrderStatus.SHIPPED
                    && milestone != OrderStatus.DELIVERED) {
                continue;
            }

            Map<String, Object> step = new LinkedHashMap<>();
            step.put("status", milestone.name());
            step.put("reached", reached);
            step.put("at", reached
                    ? (scan != null ? scan.getCreatedAt().toString() : order.getOrderDate().toString())
                    : null);
            step.put("note", scan == null || scan.getNote() == null ? "" : scan.getNote());
            steps.add(step);
        }

        List<Map<String, Object>> history = new ArrayList<>();
        for (OrderStatusEvent event : events) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", event.getId());
            row.put("status", event.getStatus().name());
            row.put("note", event.getNote());
            row.put("location", event.getLocation());
            row.put("createdAt", event.getCreatedAt().toString());
            history.add(row);
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("orderId", order.getId());
        view.put("status", order.getStatus().name());
        view.put("cancelled", order.getStatus() == OrderStatus.CANCELLED);
        // A delivered or cancelled order still has a timeline worth reading, so
        // this only gates the "live" parcel view, not the history itself.
        view.put("trackable", order.getTrackingNumber() != null && order.getStatus().isInTransit());
        view.put("trackingNumber", order.getTrackingNumber());
        view.put("carrier", order.getCarrier());
        view.put("shippedAt", order.getShippedAt() == null ? null : order.getShippedAt().toString());
        view.put("deliveredAt", order.getDeliveredAt() == null ? null : order.getDeliveredAt().toString());
        view.put("expectedDelivery", order.getExpectedDelivery() == null ? null : order.getExpectedDelivery().toString());
        view.put("shippingAddress", order.getShippingAddress());
        view.put("steps", steps);
        view.put("events", history);
        return view;
    }
    private static String generateTrackingNumber() {
        return "SE" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
    }
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
    public static final BigDecimal FLAT_SHIPPING=OrderPricingService.FLAT_SHIPPING,FREE_SHIPPING_THRESHOLD=OrderPricingService.FREE_SHIPPING_THRESHOLD,TAX_RATE=OrderPricingService.TAX_RATE;
    public static BigDecimal bundleDiscountPercent(int n){return OrderPricingService.bundleDiscountPercent(n);}
    public static BigDecimal shippingCostFor(BigDecimal subtotal){return OrderPricingService.shippingCostFor(subtotal);}
    public static BigDecimal taxFor(BigDecimal merchandise){return OrderPricingService.taxFor(merchandise);}
    public static int nextBundleTier(int n){return OrderPricingService.nextBundleTier(n);}
}
