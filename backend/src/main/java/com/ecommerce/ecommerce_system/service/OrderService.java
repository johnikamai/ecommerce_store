package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.*;
import com.ecommerce.ecommerce_system.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    /** Used when a parcel ships without the warehouse naming a courier. */
    private static final String DEFAULT_CARRIER = "ShopEase Logistics";

    /** Working-day estimate shown as the promised delivery date. */
    private static final int TRANSIT_DAYS = 4;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private StockAlertService stockAlertService;

    private Order prepareOrder(OrderRequest request, boolean redeem) {
        validateRequest(request);
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.getCustomerId()));

        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PLACED);

        // Delivery snapshot: explicit address from checkout, else the customer's saved one.
        String address = request.getShippingAddress() != null && !request.getShippingAddress().isBlank()
                ? request.getShippingAddress().trim()
                : customer.getShippingAddress();
        order.setShippingAddress(address);

        // Payment method: CASH (COD) / UPI / CARD.
        String method = request.getPaymentMethod();
        if (method != null && !method.isBlank()) {
            order.setPaymentMethod(method.toUpperCase());
        }

        BigDecimal total = BigDecimal.ZERO;

        for (OrderRequest.Item itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + itemReq.getProductId()));

            Integer quantity = itemReq.getQuantity();
            if (quantity == null || quantity < 1) {
                throw new IllegalArgumentException("Invalid quantity for product: " + product.getName());
            }

            if (product.getStockQuantity() == null || product.getStockQuantity() < quantity) {
                throw new IllegalStateException("Insufficient stock for product: " + product.getName());
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(quantity);
            orderItem.setUnitPrice(product.getPrice()); // snapshot price at order time

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            orderItem.setLineTotal(lineTotal);

            order.getOrderItems().add(orderItem);
            total = total.add(lineTotal);
        }

        BigDecimal subtotal = total;

        // Optional promo code: PERCENT or FIXED discount, validates active/expiry/limits.
        BigDecimal couponDiscount = BigDecimal.ZERO;
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            Coupon coupon = couponRepository.findByCodeIgnoreCase(request.getCouponCode().trim())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid coupon code: " + request.getCouponCode().trim()));
            // Re-read under a write lock. The limit is checked and then spent, and
            // without the lock two shoppers redeeming the final use at the same
            // moment would both pass the check and the coupon would be honoured
            // twice while recording one use. Locking serialises them so the second
            // customer sees the exhausted limit instead.
            if (redeem) coupon = couponRepository.findByCodeIgnoreCaseForUpdate(request.getCouponCode().trim())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid coupon code: " + request.getCouponCode().trim()));
            if (!coupon.isRedeemable()) {
                throw new IllegalArgumentException("Coupon is no longer valid: " + coupon.getCode());
            }
            if (coupon.getMinimumOrderAmount() != null
                    && subtotal.compareTo(coupon.getMinimumOrderAmount()) < 0) {
                throw new IllegalArgumentException("Coupon requires a minimum order of ₹" + coupon.getMinimumOrderAmount());
            }
            if (coupon.getDiscountType() == com.ecommerce.ecommerce_system.model.CouponType.PERCENT) {
                couponDiscount = subtotal
                        .multiply(coupon.getDiscountValue())
                        .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            } else {
                couponDiscount = coupon.getDiscountValue();
            }
            if (couponDiscount.compareTo(subtotal) > 0) {
                couponDiscount = subtotal;
            }
            if (redeem) {
                coupon.setTimesUsed(coupon.getTimesUsed() + 1);
                couponRepository.save(coupon);
            }

            order.setCouponCode(coupon.getCode());
            order.setDiscountAmount(couponDiscount);
            total = total.subtract(couponDiscount);
        }

        // Bundle tier: buying more *different* products in one order earns an
        // extra discount. Applied after the coupon so the two stack - a coupon
        // is never swallowed by the bundle, which is the whole point of showing
        // both lines on the invoice.
        int distinctProducts = (int) order.getOrderItems().stream()
                .map(item -> item.getProduct())
                .filter(java.util.Objects::nonNull)
                .map(Product::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        BigDecimal bundlePercent = bundleDiscountPercent(distinctProducts);
        BigDecimal bundleDiscount = total
                .multiply(bundlePercent)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        order.setBundleDiscountAmount(bundleDiscount);
        total = total.subtract(bundleDiscount);

        // Apply tier-based discount (tier may be null for legacy customers -> treat as BRONZE)
        String tier = customer.getTier() != null ? customer.getTier() : "BRONZE";
        java.math.BigDecimal discountPercent = switch (tier) {
            case "GOLD" -> java.math.BigDecimal.valueOf(0.05);
            case "SILVER" -> java.math.BigDecimal.valueOf(0.02);
            default -> java.math.BigDecimal.ZERO;
        };
java.math.BigDecimal discount = total.multiply(discountPercent).setScale(2, java.math.RoundingMode.HALF_UP);
        total = total.subtract(discount);

        // Everything above is a discount on the goods. Freeze that figure here,
        // because shipping and tax are charges rather than discounts and must not
        // be fed back into loyalty points or the lifetime-spend tiers below.
        BigDecimal merchandiseTotal = total;

        // Delivery. Charged on the pre-discount subtotal, so stacking a coupon can
        // never buy free shipping.
        BigDecimal shipping = shippingCostFor(subtotal);
        BigDecimal tax = taxFor(merchandiseTotal);

        total = total.add(shipping).add(tax);

        order.setMerchandiseTotal(merchandiseTotal);
        order.setShippingAmount(shipping);
        order.setTaxAmount(tax);
        order.setTotalAmount(total);

        return order;
    }

    @org.springframework.beans.factory.annotation.Value("${app.payments.demo-enabled:false}")
    private boolean demoPaymentsEnabled;

    @Transactional
    public Order placeOrder(OrderRequest request) {
        Order order = prepareOrder(request, true);
        Customer customer = order.getCustomer();
        BigDecimal total = order.getTotalAmount();
        if (request.getExpectedTotal() == null || total.compareTo(request.getExpectedTotal()) != 0) {
            throw new IllegalArgumentException("Checkout total changed. Review the updated quote and try again.");
        }
        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            Integer stockBefore = product.getStockQuantity();
            product.setStockQuantity(stockBefore - item.getQuantity());
            productRepository.saveAndFlush(product); // version check before sending stock notifications
            stockAlertService.onStockChanged(product, stockBefore);
        }
        Order saved = orderRepository.save(order);

        // Record payment: CASH (COD) stays PENDING until collected; UPI/CARD simulate an
        // instantly-approved online gateway.
        if (order.getPaymentMethod() != null) {
            Payment payment = new Payment();
            payment.setOrder(saved);
            try {
                payment.setPaymentMode(PaymentMode.valueOf(order.getPaymentMethod()));
            } catch (IllegalArgumentException e) {
                payment.setPaymentMode(PaymentMode.CASH);
            }
            payment.setAmount(saved.getTotalAmount());
            payment.setTransactionDate(LocalDateTime.now());
            if (payment.getPaymentMode() == PaymentMode.CASH) {
                payment.setPaymentStatus(PaymentStatus.PENDING);
            } else {
                payment.setPaymentStatus(PaymentStatus.PAID);
                // Stand-in for the gateway reference a real integration would return.
                payment.setTransactionRef("MOCK-" + saved.getId() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }
            payment.setStatusUpdatedAt(LocalDateTime.now());
            payment.setStatusUpdatedBy("checkout");
            paymentRepository.save(payment);

            saved.setPaymentStatus(payment.getPaymentStatus());
            orderRepository.save(saved);

            notificationService.paymentMade(customer, saved.getId(), payment.getPaymentMode().name(), payment.getPaymentStatus().name());
        }

        // Order confirmation + email.
        notificationService.orderPlaced(customer, saved.getId(), total.toPlainString());

        // Opens the tracking timeline so the order is not born with an empty
        // history; every later milestone appends to it.
        saved.addStatusEvent(new OrderStatusEvent(
                null, saved, OrderStatus.PLACED, "Order placed", saved.getShippingAddress(), LocalDateTime.now()));
        saved = orderRepository.save(saved);

        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> quote(OrderRequest request) {
        Order order = prepareOrder(request, false);
        BigDecimal subtotal = order.getOrderItems().stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tierDiscount = subtotal.subtract(order.getDiscountAmount()).subtract(order.getBundleDiscountAmount()).subtract(order.getMerchandiseTotal());
        Map<String,Object> quote = new LinkedHashMap<>();
        quote.put("subtotal", subtotal); quote.put("couponDiscount", order.getDiscountAmount());
        quote.put("bundleDiscount", order.getBundleDiscountAmount()); quote.put("tierDiscount", tierDiscount);
        quote.put("shipping", order.getShippingAmount()); quote.put("tax", order.getTaxAmount());
        quote.put("total", order.getTotalAmount()); quote.put("demoPaymentsEnabled", demoPaymentsEnabled);
        return quote;
    }

    private void validateRequest(OrderRequest request) {
        if (request == null || request.getCustomerId() == null) throw new IllegalArgumentException("Customer is required");
        if (request.getItems() == null || request.getItems().isEmpty() || request.getItems().size() > 100) throw new IllegalArgumentException("Order must contain 1–100 products");
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (OrderRequest.Item item : request.getItems()) {
            if (item == null || item.getProductId() == null || item.getQuantity() == null || item.getQuantity() < 1 || item.getQuantity() > 10000) throw new IllegalArgumentException("Invalid product or quantity");
            if (!ids.add(item.getProductId())) throw new IllegalArgumentException("Combine duplicate product lines");
        }
        if (request.getShippingAddress() == null || request.getShippingAddress().trim().length() < 10 || request.getShippingAddress().length() > 1000) throw new IllegalArgumentException("Enter a complete delivery address (10–1000 characters)");
        String method = request.getPaymentMethod();
        if (method == null || !java.util.Set.of("CASH", "UPI", "CARD").contains(method)) throw new IllegalArgumentException("Choose CASH, UPI or CARD");
        if (!demoPaymentsEnabled && !method.equals("CASH")) throw new IllegalArgumentException("Online payments are unavailable. Choose cash on delivery.");
    }

    private void awardRewards(Order order) {
        Customer customer = order.getCustomer();
        if (Boolean.TRUE.equals(order.getRewardsAwarded())) return;
        int earned = order.getMerchandiseTotalForScoring().divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.DOWN).intValue();
        customer.setLoyaltyPoints((customer.getLoyaltyPoints() == null ? 0 : customer.getLoyaltyPoints()) + earned);
        order.setRewardsAwarded(true);
        if (customer.getReferredBy() != null && !Boolean.TRUE.equals(customer.getReferralRewarded())) {
            Customer referrer = customer.getReferredBy();
            referrer.setLoyaltyPoints((referrer.getLoyaltyPoints() == null ? 0 : referrer.getLoyaltyPoints()) + 500);
            customerRepository.save(referrer); customer.setReferralRewarded(true);
        }
        recomputeTier(customer);
        customerRepository.save(customer);
    }

    private void recomputeTier(Customer customer) {
        BigDecimal spent = orderRepository.findByCustomerId(customer.getId()).stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED && o.getPaymentStatus() != PaymentStatus.REFUNDED)
                .map(Order::getMerchandiseTotalForScoring).reduce(BigDecimal.ZERO, BigDecimal::add);
        customer.setTier(spent.compareTo(BigDecimal.valueOf(150000)) >= 0 ? "GOLD" : spent.compareTo(BigDecimal.valueOf(50000)) >= 0 ? "SILVER" : "BRONZE");
    }

    /** Reverse previously awarded rewards, including legacy orders awarded at checkout. */
    @Transactional
    public void reverseRewards(Order order) {
        Customer customer = order.getCustomer();
        if (Boolean.TRUE.equals(order.getRewardsAwarded())) {
            int earned = order.getMerchandiseTotalForScoring().divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.DOWN).intValue();
            customer.setLoyaltyPoints(Math.max(0, (customer.getLoyaltyPoints() == null ? 0 : customer.getLoyaltyPoints()) - earned));
            order.setRewardsAwarded(false);
        }
        boolean otherCompleted = orderRepository.findByCustomerId(customer.getId()).stream()
                .anyMatch(o -> !o.getId().equals(order.getId()) && o.getStatus() == OrderStatus.DELIVERED && o.getPaymentStatus() != PaymentStatus.REFUNDED);
        if (!otherCompleted && Boolean.TRUE.equals(customer.getReferralRewarded()) && customer.getReferredBy() != null) {
            Customer referrer = customer.getReferredBy();
            referrer.setLoyaltyPoints(Math.max(0, (referrer.getLoyaltyPoints() == null ? 0 : referrer.getLoyaltyPoints()) - 500));
            customerRepository.save(referrer); customer.setReferralRewarded(false);
        }
        recomputeTier(customer); customerRepository.save(customer); orderRepository.save(order);
    }

    /**
     * Customer cancels their own PLACED order. Releases the reserved stock and
     * notifies the customer.
     */
    @Transactional
    public Order cancelOrder(Long orderId, Long customerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!order.getCustomer().getId().equals(customerId)) {
            throw new IllegalStateException("You can only cancel your own orders");
        }
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new IllegalStateException("Only orders that are still PLACED can be cancelled (current: " + order.getStatus() + ")");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.addStatusEvent(new OrderStatusEvent(
                null, order, OrderStatus.CANCELLED, "Cancelled by customer", order.getShippingAddress(), LocalDateTime.now()));

        // If the customer had paid online, issue the refund right away.
        boolean refunded = refundIfPaid(order);

        reverseRewards(order);

        // Release the reserved stock back.
        releaseStock(order);

        Order saved = orderRepository.save(order);

        // Notify alongside the order.
        notificationService.orderCancelled(order.getCustomer(), saved.getId());
        if (refunded) {
            notificationService.refundProcessed(order.getCustomer(), saved.getId(), saved.getTotalAmount().toPlainString());
        }

        return saved;
    }

    /** When a paid (UPI/CARD) order is cancelled, flip payment to REFUNDED and notify via email. */
    private boolean refundIfPaid(Order order) {
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
            if (payment != null) {
                payment.setPaymentStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
            }
            return true;
        }
        return false;
    }

    /** Puts every ordered unit back into stock. Stock lives on Product.stockQuantity. */
    private void releaseStock(Order order) {
        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            Integer stockBefore = product.getStockQuantity();
            product.setStockQuantity((stockBefore == null ? 0 : stockBefore) + item.getQuantity());
            Product saved = productRepository.save(product);
            // Returning stock can bring a sold-out product back - tell everyone.
            stockAlertService.onStockChanged(saved, stockBefore);
        }
    }

    /**
     * Shared status transition used by both the admin console and the shipping flow.
     * Emits SHIPPING / DELIVERY notifications.
     *
     * The legal-move rules live on OrderStatus (DELIVERED and CANCELLED are
     * terminal, so an order can never be flipped back to CANCELLED twice - which
     * would release its stock twice - nor reopened after it closed).
     */
    @Transactional
    public Order updateStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        OrderStatus previous = order.getStatus();
        if (!previous.canTransitionTo(newStatus)) {
            throw new IllegalArgumentException("Cannot change order #" + orderId + " from "
                    + previous + " to " + newStatus
                    + (previous.isTerminal() ? ". This order is final." : ". Allowed next: " + previous.allowedNextOrdered()));
        }

        order.setStatus(newStatus);
        applyTrackingMilestone(order, previous, newStatus);
        Order saved = orderRepository.save(order);

        Customer customer = order.getCustomer();
        if (newStatus == OrderStatus.SHIPPED) {
            notificationService.shipped(customer, saved.getId());
        } else if (newStatus == OrderStatus.DELIVERED) {
            awardRewards(order);
            notificationService.delivered(customer, saved.getId());
        } else if (newStatus == OrderStatus.CANCELLED) {
            boolean refunded = refundIfPaid(order);
            reverseRewards(order);
            // Stock is only reserved while the order is still PLACED. Once it has
            // shipped the units have left the warehouse, so cancelling must not
            // put them back on the shelf.
            if (previous == OrderStatus.PLACED || previous == OrderStatus.PACKED) {
                releaseStock(order);
            }
            notificationService.orderCancelled(customer, saved.getId());
            if (refunded) {
                notificationService.refundProcessed(customer, saved.getId(), saved.getTotalAmount().toPlainString());
            }
        }

        return saved;
    }

    /**
     * Stamps the tracking fields and appends a scan whenever the parcel moves.
     *
     * Timestamps are only ever written once, so a repeated transition onto the
     * same milestone cannot overwrite the real ship time with a later one.
     */
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

    /**
     * Assigns courier details by hand, for orders shipped outside the normal flow.
     * Refuses to overwrite a tracking number that already exists, because
     * customers quote it to the carrier and a silent change breaks that lookup.
     */
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

        return orderRepository.save(order);
    }

    /**
     * Builds the customer-facing tracking view: the milestones this order
     * actually passed through, each paired with its scan time.
     */
    @Transactional(readOnly = true)
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
        boolean reachedAnything = !events.isEmpty();
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

    /**
     * Bundle discount earned by buying several different products in one order.
     *
     * <p>Tiers: 2 products 5%, 3 products 10%, 4 or more 15%. A single product
     * earns nothing. Quantities do not count towards a tier - buying three of
     * the same shirt is not a bundle, and counting it that way would let one
     * shopper clear the whole ladder with a single line.
     *
     * <p>Public and static so the checkout screen, the cart preview and the
     * tests all read the same table instead of three copies that drift.
     */
    public static BigDecimal bundleDiscountPercent(int distinctProducts) {
        if (distinctProducts >= 4) {
            return new BigDecimal("0.15");
        }
        if (distinctProducts == 3) {
            return new BigDecimal("0.10");
        }
        if (distinctProducts == 2) {
            return new BigDecimal("0.05");
        }
        return BigDecimal.ZERO;
    }

    /** Flat delivery charge, in the same currency as the catalogue prices. */
    public static final BigDecimal FLAT_SHIPPING = new BigDecimal("49.00");

    /** Spend at or above which delivery is charged at nothing. */
    public static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("999.00");

    /**
     * Default tax rate as a fraction of the discounted goods value.
     *
     * This is a single flat rate standing in for GST. A real deployment needs the
     * CGST/SGST split for an intra-state delivery and IGST for an inter-state one,
     * decided from the pair of state codes on the shipping address - see
     * taxRate() for the runtime override.
     */
    public static final BigDecimal TAX_RATE = new BigDecimal("0.18");

    /**
     * Delivery charged for an order.
     *
     * Free once the basket crosses the threshold, and free for an empty basket:
     * a zero-value order should not be charged shipping for sending nothing.
     * Measured against the pre-discount subtotal so that stacking coupons cannot
     * tip an order over the line and make delivery free, which would let a
     * customer buy a large basket at no shipping cost purely by applying a code.
     */
    public static BigDecimal shippingCostFor(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("0.00");
        }
        if (subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
            return new BigDecimal("0.00");
        }
        return FLAT_SHIPPING;
    }

    /**
     * Tax charged on the discounted value of the goods.
     *
     * The base is deliberately the post-discount goods value, never the grand
     * total: folding shipping into the base would tax the delivery charge as
     * though it were merchandise. Returns a zero scale so an unrounded rate can
     * never leak a long decimal into the stored amount.
     */
    public static BigDecimal taxFor(BigDecimal merchandiseTotal) {
        if (merchandiseTotal == null || merchandiseTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return merchandiseTotal
                .multiply(taxRate())
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Configurable so a deployment can change the rate without a code change. */
    private static BigDecimal taxRate() {
        String configured = System.getenv("TAX_RATE");
        if (configured == null || configured.isBlank()) {
            return TAX_RATE;
        }
        try {
            BigDecimal parsed = new BigDecimal(configured.trim());
            if (parsed.compareTo(BigDecimal.ZERO) < 0 || parsed.compareTo(BigDecimal.ONE) > 0) {
                System.out.println("[TAX] TAX_RATE " + configured + " is not a 0-1 fraction, using default");
                return TAX_RATE;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            System.out.println("[TAX] TAX_RATE " + configured + " is not a number, using default");
            return TAX_RATE;
        }
    }

    /** The next tier up and how many more distinct products unlock it, or null at the top. */
    public static int nextBundleTier(int distinctProducts) {
        if (distinctProducts < 2) {
            return 2;
        }
        if (distinctProducts < 3) {
            return 3;
        }
        if (distinctProducts < 4) {
            return 4;
        }
        return 0;
    }

    private static String generateTrackingNumber() {
        return "SE" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}