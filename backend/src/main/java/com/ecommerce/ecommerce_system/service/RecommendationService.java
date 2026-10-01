package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Order;
import com.ecommerce.ecommerce_system.model.OrderItem;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.repository.OrderRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

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
                productById.putIfAbsent(p.getId(), p);
                coOccurrence.merge(p.getId(), 1, Integer::sum);
            }
        }

        // Sort by co-occurrence count descending, then take top N.
        return coOccurrence.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> rec = new LinkedHashMap<>();
                    Product p = productById.get(entry.getKey());
                    rec.put("id", p.getId());
                    rec.put("name", p.getName());
                    rec.put("category", p.getCategory());
                    rec.put("price", p.getPrice());
                    rec.put("stockQuantity", p.getStockQuantity());
                    rec.put("timesBoughtTogether", entry.getValue());
                    return rec;
                })
                .collect(Collectors.toList());
    }
}
