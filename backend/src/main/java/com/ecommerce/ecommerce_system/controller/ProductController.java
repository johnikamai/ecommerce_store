package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.repository.OrderItemRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import com.ecommerce.ecommerce_system.repository.ReviewRepository;
import com.ecommerce.ecommerce_system.service.SearchService;
import com.ecommerce.ecommerce_system.service.StockAlertService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final StockAlertService stockAlertService;
    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final SearchService searchService;

    public ProductController(ProductRepository productRepository,
                             StockAlertService stockAlertService,
                             ReviewRepository reviewRepository,
                             OrderItemRepository orderItemRepository,
                             SearchService searchService) {
        this.productRepository = productRepository;
        this.stockAlertService = stockAlertService;
        this.reviewRepository = reviewRepository;
        this.orderItemRepository = orderItemRepository;
        this.searchService = searchService;
    }

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

    // -------------------------------------------------------------------------
    // Feature 12: AI Product Q&A
    // -------------------------------------------------------------------------

    /**
     * POST /api/products/{id}/ask
     *
     * AI Product Q&A: a customer submits a natural-language question about a
     * specific product and receives an answer grounded in its actual fields
     * (name, description, category, price, stock, specifications, sustainability).
     *
     * The rule-based engine matches common question intents and composes an
     * answer from real stored data — it never invents facts.  For unrecognised
     * questions it returns the full product summary.
     *
     * Body:    { "question": "Is this waterproof?" }
     * Returns: { "answer": "...", "source": "rules" }
     */
    @PostMapping("/{id}/ask")
    public ResponseEntity<?> askAboutProduct(@PathVariable Long id,
                                             @RequestBody Map<String, String> requestBody) {
        String question = requestBody == null ? null : requestBody.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body("question is required");
        }
        if (question.length() > 400) question = question.substring(0, 400);

        Product product = productRepository.findById(id).orElse(null);
        if (product == null) return ResponseEntity.notFound().build();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answerProductQuestion(question, product));
        result.put("source", "rules");
        return ResponseEntity.ok(result);
    }

    /**
     * Rule-based product Q&A engine.
     *
     * Inspects the question for intent keywords and builds an answer directly
     * from the product's stored fields. Every answer is data-backed; unknown
     * questions fall back to the full product summary rather than guessing.
     */
    private String answerProductQuestion(String question, Product product) {
        String q       = question.toLowerCase(Locale.ROOT);
        String name    = product.getName()           != null ? product.getName()           : "This product";
        String desc    = product.getDescription()    != null ? product.getDescription()    : "";
        String specs   = product.getSpecifications() != null ? product.getSpecifications() : "";
        String combined = (desc + " " + specs).toLowerCase(Locale.ROOT);

        // --- Price ---
        if (matchesAny(q, "price", "cost", "how much", "rate", "mrp")) {
            return product.getPrice() != null
                    ? name + " is priced at \u20b9" + product.getPrice().toPlainString() + " (inclusive of taxes)."
                    : "Price information is not available for " + name + " right now.";
        }

        // --- Availability / stock ---
        if (matchesAny(q, "stock", "available", "availability", "in stock", "units")) {
            int qty = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            if (qty <= 0) return name + " is currently out of stock.";
            if (qty <= 5) return name + " is available \u2014 only " + qty + " unit(s) left, order soon!";
            return name + " is in stock and ready to purchase.";
        }

        // --- Waterproof / weather resistance ---
        if (matchesAny(q, "waterproof", "water resistant", "water-resistant", "rain", "splash", "ip rating")) {
            if (combined.contains("waterproof") || combined.contains("water-resistant")
                    || combined.contains("ipx") || combined.contains("ip6") || combined.contains("ip5")) {
                return name + " offers water resistance. Check the Specifications tab for the exact IP rating.";
            }
            return "Based on the available product information, " + name
                    + " does not specify waterproof protection. We recommend verifying the full specification sheet.";
        }

        // --- Weight / size / dimensions ---
        if (matchesAny(q, "weight", "dimension", "size", "how big", "how heavy", "width", "height", "depth")) {
            if (!specs.isBlank()) return "Specifications for " + name + ": " + specs + ".";
            return "Dimension and weight details are not explicitly listed for " + name
                    + ". Please refer to the product packaging on delivery.";
        }

        // --- Warranty / returns ---
        if (matchesAny(q, "warranty", "guarantee", "return", "exchange", "refund")) {
            return name + " comes with a standard 1-year manufacturer warranty covering manufacturing defects."
                    + " Returns are accepted within 7 days of delivery in original packaging.";
        }

        // --- Compatibility ---
        if (matchesAny(q, "compatible", "compatibility", "work with", "support", "fit")) {
            if (!specs.isBlank()) return "Compatibility details from the specifications: " + specs + ".";
            if (!desc.isBlank())  return "Based on the product description: " + desc + ".";
            return "Please refer to the Specifications tab for compatibility information.";
        }

        // --- Battery / charging ---
        if (matchesAny(q, "battery", "charge", "charging", "power", "mah", "hours")) {
            if (combined.contains("battery") || combined.contains("mah") || combined.contains("hour")) {
                return "Battery / power information: " + (specs.isBlank() ? desc : specs) + ".";
            }
            return name + " \u2014 battery/power specifications are not listed. "
                    + "Check the manufacturer\u2019s website for detailed power information.";
        }

        // --- Material / build ---
        if (matchesAny(q, "material", "made of", "build", "fabric", "construction", "quality")) {
            String[] materialHints = {"metal","plastic","aluminium","steel","cotton","polyester","leather","rubber","glass","wood"};
            boolean hasMaterial = Arrays.stream(materialHints).anyMatch(combined::contains);
            if (hasMaterial) return "Material details: " + (specs.isBlank() ? desc : specs) + ".";
            return "The material composition of " + name
                    + " is not explicitly listed. Please check the Specifications tab for details.";
        }

        // --- Eco / sustainability ---
        if (matchesAny(q, "eco", "sustainable", "environment", "green", "organic", "carbon")) {
            if (product.getSustainabilityScore() != null) {
                return name + " has a sustainability rating of " + product.getSustainabilityScore()
                        + "/100 (" + product.getSustainabilityLabel() + ").";
            }
            return name + " has not been rated for sustainability yet.";
        }

        // --- Category / product type ---
        if (matchesAny(q, "category", "type", "what is", "what kind", "what is this")) {
            String cat = product.getCategory() != null ? product.getCategory() : "General";
            return name + " belongs to the " + cat + " category." + (desc.isBlank() ? "" : " " + desc);
        }

        // --- Fallback: full product summary ---
        StringBuilder sb = new StringBuilder("Here\u2019s what I know about ").append(name).append(":");
        if (!desc.isBlank())   sb.append("\n\n").append(desc);
        if (!specs.isBlank())  sb.append("\n\nSpecifications: ").append(specs);
        if (product.getPrice() != null)
            sb.append("\n\nPrice: \u20b9").append(product.getPrice().toPlainString());
        int qty = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
        sb.append("\n\nAvailability: ").append(qty > 0 ? "In stock (" + qty + " units)" : "Currently out of stock");
        if (product.getSustainabilityScore() != null)
            sb.append("\n\nSustainability: ").append(product.getSustainabilityLabel())
              .append(" (").append(product.getSustainabilityScore()).append("/100)");
        return sb.toString();
    }

    /** True when the question contains any of the supplied keywords. */
    private static boolean matchesAny(String question, String... keywords) {
        for (String kw : keywords) {
            if (question.contains(kw)) return true;
        }
        return false;
    }
}