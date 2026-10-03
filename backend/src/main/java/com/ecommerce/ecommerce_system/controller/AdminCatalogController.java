package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.config.ProductSeeder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
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

    @Autowired
    private ProductSeeder productSeeder;

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
}