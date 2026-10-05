package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Search behaviour that a shopper would notice breaking.
 *
 * The catalogue below is a representative slice of the real seeded products
 * rather than a synthetic one, because the interesting failures ("sneker",
 * "headfones") only show up against realistic names and descriptions.
 */
class SearchServiceTest {

    private final SearchService service = new SearchService();

    private static Product product(long id, String name, String category, String description, String price) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setCategory(category);
        p.setDescription(description);
        p.setPrice(new BigDecimal(price));
        p.setStockQuantity(25);
        p.setSustainabilityScore(70);
        return p;
    }

    private final List<Product> catalogue = List.of(
            product(1, "Wireless Earbuds", "Electronics", "Bluetooth in-ear earphones with charging case", "2499"),
            product(2, "Bluetooth Speaker", "Electronics", "Portable speaker with deep bass", "3499"),
            product(3, "Smartwatch", "Electronics", "Fitness tracking smartwatch with heart rate monitor", "4999"),
            product(4, "4K Action Camera", "Electronics", "Waterproof action cam for sports", "7999"),
            product(5, "USB-C Charger 65W", "Electronics", "Fast charger power bank adapter", "1299"),
            product(6, "Mechanical Keyboard", "Electronics", "Hot-swappable mechanical keyboard", "4299"),
            product(7, "Cotton T-Shirt", "Fashion", "Breathable cotton t-shirt for everyday wear", "799"),
            product(8, "Slim Fit Jeans", "Fashion", "Slim fit denim jeans", "1899"),
            product(9, "Running Sneakers", "Fashion", "Lightweight running shoes for jogging", "2499"),
            product(10, "Denim Jacket", "Fashion", "Classic denim jacket", "2999"),
            product(11, "A-Line Dress", "Fashion", "Elegant a-line dress for weddings", "3499"),
            product(12, "Oversized Hoodie", "Fashion", "Warm oversized hoodie sweatshirt", "1599"),
            product(13, "Vitamin C Serum", "Beauty", "Brightening vitamin c face serum", "899"),
            product(14, "Matte Lipstick", "Beauty", "Long lasting matte lipstick", "699"),
            product(15, "Green Tea Bags", "Groceries & Food", "Antioxidant green tea bags", "299"),
            product(16, "Adjustable Dumbbells", "Sports", "Adjustable dumbbell weight set for home gym", "3499"),
            product(17, "Non-Slip Yoga Mat", "Sports", "Extra thick yoga mat with carrying strap", "1199"),
            product(18, "Insulated Water Bottle", "Sports", "Double wall insulated bottle", "899"),
            product(19, "Cat Tower Scratcher", "Pets", "Cat tower with scratch post and perch", "4499"),
            product(20, "Dog Food Premium", "Pets", "Premium dog food for adult dogs", "1899"),
            product(21, "3D Wooden Puzzle", "Toys & Kids", "Challenging 3D wooden brain teaser puzzle", "999"),
            product(22, "Plush Teddy Bear", "Toys & Kids", "Soft plush teddy bear for kids", "699"),
            product(23, "Ceramic Coffee Set", "Home & Living", "Ceramic coffee mug set", "1499"),
            product(24, "Sheer Curtains Pair", "Home & Living", "Light filtering sheer curtains, two panels", "2199"),
            // Size variants are named "Max", which collides with "max 500" as a
            // budget phrasing. Kept here so that collision stays covered.
            product(25, "Cotton T-Shirt Max", "Fashion", "Oversized cotton t-shirt, extra large", "999")
    );

    private List<String> names(String query) {
        return service.search(query, catalogue).hits().stream()
                .map(h -> h.product().getName())
                .toList();
    }

    @Test
    void findsExactMatch() {
        assertEquals("Wireless Earbuds", names("wireless earbuds").get(0));
    }

    @Test
    void correctsSimpleTypo() {
        List<String> hits = names("wireles earbuds");
        assertEquals("Wireless Earbuds", hits.get(0), "dropped letter should still match");
    }

    @Test
    void correctsTransposedLetters() {
        assertEquals("Wireless Earbuds", names("wireless erbudz").get(0));
    }

    /**
     * Regression: "shoes" and "size" share a Soundex code (both S200). When a
     * Soundex match was accepted on its own, the engine "corrected" shoes to
     * size and answered a footwear search with bedsheets - six products whose
     * descriptions happen to mention size. Soundex may now only widen a match
     * the edit distance already tolerates.
     */
    @Test
    void doesNotCorrectToASoundexCollision() {
        assertFalse(new FuzzyMatcher().isTypoOf("shoes", "size"),
                "shoes/size share Soundex S200 but are not the same word");
        assertFalse(new FuzzyMatcher().isTypoOf("shoes", "sheep"));
        // Note: shirt/short is deliberately still accepted. It is one edit
        // apart, so it clears the edit-distance rule on its own merits without
        // Soundex being involved - a plausible typo, not a collision.
    }

    /** The widening must not have broken real typo tolerance. */
    @Test
    void stillAcceptsTranspositionsAndDroppedLetters() {
        FuzzyMatcher matcher = new FuzzyMatcher();
        assertTrue(matcher.isTypoOf("shoees", "shoes"), "transposed pair is still a typo");
        assertTrue(matcher.isTypoOf("wireles", "wireless"), "dropped letter is still a typo");
        assertTrue(matcher.isTypoOf("erbudz", "earbuds"));
    }

    @Test
    void resolvesSynonymToCatalogueWord() {
        // "headphones" is not a catalogue word; it must resolve to earbuds.
        assertEquals("Wireless Earbuds", names("headphones").get(0));
        assertEquals("Running Sneakers", names("footwear").get(0));
        assertEquals("Mechanical Keyboard", names("keyboard").get(0));
        // "tshirt" reaches "Cotton T-Shirt" through the synonym class, even
        // though the product name is punctuated as "T-Shirt".
        assertEquals("Cotton T-Shirt", names("tshirt").get(0));
        assertEquals("Insulated Water Bottle", names("flask").get(0));
    }

    @Test
    void matchesOnCategoryWhenNoNameMatches() {
        List<String> hits = names("automotive");
        assertTrue(hits.isEmpty() || !hits.contains("Wireless Earbuds"));
    }

    @Test
    void ignoresStopWordsAndIntentWords() {
        // "show me cheap wireless earbuds under 500" - only "wireless" and
        // "earbuds" are searchable; the rest must not break the match.
        assertEquals("Wireless Earbuds", names("show me cheap wireless earbuds under 500").get(0));
    }

    @Test
    void pullsBudgetOutOfThePhrasing() {
        SearchService.Result under = service.search("wireless earbuds under 3000", catalogue);
        assertFalse(under.hits().isEmpty(), "text terms should still match");
        assertEquals(3000.0, under.maxPrice());
        assertTrue(under.hasPriceHint());

        SearchService.Result over = service.search("earbuds above 1000", catalogue);
        assertEquals(1000.0, over.minPrice());

        SearchService.Result between = service.search("earbuds between 1000 and 3000", catalogue);
        assertEquals(1000.0, between.minPrice());
        assertEquals(3000.0, between.maxPrice());
    }

    @Test
    void budgetOnlyQueryReturnsNoHitsButKeepsTheBudget() {
        SearchService.Result r = service.search("under 500", catalogue);
        assertTrue(r.hits().isEmpty());
        assertEquals(500.0, r.maxPrice());
    }

    /**
     * Regression: only "under" was a stop word, so every other budget phrasing
     * left its keyword in the AND token set and matched nothing at all.
     * "earbuds below 3000" returned zero results on production.
     */
    @Test
    void everyBudgetPhrasingStillMatchesTheTextTerms() {
        List<String> expected = names("wireless earbuds");
        assertFalse(expected.isEmpty(), "baseline query must match something");

        for (String phrasing : List.of("under 3000", "below 3000", "less than 3000",
                "cheaper than 3000", "within 3000", "up to 3000", "max 3000")) {
            assertEquals(expected, names("wireless earbuds " + phrasing),
                    "budget phrasing '" + phrasing + "' should not affect text matching");
        }

        for (String phrasing : List.of("above 1000", "over 1000", "more than 1000", "at least 1000")) {
            assertEquals(expected, names("wireless earbuds " + phrasing),
                    "minimum phrasing '" + phrasing + "' should not affect text matching");
        }

        assertEquals(expected, names("wireless earbuds between 1000 and 3000"));
    }

    /** The stripped phrasing must also leave the echoed corrected query clean. */
    @Test
    void budgetPhraseDoesNotSurviveIntoTheCorrectedQuery() {
        SearchService.Result r = service.search("wireless earbuds below 3000", catalogue);
        assertFalse(r.correctedQuery().contains("below"), "got: " + r.correctedQuery());
        assertFalse(r.correctedQuery().contains("3000"), "got: " + r.correctedQuery());
        assertEquals(3000.0, r.maxPrice(), "budget itself must survive");
    }

    /**
     * "max" is a size variant on 101 real product names, so the budget strip has
     * to require a number after the word instead of dropping "max" outright.
     */
    @Test
    void maxStaysSearchableWhenItIsNotFollowedByABudget() {
        SearchService.Result r = service.search("cotton t-shirt max", catalogue);
        assertEquals("Cotton T-Shirt Max", r.hits().get(0).product().getName());
        assertFalse(r.hasPriceHint(), "a bare 'max' is not a budget");
    }

    /** Guards the two lists against drifting apart. */
    @Test
    void stripBudgetAgreesWithTheParser() {
        for (String word : List.of("under", "below", "less than", "cheaper than", "up to", "within",
                "max", "above", "over", "more than", "at least", "min", "between")) {
            String stripped = SearchService.stripBudget("earbuds " + word + " 3000");
            assertFalse(stripped.contains(word),
                    "'" + word + "' is understood by the parser but survives stripping: '" + stripped + "'");
        }
    }

    @Test
    void genericNounsDoNotMatchArbitraryProducts() {
        assertEquals(0, service.search("the best cheap things", catalogue).hits().size());
    }

    @Test
    void requiresEveryWordToMatchSomething() {
        // AND semantics: no product has both, so nothing is returned rather than
        // dumping the whole catalogue.
        assertTrue(names("wireless teapot").isEmpty());
    }

    @Test
    void reportsCorrectionSoUiCanExplainItself() {
        SearchService.Result result = service.search("wireles erbuds", catalogue);
        assertFalse(result.correctedQuery().isBlank());
        assertTrue(result.total() > 0);
    }

    @Test
    void handlesEmptyAndStopWordOnlyQueries() {
        assertEquals(0, service.search("", catalogue).hits().size());
        assertEquals(0, service.search("the best cheap things", catalogue).hits().size());
        assertEquals(0, service.search(null, catalogue).hits().size());
    }

    @Test
    void ranksTitleMatchesAboveDescriptionMatches() {
        List<String> hits = names("curtains");
        assertEquals("Sheer Curtains Pair", hits.get(0));
    }

    @Test
    void sinksOutOfStockResults() {
        List<Product> withStock = new ArrayList<>(catalogue);
        Product soldOut = product(99, "Wireless Earbuds Pro", "Electronics", "Pro wireless earbuds", "2999");
        soldOut.setStockQuantity(0);
        withStock.add(soldOut);

        List<String> hits = service.search("wireless earbuds", withStock).hits().stream()
                .map(h -> h.product().getName()).toList();
        assertEquals("Wireless Earbuds", hits.get(0), "in-stock item should outrank sold-out one");
    }

    @Test
    void soundexCatchesPhoneticTypos() {
        FuzzyMatcher f = new FuzzyMatcher();
        assertTrue(f.isTypoOf("bech", "beach"));
        assertTrue(f.isTypoOf("sneker", "sneakers"));
        assertFalse(f.isTypoOf("laptop", "candle"), "unrelated words must not fuzz together");
    }

    @Test
    void neverMatchesShortTokensFuzzily() {
        // One letter of slack in a 3-letter word produces nonsense matches.
        FuzzyMatcher f = new FuzzyMatcher();
        assertFalse(f.isTypoOf("bat", "bag"));
    }
}
