package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.repository.OrderItemRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import com.ecommerce.ecommerce_system.repository.ReviewRepository;
import com.ecommerce.ecommerce_system.service.SearchService;
import com.ecommerce.ecommerce_system.service.StockAlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockAlertService stockAlertService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private SearchService searchService;

    @GetMapping
    public List<Product> getAll() {
        return productRepository.findAll();
    }

    /**
     * Storefront catalog: every product enriched with aggregate rating data
     * (averageRating, reviewCount) and unitsSold, so the frontend can sort by
     * popularity and rating without paging the backend.
     */
    @GetMapping("/catalog")
    public List<Map<String, Object>> catalog() {
        return enrich(productRepository.findAll());
    }

    /**
     * Natural-language search.
     *
     * Deliberately unauthenticated and un-paged: the storefront searches before
     * login, and 600-odd products is small enough that paging here would add
     * latency and break the "did you mean" hint, which needs the full vocabulary.
     *
     * Responds with the corrected query and any suggestions so the UI can be
     * honest that it understood a typo rather than silently returning odd results.
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "limit", defaultValue = "0") int limit) {

        SearchService.Result result = searchService.search(q, productRepository.findAll());
        List<Map<String, Object>> enriched = enrich(
                result.hits().stream().map(SearchService.Hit::product).toList());

        // Re-attach the relevance score to the enriched rows.
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < enriched.size(); i++) {
            Map<String, Object> row = new HashMap<>(enriched.get(i));
            row.put("relevance", Math.round(result.hits().get(i).score() * 1000) / 1000.0);
            rows.add(row);
        }

        // limit > 0 is for autocomplete, which wants a handful of names not cards.
        if (limit > 0 && rows.size() > limit) rows = rows.subList(0, limit);

        Map<String, Object> body = new HashMap<>();
        body.put("query", q == null ? "" : q.trim());
        body.put("correctedQuery", result.correctedQuery());
        body.put("suggestions", result.suggestions());
        body.put("total", result.total());
        // A budget in the phrasing ("earbuds under 3000") comes back separately so
        // the storefront can apply it as a filter rather than as a search term.
        body.put("minPrice", result.minPrice());
        body.put("maxPrice", result.maxPrice());
        body.put("results", rows);
        return ResponseEntity.ok(body);
    }

    /**
     * Attaches rating aggregates and units sold to each product.
     *
     * Shared by /catalog and /search so a product looks identical wherever the
     * storefront finds it, which matters once the same product can appear in the
     * grid, a search result and the compare table at once.
     */
    private List<Map<String, Object>> enrich(List<Product> products) {
        Map<Long, double[]> reviewStats = new HashMap<>(); // pid -> [count, avg]
        for (Object[] row : reviewRepository.aggregateByProduct()) {
            Long pid = ((Number) row[0]).longValue();
            long cnt = ((Number) row[1]).longValue();
            double avg = row[2] == null ? 0.0 : ((Number) row[2]).doubleValue();
            reviewStats.put(pid, new double[]{cnt, Math.round(avg * 10.0) / 10.0});
        }

        Map<Long, Long> unitsSold = new HashMap<>();
        for (Object[] row : orderItemRepository.unitsSoldByProduct()) {
            Long pid = ((Number) row[0]).longValue();
            unitsSold.put(pid, ((Number) row[1]).longValue());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Product p : products) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", p.getId());
            entry.put("name", p.getName());
            entry.put("description", p.getDescription());
            entry.put("category", p.getCategory());
            entry.put("price", p.getPrice());
            entry.put("stockQuantity", p.getStockQuantity());
            entry.put("sustainabilityScore", p.getSustainabilityScore());
            entry.put("sustainabilityLabel", p.getSustainabilityLabel());
            entry.put("imageUrl", p.getImageUrl());
            double[] stats = reviewStats.getOrDefault(p.getId(), new double[]{0, 0.0});
            entry.put("reviewCount", (long) stats[0]);
            entry.put("averageRating", stats[1]);
            entry.put("unitsSold", unitsSold.getOrDefault(p.getId(), 0L));
            result.add(entry);
        }
        return result;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Product product) {
        if (product.getSustainabilityScore() != null &&
                (product.getSustainabilityScore() < 0 || product.getSustainabilityScore() > 100)) {
            return ResponseEntity.badRequest().body("Sustainability score must be between 0 and 100");
        }
        Product saved = productRepository.save(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Product updated) {
        if (updated.getSustainabilityScore() != null &&
                (updated.getSustainabilityScore() < 0 || updated.getSustainabilityScore() > 100)) {
            return ResponseEntity.badRequest().body("Sustainability score must be between 0 and 100");
        }
        return productRepository.findById(id)
                .map(existing -> {
                    // Track the old stock level so we can detect a restock transition.
                    Integer oldStock = existing.getStockQuantity();

                    existing.setName(updated.getName());
                    existing.setDescription(updated.getDescription());
                    existing.setCategory(updated.getCategory());
                    existing.setPrice(updated.getPrice());
                    existing.setStockQuantity(updated.getStockQuantity());
                    existing.setSustainabilityScore(updated.getSustainabilityScore());
                    existing.setImageUrl(updated.getImageUrl());
                    if (updated.getReorderLevel() != null && updated.getReorderLevel() >= 0) {
                        existing.setReorderLevel(updated.getReorderLevel());
                    }

                    Product saved = productRepository.save(existing);

                    // React to the stock move: warn staff and customers as needed.
                    stockAlertService.onStockChanged(saved, oldStock);

                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!productRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        productRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}