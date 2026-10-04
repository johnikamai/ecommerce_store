package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.config.ProductSeeder;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog maintenance for store staff.
 *
 * The catalog is normally seeded at boot, but that runs before the app serves
 * traffic and inside a try/catch, so a seeding problem is invisible from the UI.
 * This exposes the same idempotent routine on demand, plus a read-only status
 * endpoint so the current catalog size can be checked without counting rows.
 *
 * Under /api/admin/**, so it already requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/catalog")
public class AdminCatalogController {

    private static final int EXPECTED_PRODUCTS = 606;

    /**
     * Products per saveAll batch. The whole catalog is written in one request, so
     * without chunking a single persistence context would hold all 606 entities
     * and dirty-check them on flush.
     */
    private static final int IMAGE_BATCH = 100;

    @Autowired
    private ProductSeeder productSeeder;

    @Autowired
    private ProductRepository productRepository;

    /** Read-only: is the catalog fully seeded? Safe to call on every page load. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        int total = productSeeder.currentProductCount();
        Map<String, Object> body = new HashMap<>();
        body.put("totalProducts", total);
        body.put("expectedProducts", EXPECTED_PRODUCTS);
        body.put("fullySeeded", total >= EXPECTED_PRODUCTS);
        body.put("missing", Math.max(0, EXPECTED_PRODUCTS - total));
        return body;
    }

    /**
     * Runs the idempotent seeder immediately. Safe to call repeatedly - existing
     * products are reused, never duplicated or deleted.
     */
    @PostMapping("/reseed")
    public ResponseEntity<?> reseed() {
        ProductSeeder.CatalogReport report = productSeeder.ensureCatalog();
        Map<String, Object> body = new HashMap<>();
        body.put("totalProducts", report.totalProducts());
        body.put("baseCreated", report.baseCreated());
        body.put("baseBackfilled", report.baseBackfilled());
        body.put("legacyImagesCleared", report.legacyImagesCleared());
        body.put("variantsCreated", report.variantsCreated());
        body.put("expectedProducts", EXPECTED_PRODUCTS);
        body.put("fullySeeded", report.totalProducts() >= EXPECTED_PRODUCTS);
        return ResponseEntity.ok(body);
    }

    /**
     * Applies a productId -> image URL map across the catalog in one request.
     *
     * The storefront hosts its product photos as static files under
     * /products/*.webp, so applying them means updating 606 rows. Doing that with
     * the existing single-product update endpoint would be 606 authenticated
     * round trips that can individually fail and leave the catalog half
     * updated. One request either mostly succeeds or reports exactly what it
     * could not match, which makes the result verifiable.
     *
     * Ids with no matching product are reported rather than silently dropped, so a
     * stale map is visible instead of quietly leaving products on placeholder art.
     */
    @PostMapping("/images")
    @Transactional
    public ResponseEntity<?> applyImages(@RequestBody Map<String, String> images) {
        if (images == null || images.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "no images supplied"));
        }

        List<Long> ids = new ArrayList<>(images.size());
        for (String key : images.keySet()) {
            try {
                ids.add(Long.valueOf(key.trim()));
            } catch (NumberFormatException ignored) {
                // Not a product id; counted below as unmatched.
            }
        }

        int updated = 0;
        int unchanged = 0;
        List<Product> batch = new ArrayList<>(IMAGE_BATCH);
        for (Long id : ids) {
            Product product = productRepository.findById(id).orElse(null);
            if (product == null) {
                continue;
            }
            String url = images.get(String.valueOf(id));
            if (url == null || url.isBlank()) {
                continue;
            }
            if (url.equals(product.getImageUrl())) {
                unchanged++;
                continue;
            }
            product.setImageUrl(url.trim());
            batch.add(product);
            if (batch.size() >= IMAGE_BATCH) {
                productRepository.saveAll(batch);
                updated += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            productRepository.saveAll(batch);
            updated += batch.size();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("supplied", images.size());
        body.put("updated", updated);
        body.put("unchanged", unchanged);
        body.put("unmatched", images.size() - updated - unchanged);
        body.put("totalProducts", productRepository.count());
        return ResponseEntity.ok(body);
    }
}