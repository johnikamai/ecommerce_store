package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GiftFinderServiceTest {

    private final GiftFinderService service = new GiftFinderService();

    private static Product product(long id, String name, String category, String price, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setCategory(category);
        product.setDescription(name);
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(stock);
        product.setBrand("ShopEase");
        return product;
    }

    private final List<Product> products = List.of(
            product(1, "Samsung Galaxy S24", "Mobiles", "74999", 10),
            product(2, "Samsung Galaxy Buds3 Pro", "Headphones", "19999", 10),
            product(3, "Wireless Earbuds Gift Edition", "Electronics", "2999", 10),
            product(4, "Kids Art & Craft Kit", "Toys & Kids", "899", 10),
            product(5, "Birthday Gift Hamper", "Groceries & Food", "1499", 10),
            product(6, "Non-Slip Yoga Mat", "Sports", "899", 0),
            product(7, "Skincare Gift Kit", "Beauty", "2999", 10),
            product(8, "Best-Selling Novel", "Books & Stationery", "399", 10),
            product(9, "Pet Bowl", "Pets", "499", 10),
            product(10, "Premium 8K TV", "Electronics", "120000", 5));

    private final Map<Long, Double> ratings = Map.of(1L, 4.6, 3L, 4.4, 5L, 4.8);
    private final Map<Long, Long> unitsSold = Map.of(3L, 20L);

    private static GiftFinderService.GiftRequest request(String occasion, Integer age, List<String> interests,
                                                         Double min, Double max) {
        return new GiftFinderService.GiftRequest(occasion, age, null, interests, min, max, "friend");
    }

    @Test
    void onlyReturnsPicksWithinBudget() {
        GiftFinderService.Result result = service.suggest(
                request("birthday", 30, List.of("tech"), 500.0, 3000.0), products, ratings, unitsSold);

        assertFalse(result.suggestions().isEmpty());
        for (GiftFinderService.Suggestion suggestion : result.suggestions()) {
            double price = suggestion.price().doubleValue();
            assertTrue(price >= 500.0 && price <= 3000.0,
                    suggestion.name() + " costs " + price + ", outside the budget");
        }
        assertEquals(500.0, result.budgetMin());
        assertEquals(3000.0, result.budgetMax());
    }

    @Test
    void excludesOutOfStockProducts() {
        GiftFinderService.Result result = service.suggest(
                request("birthday", 30, List.of("fitness"), 0.0, 5000.0), products, ratings, unitsSold);

        assertTrue(result.suggestions().stream().noneMatch(s -> s.id() == 6L),
                "an out-of-stock product must never be suggested");
    }

    @Test
    void interestShapesTheShortlist() {
        GiftFinderService.Result tech = service.suggest(
                request("birthday", 30, List.of("tech"), 2000.0, 80000.0), products, ratings, unitsSold);

        GiftFinderService.Suggestion bestTech = tech.suggestions().get(0);
        assertTrue(List.of("Electronics", "Mobiles", "Headphones").contains(bestTech.category()),
                "the top pick should come from the chosen interest, got " + bestTech.category());
        assertTrue(bestTech.reasons().stream()
                .anyMatch(r -> "interest".equals(r.code()) && "tech".equals(r.value())));

        GiftFinderService.Suggestion samsung = tech.suggestions().stream()
                .filter(s -> "Samsung Galaxy S24".equals(s.name()))
                .findFirst().orElseThrow();
        assertTrue(samsung.reasons().stream()
                .anyMatch(r -> "interest".equals(r.code()) && "tech".equals(r.value())));

        GiftFinderService.Result pets = service.suggest(
                request("birthday", 30, List.of("pets"), 0.0, 5000.0), products, ratings, unitsSold);

        List<String> petNames = pets.suggestions().stream().map(GiftFinderService.Suggestion::name).toList();
        assertTrue(petNames.indexOf("Pet Bowl") < petNames.indexOf("Best-Selling Novel"),
                "a pet gift must outrank an unrelated book, got: " + petNames);
    }

    @Test
    void ageShiftsGiftsTowardKids() {
        GiftFinderService.Result result = service.suggest(
                request("birthday", 8, List.of(), 0.0, 2000.0), products, ratings, unitsSold);

        assertTrue(result.suggestions().stream().limit(3).anyMatch(s -> "Kids Art & Craft Kit".equals(s.name())),
                "a child's gift list should lead with age-appropriate toys");
        assertTrue(result.suggestions().stream()
                        .anyMatch(s -> s.reasons().stream().anyMatch(r -> "age".equals(r.code())
                                && "child".equals(r.value()))),
                "the age band must be reported as a reason");
    }

    @Test
    void topPicksCarryTheirReasons() {
        GiftFinderService.Result result = service.suggest(
                request("housewarming", 30, List.of("home", "gardening"), 0.0, 5000.0), products, ratings, unitsSold);

        assertFalse(result.suggestions().isEmpty());
        result.suggestions().stream().limit(3).forEach(suggestion ->
                assertFalse(suggestion.reasons().isEmpty(), suggestion.name() + " has no reasons"));
    }

    @Test
    void keepsTheShortlistVariedAcrossCategories() {
        GiftFinderService.Result result = service.suggest(
                request("birthday", 30, List.of("tech"), 0.0, 200000.0), products, ratings, unitsSold);

        Map<String, Long> perCategory = result.suggestions().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        GiftFinderService.Suggestion::category, java.util.stream.Collectors.counting()));
        assertTrue(perCategory.values().stream().allMatch(count -> count <= 2),
                "too many picks from one category: " + perCategory);
    }

    @Test
    void swapsAReversedBudgetInsteadOfReturningNothing() {
        GiftFinderService.Result result = service.suggest(
                request("birthday", 30, List.of(), 3000.0, 500.0), products, ratings, unitsSold);

        assertEquals(500.0, result.budgetMin());
        assertEquals(3000.0, result.budgetMax());
        assertFalse(result.suggestions().isEmpty());
    }

    @Test
    void survivesAMissingRequest() {
        GiftFinderService.Result result = service.suggest(null, products, ratings, unitsSold);

        assertFalse(result.suggestions().isEmpty(), "no filters should still yield a shortlist");
        assertTrue(result.suggestions().size() <= 9);
    }
}
