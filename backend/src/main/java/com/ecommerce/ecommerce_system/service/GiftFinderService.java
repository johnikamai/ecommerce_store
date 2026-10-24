package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ranks catalogue products as gift suggestions.
 *
 * The scorer blends what the shopper asked for (occasion, age, interests,
 * budget) with what makes a product a good present (gift packaging, rating,
 * popularity) and then enforces category variety so the result is not nine
 * variations of the same mug. It is intentionally deterministic: the same
 * answers always give the same shortlist, which makes it testable and
 * explainable — every pick carries the reasons it was chosen.
 */
@Service
public class GiftFinderService {

    private static final int MAX_RESULTS = 9;

    public record GiftRequest(String occasion,
                              Integer ageYears,
                              String ageGroup,
                              List<String> interests,
                              Double budgetMin,
                              Double budgetMax,
                              String relationship) {
    }

    /** A reason code the UI translates, plus an optional value (interest, age band…). */
    public record Reason(String code, String value) {
    }

    public record Suggestion(Long id,
                             String name,
                             String category,
                             BigDecimal price,
                             Integer stockQuantity,
                             String brand,
                             Double averageRating,
                             double score,
                             List<Reason> reasons) {
    }

    public record Result(List<Suggestion> suggestions,
                         int considered,
                         double budgetMin,
                         double budgetMax) {
    }

    public Result suggest(GiftRequest request, List<Product> products,
                          Map<Long, Double> ratings, Map<Long, Long> unitsSold) {
        GiftRequest safe = request == null
                ? new GiftRequest(null, null, null, List.of(), null, null, null)
                : request;

        String occasion = normalize(safe.occasion(), "any");
        List<String> interests = safe.interests() == null ? List.of()
                : safe.interests().stream()
                        .filter(i -> i != null && !i.isBlank())
                        .map(i -> i.toLowerCase(Locale.ROOT).strip())
                        .distinct()
                        .limit(4)
                        .toList();
        String band = ageBand(safe.ageYears(), safe.ageGroup());

        double min = safe.budgetMin() == null ? 0.0 : Math.max(0.0, safe.budgetMin());
        double max = safe.budgetMax() == null ? Double.MAX_VALUE : safe.budgetMax();
        if (max < min) {
            double swap = min;
            min = max;
            max = swap;
        }

        Map<String, Integer> perCategory = new LinkedHashMap<>();
        List<Suggestion> ranked = new ArrayList<>();
        int considered = 0;

        for (Product product : products) {
            if (product.getPrice() == null || product.getStockQuantity() == null
                    || product.getStockQuantity() <= 0) {
                continue;
            }
            double price = product.getPrice().doubleValue();
            if (price < min || price > max) {
                continue;
            }
            considered++;

            String category = product.getCategory() == null ? "" : product.getCategory();
            String haystack = haystack(product);
            List<Reason> reasons = new ArrayList<>();
            double score = 0.0;

            for (String interest : interests) {
                if (matchesInterest(interest, category, haystack)) {
                    score += categoryWeight(INTEREST_CATEGORIES, interest, category) > 0 ? 2.2 : 1.4;
                    reasons.add(new Reason("interest", interest));
                }
            }

            double occasionCategory = categoryWeight(OCCASION_CATEGORIES, occasion, category);
            boolean occasionKeyword = containsAny(haystack, OCCASION_KEYWORDS.getOrDefault(occasion, List.of()));
            if (occasionCategory > 0 || occasionKeyword) {
                score += occasionCategory * 0.9 + (occasionKeyword ? 1.2 : 0.0);
                reasons.add(new Reason("occasion", occasion));
            }

            if (band != null) {
                double ageWeight = categoryWeight(AGE_CATEGORIES, band, category);
                if (ageWeight > 0) {
                    score += ageWeight;
                    reasons.add(new Reason("age", band));
                }
            }

            if (isGiftPackaged(haystack)) {
                score += 1.1;
                reasons.add(new Reason("gift", null));
            }

            Double average = ratings.get(product.getId());
            if (average != null && average >= 4.0) {
                score += (average - 3.5) * 0.8;
                reasons.add(new Reason("rated", String.format(Locale.ROOT, "%.1f", average)));
            }

            long sold = unitsSold.getOrDefault(product.getId(), 0L);
            if (sold >= 5) {
                score += Math.min(0.6, sold * 0.02);
                reasons.add(new Reason("popular", null));
            }

            if (safe.budgetMax() != null) {
                reasons.add(new Reason("budget", null));
            }

            ranked.add(new Suggestion(product.getId(), product.getName(), category,
                    product.getPrice(), product.getStockQuantity(), product.getBrand(),
                    average, Math.round(score * 100.0) / 100.0, List.copyOf(reasons)));
        }

        ranked.sort(Comparator.comparingDouble(Suggestion::score).reversed()
                .thenComparing(Suggestion::name));

        // Keep the shortlist varied: no category may take more than two slots
        // while a different category is still available.
        List<Suggestion> picked = new ArrayList<>();
        List<Suggestion> overflow = new ArrayList<>();
        for (Suggestion suggestion : ranked) {
            if (picked.size() >= MAX_RESULTS) {
                break;
            }
            int used = perCategory.getOrDefault(suggestion.category(), 0);
            if (used >= 2) {
                overflow.add(suggestion);
                continue;
            }
            perCategory.merge(suggestion.category(), 1, Integer::sum);
            picked.add(suggestion);
        }
        for (Suggestion suggestion : overflow) {
            if (picked.size() >= MAX_RESULTS) {
                break;
            }
            if (!picked.contains(suggestion)) {
                picked.add(suggestion);
            }
        }

        return new Result(picked, considered, min, max);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.toLowerCase(Locale.ROOT).strip();
    }

    private static String ageBand(Integer years, String group) {
        if (years != null) {
            if (years <= 2) {
                return "toddler";
            }
            if (years <= 12) {
                return "child";
            }
            if (years <= 19) {
                return "teen";
            }
            if (years <= 39) {
                return "adult";
            }
            return "senior";
        }
        if (group == null) {
            return null;
        }
        return switch (group.toLowerCase(Locale.ROOT).strip()) {
            case "baby", "toddler", "0-2" -> "toddler";
            case "child", "kid", "kids", "3-12" -> "child";
            case "teen", "teenager", "13-19" -> "teen";
            case "adult", "20-39" -> "adult";
            case "senior", "40+" -> "senior";
            default -> null;
        };
    }

    private static String haystack(Product product) {
        StringBuilder text = new StringBuilder();
        append(text, product.getName());
        append(text, product.getDescription());
        append(text, product.getCategory());
        append(text, product.getBrand());
        append(text, product.getSpecifications());
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static void append(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(' ').append(value);
        }
    }

    private static double categoryWeight(Map<String, Map<String, Double>> table,
                                         String key, String category) {
        Map<String, Double> weights = table.get(key);
        if (weights == null || category == null) {
            return 0.0;
        }
        return weights.getOrDefault(category, 0.0);
    }

    private static boolean matchesInterest(String interest, String category, String haystack) {
        if (categoryWeight(INTEREST_CATEGORIES, interest, category) > 0) {
            return true;
        }
        return containsAny(haystack, INTEREST_KEYWORDS.getOrDefault(interest, List.of()));
    }

    private static boolean containsAny(String haystack, List<String> words) {
        for (String word : words) {
            if (haystack.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGiftPackaged(String haystack) {
        return containsAny(haystack, List.of("gift", "hamper", "keepsake", "presentation case",
                "gift box", "gift wrap", "gift sleeve", "gift basket"));
    }

    private static final Map<String, Map<String, Double>> INTEREST_CATEGORIES = table(
            "tech", Map.of("Electronics", 1.0, "Mobiles", 1.0, "Laptops", 1.0, "Headphones", 1.0, "Watches", 0.6),
            "fashion", Map.of("Fashion", 1.0, "Shoes", 1.0),
            "beauty", Map.of("Beauty", 1.0),
            "home", Map.of("Home & Living", 1.0),
            "cooking", Map.of("Groceries & Food", 1.0, "Home & Living", 0.4),
            "fitness", Map.of("Sports", 1.0),
            "books", Map.of("Books & Stationery", 1.0),
            "music", Map.of("Headphones", 1.0, "Electronics", 0.4),
            "pets", Map.of("Pets", 1.0),
            "kids", Map.of("Toys & Kids", 1.0),
            "travel", Map.of("Sports", 0.5, "Electronics", 0.4, "Fashion", 0.4),
            "gardening", Map.of("Home & Living", 0.6));

    private static final Map<String, List<String>> INTEREST_KEYWORDS = Map.ofEntries(
            Map.entry("tech", List.of("smart", "wireless", "bluetooth", "4k", "camera", "phone", "laptop",
                    "ssd", "tv", "charger", "gadget")),
            Map.entry("fashion", List.of("jacket", "shirt", "jeans", "bag", "scarf", "sunglasses", "sneaker")),
            Map.entry("beauty", List.of("serum", "cream", "lipstick", "skincare", "perfume", "bath", "makeup")),
            Map.entry("home", List.of("candle", "cushion", "vase", "planter", "diffuser", "frame", "rug", "curtain")),
            Map.entry("cooking", List.of("coffee", "tea", "spice", "mithai", "hamper", "kitchen", "mug", "sweets")),
            Map.entry("fitness", List.of("yoga", "dumbbell", "gym", "running", "fitness", "bottle", "band")),
            Map.entry("books", List.of("journal", "novel", "pen", "planner", "notebook", "book")),
            Map.entry("music", List.of("speaker", "earbuds", "headphone", "audio", "bass")),
            Map.entry("pets", List.of("dog", "cat", "pet", "aquarium", "puppy", "kitten")),
            Map.entry("kids", List.of("puzzle", "teddy", "craft", "art", "toy", "kids", "plush")),
            Map.entry("travel", List.of("bottle", "power bank", "backpack", "carrier", "luggage", "travel")),
            Map.entry("gardening", List.of("plant", "planter", "vase", "seed", "gardening", "succulent")));

    private static final Map<String, Map<String, Double>> OCCASION_CATEGORIES = table(
            "birthday", Map.of("Toys & Kids", 1.0, "Beauty", 0.8, "Home & Living", 0.8,
                    "Electronics", 0.6, "Fashion", 0.6),
            "anniversary", Map.of("Home & Living", 1.2, "Beauty", 1.0, "Groceries & Food", 0.6),
            "wedding", Map.of("Home & Living", 1.2, "Beauty", 0.8, "Fashion", 0.6),
            "baby-shower", Map.of("Toys & Kids", 1.4, "Beauty", 0.5),
            "housewarming", Map.of("Home & Living", 1.4),
            "graduation", Map.of("Books & Stationery", 1.2, "Electronics", 0.8,
                    "Watches", 0.8, "Laptops", 0.8),
            "festival", Map.of("Groceries & Food", 1.2, "Home & Living", 0.8, "Beauty", 0.6),
            "thank-you", Map.of("Beauty", 1.0, "Home & Living", 0.8, "Groceries & Food", 0.8),
            "corporate", Map.of("Books & Stationery", 1.2, "Electronics", 0.6),
            "just-because", Map.of("Home & Living", 0.4, "Groceries & Food", 0.4, "Beauty", 0.4));

    private static final Map<String, List<String>> OCCASION_KEYWORDS = Map.of(
            "birthday", List.of("birthday", "candle", "hamper", "sweets", "craft"),
            "anniversary", List.of("anniversary", "crystal", "couple", "candle", "frame"),
            "wedding", List.of("crystal", "dinner", "frame", "set"),
            "baby-shower", List.of("newborn", "baby", "organic", "muslin", "plush"),
            "housewarming", List.of("planter", "lamp", "vase", "diffuser", "basket", "frame"),
            "graduation", List.of("journal", "planner", "pen", "watch", "laptop"),
            "festival", List.of("festive", "sweets", "mithai", "candle", "hamper"),
            "thank-you", List.of("bath", "coffee", "candle", "hamper", "gift"),
            "corporate", List.of("corporate", "pen", "planner", "notebook", "desk"),
            "just-because", List.of("gift", "box", "set"));

    private static final Map<String, Map<String, Double>> AGE_CATEGORIES = table(
            "toddler", Map.of("Toys & Kids", 2.0),
            "child", Map.of("Toys & Kids", 2.0, "Books & Stationery", 0.8, "Electronics", 0.2),
            "teen", Map.of("Electronics", 1.0, "Mobiles", 1.2, "Headphones", 1.2,
                    "Fashion", 1.0, "Books & Stationery", 0.6),
            "adult", Map.of("Beauty", 0.8, "Fashion", 0.8, "Home & Living", 0.8,
                    "Electronics", 0.6, "Sports", 0.5),
            "senior", Map.of("Home & Living", 1.2, "Books & Stationery", 0.8,
                    "Groceries & Food", 0.8, "Beauty", 0.6));

    @SafeVarargs
    private static Map<String, Map<String, Double>> table(Object... entries) {
        Map<String, Map<String, Double>> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            @SuppressWarnings("unchecked")
            Map<String, Double> weights = (Map<String, Double>) entries[i + 1];
            result.put((String) entries[i], weights);
        }
        return result;
    }
}
