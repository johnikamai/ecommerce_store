package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.*;
import com.ecommerce.ecommerce_system.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

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

    @Transactional
    public Order placeOrder(OrderRequest request) {
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

            if (product.getStockQuantity() < quantity) {
                throw new IllegalStateException("Insufficient stock for product: " + product.getName());
            }

            // Deduct stock
            Integer stockBefore = product.getStockQuantity();
            product.setStockQuantity(product.getStockQuantity() - quantity);
            Product savedProduct = productRepository.save(product);
            stockAlertService.onStockChanged(savedProduct, stockBefore);

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
            coupon.setTimesUsed(coupon.getTimesUsed() + 1);
            couponRepository.save(coupon);

            order.setCouponCode(coupon.getCode());
            order.setDiscountAmount(couponDiscount);
            total = total.subtract(couponDiscount);
        }

        // Apply tier-based discount (tier may be null for legacy customers -> treat as BRONZE)
        String tier = customer.getTier() != null ? customer.getTier() : "BRONZE";
        java.math.BigDecimal discountPercent = switch (tier) {
            case "GOLD" -> java.math.BigDecimal.valueOf(0.05);
            case "SILVER" -> java.math.BigDecimal.valueOf(0.02);
            default -> java.math.BigDecimal.ZERO;
        };
        java.math.BigDecimal discount = total.multiply(discountPercent);
        total = total.subtract(discount);

        order.setTotalAmount(total);

        // Award loyalty points: 1 point per ₹100 spent
        int pointsEarned = total.divide(java.math.BigDecimal.valueOf(100), 0, java.math.RoundingMode.DOWN).intValue();
        int currentPoints = customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0;
        customer.setLoyaltyPoints(currentPoints + pointsEarned);

        // Recalculate tier based on lifetime spend across all orders
        java.math.BigDecimal lifetimeSpend = orderRepository.findByCustomerId(customer.getId()).stream()
                .map(Order::getTotalAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                .add(total); // include this order too, since it isn't saved yet

        if (lifetimeSpend.compareTo(java.math.BigDecimal.valueOf(150000)) >= 0) {
            customer.setTier("GOLD");
        } else if (lifetimeSpend.compareTo(java.math.BigDecimal.valueOf(50000)) >= 0) {
            customer.setTier("SILVER");
        }

        // REFERRAL BONUS:
        // When a referred customer places their FIRST order, reward the referrer
        // with bonus loyalty points (exactly once per referred friend).
        int referralBonus = 500;
        boolean isFirstOrder = orderRepository.findByCustomerId(customer.getId()).isEmpty();
        if (isFirstOrder
                && customer.getReferredBy() != null
                && !Boolean.TRUE.equals(customer.getReferralRewarded())) {
            Customer referrer = customer.getReferredBy();
            int referrerPoints = referrer.getLoyaltyPoints() != null ? referrer.getLoyaltyPoints() : 0;
            referrer.setLoyaltyPoints(referrerPoints + referralBonus);
            customerRepository.save(referrer);

            // Mark this referral as rewarded so it never fires again.
            customer.setReferralRewarded(true);
        }

        customerRepository.save(customer);

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

        return saved;
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

        // If the customer had paid online, issue the refund right away.
        boolean refunded = refundIfPaid(order);

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

    /** Puts every ordered unit back into inventory. */
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
        Order saved = orderRepository.save(order);

        Customer customer = order.getCustomer();
        if (newStatus == OrderStatus.SHIPPED) {
            notificationService.shipped(customer, saved.getId());
        } else if (newStatus == OrderStatus.DELIVERED) {
            notificationService.delivered(customer, saved.getId());
        } else if (newStatus == OrderStatus.CANCELLED) {
            boolean refunded = refundIfPaid(order);
            // Stock is only reserved while the order is still PLACED. Once it has
            // shipped the units have left the warehouse, so cancelling must not
            // put them back on the shelf.
            if (previous == OrderStatus.PLACED) {
                releaseStock(order);
            }
            notificationService.orderCancelled(customer, saved.getId());
            if (refunded) {
                notificationService.refundProcessed(customer, saved.getId(), saved.getTotalAmount().toPlainString());
            }
        }

        return saved;
    }
}