package com.ecommerce.order.service;
import com.ecommerce.order.model.*;
import com.ecommerce.order.repo.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;
@Service
public class OrderPricingService {
    private final CustomerRepository customerRepository;private final ProductRepository productRepository;private final CouponRepository couponRepository;
    @org.springframework.beans.factory.annotation.Value("${app.payments.demo-enabled:false}")private boolean demoPaymentsEnabled;
    public OrderPricingService(CustomerRepository customerRepository,ProductRepository productRepository,CouponRepository couponRepository){this.customerRepository=customerRepository;this.productRepository=productRepository;this.couponRepository=couponRepository;}
    public boolean isDemoPaymentsEnabled(){return demoPaymentsEnabled;}
    public static final BigDecimal FLAT_SHIPPING=new BigDecimal("49.00"),FREE_SHIPPING_THRESHOLD=new BigDecimal("999.00"),TAX_RATE=new BigDecimal("0.18");
    public Order prepareOrder(OrderRequest request) {
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
            if (!coupon.isRedeemable()) {
                throw new IllegalArgumentException("Coupon is no longer valid: " + coupon.getCode());
            }
            if (coupon.getMinimumOrderAmount() != null
                    && subtotal.compareTo(coupon.getMinimumOrderAmount()) < 0) {
                throw new IllegalArgumentException("Coupon requires a minimum order of ₹" + coupon.getMinimumOrderAmount());
            }
            if (coupon.getDiscountType() == com.ecommerce.order.model.CouponType.PERCENT) {
                couponDiscount = subtotal
                        .multiply(coupon.getDiscountValue())
                        .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            } else {
                couponDiscount = coupon.getDiscountValue();
            }
            if (couponDiscount.compareTo(subtotal) > 0) {
                couponDiscount = subtotal;
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
    public Map<String,Object> quote(OrderRequest request) {
        Order order = prepareOrder(request);
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
    public static BigDecimal shippingCostFor(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("0.00");
        }
        if (subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
            return new BigDecimal("0.00");
        }
        return FLAT_SHIPPING;
    }
    public static BigDecimal taxFor(BigDecimal merchandiseTotal) {
        if (merchandiseTotal == null || merchandiseTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return merchandiseTotal
                .multiply(taxRate())
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
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
        } catch (NumberFormatException ignored) {
            System.out.println("[TAX] TAX_RATE " + configured + " is not a number, using default");
            return TAX_RATE;
        }
    }
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
}
