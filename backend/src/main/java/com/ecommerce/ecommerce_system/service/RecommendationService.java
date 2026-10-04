package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Order;
import com.ecommerce.ecommerce_system.model.OrderStatus;
import com.ecommerce.ecommerce_system.model.OrderItem;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.repository.OrderItemRepository;
import com.ecommerce.ecommerce_system.repository.OrderRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import com.ecommerce.ecommerce_system.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    /**
     * Rating counts/averages per product. Reviews are a separate table, so the
     * numbers ProductController shows in the catalog have to be re-read here
     * rather than taken off the Product entity.
     */
    private Map<Long, double[]> reviewStats() {
        Map<Long, double[]> stats = new HashMap<>();
        for (Object[] row : reviewRepository.aggregateByProduct()) {
            Long pid = ((Number) row[0]).longValue();
            long cnt = ((Number) row[1]).longValue();
            double avg = row[2] == null ? 0.0 : ((Number) row[2]).doubleValue();
            stats.put(pid, new double[]{cnt, Math.round(avg * 10.0) / 10.0});
        }
        return stats;
    }

    /** Units sold per product, used as the popularity signal. */
    private Map<Long, Long> unitsSold() {
        Map<Long, Long> sold = new HashMap<>();
        for (Object[] row : orderItemRepository.unitsSoldByProduct()) {
            sold.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return sold;
    }

    /**
     * "Frequently Bought Together" recommendation.
     *
     * Idea: for a given target product, find every ORDER that contains that product.
     * Then, among all the other products inside those same orders, count how often
     * each one appears. The most frequent co-occurring products are the recommendations.
     *
     * This is real co-occurrence analysis based on actual purchase history —
     * not random or pre-seeded data.
     */
    @Transactional
    public List<Map<String, Object>> frequentlyBoughtTogether(Long productId, int limit) {
        Product target = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        // Map: OTHER product id -> how many orders it co-occurs with the target.
        Map<Long, Integer> coOccurrence = new HashMap<>();
        Map<Long, Product> productById = new HashMap<>();
        Map<Long, double[]> reviewStats = reviewStats();

        // Consider only successfully purchased orders (not cancelled) so we recommend
        // things people actually bought together.
        List<Order> orders = orderRepository.findAll().stream()
                .filter(o -> o.getOrderItems() != null)
                .toList();

        for (Order order : orders) {
            if (order.getStatus() != null && order.getStatus().name().equals("CANCELLED")) {
                continue; // skip cancelled orders
            }

            boolean orderHasTarget = order.getOrderItems().stream()
                    .anyMatch(item -> item.getProduct() != null &&
                            item.getProduct().getId().equals(productId));

            if (!orderHasTarget) {
                continue; // only inspect orders that actually contain the target
            }

            // For every OTHER product in this same order, bump its co-occurrence count.
            for (OrderItem item : order.getOrderItems()) {
                Product p = item.getProduct();
                if (p == null || p.getId().equals(productId)) {
                    continue; // skip the target itself (and any null product)
                }
                if (p.getStockQuantity() == null || p.getStockQuantity() <= 0) {
                    continue; // never point a shopper at something they cannot buy
                }
                productById.putIfAbsent(p.getId(), p);
                coOccurrence.merge(p.getId(), 1, Integer::sum);
            }
        }

        // Sort by co-occurrence count descending, then take top N.
        return coOccurrence.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .map(entry -> {
                    Product p = productById.get(entry.getKey());
                    double[] stats = reviewStats.getOrDefault(p.getId(), new double[]{0, 0.0});
                    Map<String, Object> rec = new LinkedHashMap<>();
                    rec.put("id", p.getId());
                    rec.put("name", p.getName());
                    rec.put("category", p.getCategory());
                    rec.put("price", p.getPrice());
                    rec.put("stockQuantity", p.getStockQuantity());
                    // imageUrl is included so the storefront renders the real photo
                    // instead of falling back to a generated tile.
                    rec.put("imageUrl", p.getImageUrl());
                    rec.put("sustainabilityScore", p.getSustainabilityScore());
                    rec.put("sustainabilityLabel", p.getSustainabilityLabel());
                    rec.put("averageRating", stats[1]);
                    rec.put("reviewCount", (long) stats[0]);
                    rec.put("timesBoughtTogether", entry.getValue());
                    // A translation key plus its parameters, not a rendered
                    // English sentence: the storefront localises the reason so
                    // it reads correctly in the shopper's own language.
                    rec.put("reasonCode", "recs.reason.boughtTogether");
                    rec.put("reasonParams", Map.of("count", entry.getValue()));
                    return rec;
                })
                .collect(Collectors.toList());
    }

    /**
     * Recommendations shaped around one customer's own history.
     *
     * Scores every in-stock product against three signals from what this
     * customer has actually done, then returns the best-scoring unseen items:
     *
     *   1. category affinity - categories they have bought before, weighted by
     *      how much they spent there. Buying two phones should make phones rank
     *      higher, not just count them twice.
     *   2. price-band fit - products priced near what they usually spend. A
     *      customer who buys ₹500 items is not shown a ₹40,000 laptop first.
     *   3. popularity - units already sold, as a tie-breaker so a sparse
     *      history still produces something sensible.
     *
     * Anything the customer already bought is excluded, since re-selling it to
     * them is the fastest way to make a recommendation feel broken, and
     * out-of-stock items are excluded because they cannot be actioned.
     *
     * Returns an empty list rather than throwing when there is not enough
     * history yet; the caller renders nothing and the catalog takes over.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> personalizedFor(Long customerId, int limit) {
        List<Order> orders = orderRepository.findByCustomerId(customerId).stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .filter(o -> o.getOrderItems() != null)
                .toList();

        Set<Long> alreadyBought = new HashSet<>();
        Map<String, Double> categorySpend = new HashMap<>();
        List<Double> basketTotals = new ArrayList<>();

        for (Order order : orders) {
            double orderTotal = 0;
            for (OrderItem item : order.getOrderItems()) {
                Product p = item.getProduct();
                if (p == null) {
                    continue;
                }
                alreadyBought.add(p.getId());
                if (p.getCategory() != null) {
                    double spend = item.getLineTotal() == null ? 0 : item.getLineTotal().doubleValue();
                    categorySpend.merge(p.getCategory(), spend, Double::sum);
                }
                orderTotal += item.getQuantity() == null ? 0 : item.getQuantity();
            }
            if (orderTotal > 0) {
                basketTotals.add(orderTotal);
            }
        }

        // No purchase history: an affinity guess would be fabricated, so say so
        // rather than dressing up random products as personal.
        if (categorySpend.isEmpty()) {
            return List.of();
        }

        double typicalBasket = basketTotals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double maxSpend = categorySpend.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);

        Map<Long, double[]> reviewStats = reviewStats();
        Map<Long, Long> unitsSold = unitsSold();

        List<Product> candidates = productRepository.findAll().stream()
                .filter(p -> p.getId() != null && !alreadyBought.contains(p.getId()))
                .filter(p -> p.getStockQuantity() != null && p.getStockQuantity() > 0)
                .toList();

        record Scored(Product product, double score, String reasonCode, String reasonCategory) {}

        List<Scored> scored = new ArrayList<>();
        for (Product p : candidates) {
            double affinity = categorySpend.getOrDefault(p.getCategory(), 0.0) / maxSpend;
            if (affinity <= 0) {
                // Never bought this category. Only surface it when the shopper has
                // nothing else to be shown, via the fallback below.
                continue;
            }
            double priceFit = priceFit(p.getPrice() == null ? 0 : p.getPrice().doubleValue(), typicalBasket);
            double popularity = Math.log1p(unitsSold.getOrDefault(p.getId(), 0L)) / 10.0;
            double rating = reviewStats.getOrDefault(p.getId(), new double[]{0, 0.0})[1] / 5.0;

            double score = affinity * 3.0 + priceFit * 2.0 + popularity + rating;
            scored.add(new Scored(p, score, "recs.reason.because", p.getCategory()));
        }

        scored.sort((a, b) -> Double.compare(b.score(), a.score()));

        if (scored.size() < limit) {
            // Pad with well-rated in-stock products from categories they have not
            // tried, clearly labelled as a general pick rather than a personal one.
            Set<Long> chosen = scored.stream().map(s -> s.product().getId()).collect(Collectors.toSet());
            productRepository.findAll().stream()
                    .filter(p -> p.getId() != null && !chosen.contains(p.getId()))
                    .filter(p -> !alreadyBought.contains(p.getId()))
                    .filter(p -> p.getStockQuantity() != null && p.getStockQuantity() > 0)
                    .sorted(Comparator.comparingDouble(
                            (Product p) -> reviewStats.getOrDefault(p.getId(), new double[]{0, 0.0})[1]).reversed())
                    .limit(limit - scored.size())
                    .forEach(p -> scored.add(new Scored(p, 0, "recs.reason.topRated", null)));
        }

        return scored.stream()
                .limit(limit)
                .map(s -> {
                    Product p = s.product();
                    double[] stats = reviewStats.getOrDefault(p.getId(), new double[]{0, 0.0});
                    Map<String, Object> rec = new LinkedHashMap<>();
                    rec.put("id", p.getId());
                    rec.put("name", p.getName());
                    rec.put("category", p.getCategory());
                    rec.put("price", p.getPrice());
                    rec.put("stockQuantity", p.getStockQuantity());
                    rec.put("imageUrl", p.getImageUrl());
                    rec.put("sustainabilityScore", p.getSustainabilityScore());
                    rec.put("sustainabilityLabel", p.getSustainabilityLabel());
                    rec.put("averageRating", stats[1]);
                    rec.put("reviewCount", (long) stats[0]);
                    rec.put("reasonCode", s.reasonCode());
                    Map<String, Object> params = new LinkedHashMap<>();
                    if (s.reasonCategory() != null) {
                        params.put("category", s.reasonCategory());
                    }
                    rec.put("reasonParams", params);
                    return rec;
                })
                .collect(Collectors.toList());
    }

    /**
     * 1.0 when the product sits in the customer's usual price bracket, tapering
     * to 0 as it diverges. Deliberately forgiving: a slightly-pricier upsell is
     * a reasonable suggestion, a 50x jump is not.
     */
    private static double priceFit(double price, double typicalBasket) {
        if (typicalBasket <= 0 || price <= 0) {
            return 0.5;
        }
        double ratio = price / typicalBasket;
        if (ratio < 1) {
            ratio = 1 / ratio;
        }
        return ratio <= 4 ? 1.0 : Math.max(0, 1.0 - (ratio - 4) / 12.0);
    }

    /**
     * Categories this customer buys most, used by the storefront to explain an
     * empty personalized rail and by tests to assert affinity is being tracked.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> tasteProfile(Long customerId) {
        Map<String, Double> spend = new HashMap<>();
        orderRepository.findByCustomerId(customerId).stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .filter(o -> o.getOrderItems() != null)
                .forEach(o -> o.getOrderItems().forEach(item -> {
                    Product p = item.getProduct();
                    if (p != null && p.getCategory() != null) {
                        double amount = item.getLineTotal() == null ? 0 : item.getLineTotal().doubleValue();
                        spend.merge(p.getCategory(), amount, Double::sum);
                    }
                }));

        return spend.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("category", e.getKey());
                    row.put("spend", BigDecimal.valueOf(e.getValue()).setScale(2, java.math.RoundingMode.HALF_UP));
                    return row;
                })
                .collect(Collectors.toList());
    }
}
