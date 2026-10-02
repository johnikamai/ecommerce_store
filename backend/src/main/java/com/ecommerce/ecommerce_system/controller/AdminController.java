package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.*;
import com.ecommerce.ecommerce_system.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private AdminAlertRepository adminAlertRepository;

    @Autowired
    private UserRepository userRepository;

@Autowired
    private ReviewRepository reviewRepository;

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        List<Order> allOrders = orderRepository.findAll();

        BigDecimal totalRevenue = allOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Count from live product stock - Product.stockQuantity is the single
        // source of truth for availability.
        List<Product> allProducts = productRepository.findAll();
        long lowStockCount = allProducts.stream().filter(Product::isLowStock).count();
        long outOfStockCount = allProducts.stream().filter(Product::isOutOfStock).count();

        return Map.of(
                "totalProducts", productRepository.count(),
                "totalCustomers", customerRepository.count(),
                "totalOrders", allOrders.size(),
                "totalRevenue", totalRevenue,
                "lowStockAlerts", lowStockCount,
                "outOfStockAlerts", outOfStockCount,
                "unreadStockAlerts", adminAlertRepository.countUnread()
        );
    }

    @GetMapping("/sales-report")
    public Map<OrderStatus, Long> salesReportByStatus() {
        List<Order> allOrders = orderRepository.findAll();
        return allOrders.stream()
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));
    }

    // ---------- User / Customer management ----------

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            Customer customer = null;
            if (u.getCustomerId() != null) {
                customer = customerRepository.findById(u.getCustomerId()).orElse(null);
            }
            List<Order> orders = u.getCustomerId() != null
                    ? orderRepository.findByCustomerId(u.getCustomerId())
                    : List.of();
            BigDecimal spend = orders.stream()
                    .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int addressCount = u.getCustomerId() != null
                    ? addressRepository.findByCustomerId(u.getCustomerId()).size()
                    : 0;
            Map<String, Object> row = new HashMap<>();
            row.put("id", u.getId());
            row.put("username", u.getUsername());
            row.put("email", u.getEmail());
            row.put("role", u.getRole().name());
            row.put("enabled", u.isEnabled());
            row.put("customerId", u.getCustomerId());
            row.put("customerName", customer == null ? null : customer.getName());
            row.put("phone", customer == null ? null : customer.getPhone());
            row.put("addressCount", addressCount);
            row.put("orderCount", orders.size());
            row.put("totalSpend", spend);
            result.add(row);
        }
        result.sort(Comparator.comparing((Map<String, Object> m) -> m.get("id").toString()));
        return result;
    }

    // GET /api/admin/customers/{customerId}
    // Full customer profile plus saved delivery addresses, so admins can see
    // contact details without opening the customer's own account.
    @GetMapping("/customers/{customerId}")
    public ResponseEntity<?> customerDetail(@PathVariable Long customerId) {
        return customerRepository.findById(customerId)
                .map(customer -> {
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("id", customer.getId());
                    detail.put("name", customer.getName());
                    detail.put("email", customer.getEmail());
                    detail.put("phone", customer.getPhone());
                    detail.put("shippingAddress", customer.getShippingAddress());
                    detail.put("loyaltyPoints", customer.getLoyaltyPoints());
                    detail.put("tier", customer.getTier());
                    detail.put("referralCode", customer.getReferralCode());
                    detail.put("referredBy", customer.getReferredBy() == null
                            ? null : customer.getReferredBy().getName());
                    detail.put("addresses", addressRepository.findByCustomerId(customerId));

                    List<Order> orders = orderRepository.findByCustomerId(customerId);
                    detail.put("orderCount", orders.size());
                    detail.put("totalSpend", orders.stream()
                            .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                            .map(Order::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                    return ResponseEntity.ok(detail);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // PUT /api/admin/users/{id}/enabled  { "enabled": true|false }
    @PutMapping("/users/{id}/enabled")
    public ResponseEntity<?> setUserEnabled(@PathVariable Long id,
                                            @RequestBody Map<String, Boolean> body,
                                            Authentication auth) {
        return userRepository.findById(id)
                .map(user -> {
                    Boolean enabled = body.get("enabled");
                    if (enabled == null) {
                        return ResponseEntity.badRequest().body("enabled flag is required");
                    }
                    if (auth != null && auth.getName().equals(user.getUsername())) {
                        return ResponseEntity.badRequest().body("You cannot block your own account");
                    }
                    user.setEnabled(enabled);
                    return ResponseEntity.ok(userRepository.save(user));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(user -> {
                    if (user.getRole() == Role.ADMIN) {
                        return ResponseEntity.badRequest().body("Cannot delete an admin account");
                    }
                    if (user.getCustomerId() != null) {
                        Long customerId = user.getCustomerId();
                        if (!orderRepository.findByCustomerId(customerId).isEmpty()) {
                            return ResponseEntity.badRequest()
                                    .body("Cannot delete: this customer has orders. Handle or archive them first.");
                        }
                        boolean isReferrer = customerRepository.findAll().stream()
                                .anyMatch(c -> c.getReferredBy() != null
                                        && customerId.equals(c.getReferredBy().getId()));
                        if (isReferrer) {
                            return ResponseEntity.badRequest()
                                    .body("Cannot delete: this customer has referred other customers.");
                        }
                        customerRepository.deleteById(customerId);
                    }
                    userRepository.delete(user);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customers/{customerId}/orders")
    public ResponseEntity<?> customerOrders(@PathVariable Long customerId) {
        if (customerRepository.findById(customerId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(orderRepository.findByCustomerId(customerId));
    }

    // ---------- Reviews moderation ----------

    @GetMapping("/reviews")
    public List<Review> allReviews() {
        return reviewRepository.findAll().stream()
                .sorted(Comparator.comparing(Review::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        if (!reviewRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        reviewRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- Reports & Analytics ----------

    @GetMapping("/reports/sales")
    public Map<String, Object> salesReport() {
        List<Order> all = orderRepository.findAll();
        List<Order> nonCancelled = all.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .toList();
        BigDecimal revenue = nonCancelled.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = nonCancelled.isEmpty()
                ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(nonCancelled.size()), 2, java.math.RoundingMode.HALF_UP);
        long placed = all.stream().filter(o -> o.getStatus() == OrderStatus.PLACED).count();
        long shipped = all.stream().filter(o -> o.getStatus() == OrderStatus.SHIPPED).count();
        long delivered = all.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
        long cancelled = all.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
        return Map.of(
                "totalRevenue", revenue,
                "totalOrders", all.size(),
                "pendingOrders", placed + shipped,
                "completedOrders", delivered,
                "cancelledOrders", cancelled,
                "avgOrderValue", avg
        );
    }

    @GetMapping("/reports/top-products")
    public List<Map<String, Object>> topProducts() {
        Map<Long, Map<String, Object>> agg = new HashMap<>();
        for (Order order : orderRepository.findAll()) {
            if (order.getStatus() == OrderStatus.CANCELLED) {
                continue;
            }
            for (OrderItem item : order.getOrderItems()) {
                Product p = item.getProduct();
                Map<String, Object> row = agg.computeIfAbsent(p.getId(), k -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productId", p.getId());
                    m.put("name", p.getName());
                    m.put("category", p.getCategory());
                    m.put("quantitySold", 0);
                    m.put("revenue", BigDecimal.ZERO);
                    return m;
                });
                int qty = (Integer) row.get("quantitySold") + item.getQuantity();
                row.put("quantitySold", qty);
                BigDecimal rev = ((BigDecimal) row.get("revenue"))
                        .add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                row.put("revenue", rev);
            }
        }
        return agg.values().stream()
                .sorted(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("quantitySold")).reversed())
                .limit(10)
                .toList();
    }

    @GetMapping("/reports/customer-activity")
    public List<Map<String, Object>> customerActivity() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Customer c : customerRepository.findAll()) {
            List<Order> orders = orderRepository.findByCustomerId(c.getId());
            BigDecimal spend = orders.stream()
                    .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(Map.of(
                    "customerId", c.getId(),
                    "name", c.getName(),
                    "email", c.getEmail() == null ? "" : c.getEmail(),
                    "orderCount", orders.size(),
                    "totalSpend", spend,
                    "tier", c.getTier() == null ? "BRONZE" : c.getTier()
            ));
        }
        result.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("totalSpend")).reversed());
        return result.stream().limit(10).toList();
    }
}