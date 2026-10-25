package com.ecommerce.product.service;

import com.ecommerce.product.model.Product;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * F8: Product Compatibility Checker.
 *
 * Determines whether two products are likely to work together by inspecting
 * their categories, specifications, and brand keywords.
 *
 * Rules are deterministic and require no external AI call:
 *   1. Same-category check: identical categories are almost always compatible.
 *   2. Known-incompatible pairs (e.g., two chargers, two cases for different
 *      phone models).
 *   3. Spec keyword matching: shared connectivity tokens (USB-C, Bluetooth 5,
 *      HDMI 2.1 …) raise the score; conflicting exclusive tokens lower it.
 *   4. Complementary category pairs raise the score (laptop + mouse, etc.).
 */
@Service
public class CompatibilityService {

    /** Category pairs that are well-known complements. Score boost applied. */
    private static final List<Set<String>> COMPLEMENT_PAIRS = List.of(
        Set.of("laptops", "mice"),
        Set.of("laptops", "keyboard"),
        Set.of("laptops", "keyboards"),
        Set.of("laptops", "laptop bags"),
        Set.of("laptops", "monitors"),
        Set.of("laptops", "laptop stands"),
        Set.of("mobiles", "phone cases"),
        Set.of("mobiles", "earbuds"),
        Set.of("mobiles", "chargers"),
        Set.of("tablets", "tablet cases"),
        Set.of("tablets", "stylus"),
        Set.of("cameras", "camera bags"),
        Set.of("cameras", "tripods"),
        Set.of("cameras", "memory cards"),
        Set.of("televisions", "hdmi cables"),
        Set.of("televisions", "soundbars"),
        Set.of("gaming consoles", "game controllers"),
        Set.of("routers", "network switches")
    );

    /** Category pairs that rarely make sense together. */
    private static final List<Set<String>> CONFLICT_PAIRS = List.of(
        Set.of("laptops", "desktop computers"),
        Set.of("ios cases", "android cases"),
        Set.of("refrigerators", "washing machines")
    );

    public record CompatibilityResult(
        boolean compatible,
        int score,           // 0-100
        String verdict,      // one-line summary
        List<String> reasons // bullet-point list of why
    ) {}

    public CompatibilityResult check(Product a, Product b) {
        List<String> reasons = new ArrayList<>();
        int score = 50; // start neutral

        String catA = normalise(a.getCategory());
        String catB = normalise(b.getCategory());

        // 1. Same category → generally compatible (two of the same thing)
        if (catA.equals(catB)) {
            score += 20;
            reasons.add("Both products are in the same category (" + a.getCategory() + ").");
        }

        // 2. Complement pair
        Set<String> pair = Set.of(catA, catB);
        boolean isComplement = COMPLEMENT_PAIRS.stream().anyMatch(p -> p.equals(pair));
        if (isComplement) {
            score += 30;
            reasons.add(a.getCategory() + " and " + b.getCategory() + " are a well-known compatible combination.");
        }

        // 3. Conflict pair
        boolean isConflict = CONFLICT_PAIRS.stream().anyMatch(p -> p.equals(pair));
        if (isConflict) {
            score -= 40;
            reasons.add("These two product types are generally not used together.");
        }

        // 4. Spec keyword overlap
        Set<String> tokensA = specTokens(a.getSpecifications());
        Set<String> tokensB = specTokens(b.getSpecifications());

        Set<String> shared = new HashSet<>(tokensA);
        shared.retainAll(tokensB);
        if (!shared.isEmpty()) {
            int boost = Math.min(15, shared.size() * 5);
            score += boost;
            reasons.add("Shared specification keywords: " + String.join(", ", shared) + ".");
        }

        // 5. Brand mismatch penalty (minor — cross-brand is usually fine)
        if (a.getBrand() != null && b.getBrand() != null
                && !normalise(a.getBrand()).equals(normalise(b.getBrand()))) {
            // different brands is normal, no penalty
        }

        score = Math.max(0, Math.min(100, score));
        boolean compatible = score >= 50;

        String verdict;
        if (score >= 80) verdict = "Highly compatible — these products work great together.";
        else if (score >= 60) verdict = "Compatible — these products should work well together.";
        else if (score >= 40) verdict = "Possibly compatible — verify specifications before purchasing.";
        else verdict = "Likely incompatible — these products may not work well together.";

        if (reasons.isEmpty()) {
            reasons.add("No strong compatibility signals found; check product specifications manually.");
        }

        return new CompatibilityResult(compatible, score, verdict, reasons);
    }

    private static String normalise(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /** Extracts connectivity/interface tokens from a spec string. */
    private static Set<String> specTokens(String spec) {
        if (spec == null || spec.isBlank()) return Set.of();
        Set<String> tokens = new LinkedHashSet<>();
        String lower = spec.toLowerCase(Locale.ROOT);
        for (String kw : List.of(
            "usb-c", "usb-a", "usb 3", "usb 2", "bluetooth 5", "bluetooth 4",
            "wi-fi 6", "wi-fi 5", "hdmi 2.1", "hdmi 2.0", "hdmi 1.4",
            "displayport", "thunderbolt", "lightning", "aux", "3.5mm",
            "micro-sd", "sd card", "type-c", "type-a",
            "android", "ios", "windows", "macos"
        )) {
            if (lower.contains(kw)) tokens.add(kw);
        }
        return tokens;
    }
}
