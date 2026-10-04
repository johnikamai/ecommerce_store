package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour a shopper would notice the assistant getting wrong.
 *
 * The catalogue mirrors the real seeded products, because the whole point of the
 * occasion/context mapping is that it is grounded in descriptions that really
 * exist - a test against invented products would pass while the feature stayed
 * useless.
 */
class AssistantServiceTest {

    private final SearchService search = new SearchService();
    private final AssistantService service = new AssistantService(search, null);

    private static Product product(long id, String name, String category, String description,
                                   String price, int stock, int eco) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setCategory(category);
        p.setDescription(description);
        p.setPrice(new BigDecimal(price));
        p.setStockQuantity(stock);
        p.setSustainabilityScore(eco);
        p.setImageUrl("/products/" + id + ".webp");
        return p;
    }

    /** The real ten-category slice, descriptions copied from the live catalogue. */
    private final List<Product> catalogue = List.of(
            product(1, "Wireless Earbuds", "Electronics", "Noise-cancelling Bluetooth earbuds with 30-hour battery life and wireless charging case.", "2499", 25, 60),
            product(2, "Bluetooth Speaker", "Electronics", "Portable speaker with punchy bass, IPX7 waterproofing and 12-hour playtime.", "3499", 12, 55),
            product(3, "Smartwatch", "Electronics", "Tracks steps, heart rate and sleep with a 7-day battery and AMOLED display.", "4999", 8, 65),
            product(4, "USB-C Charger 65W", "Electronics", "Fast-charges laptop, tablet and phone from one compact GaN brick.", "1299", 40, 70),
            product(5, "Cotton T-Shirt", "Fashion", "Breathable 100% combed cotton tee in soft pastel shades.", "799", 60, 88),
            product(6, "Slim Fit Jeans", "Fashion", "Stretch denim with a modern slim fit and flexible waistband.", "1899", 30, 70),
            product(7, "Running Sneakers", "Fashion", "Lightweight knit sneakers with a cushioned impact sole.", "2499", 22, 75),
            product(8, "Denim Jacket", "Fashion", "Classic medium-wash jacket with button-flap pockets.", "2999", 18, 72),
            product(9, "A-Line Dress", "Fashion", "Flowy knee-length dress that works for work or weekends.", "3499", 9, 80),
            product(10, "Oversized Hoodie", "Fashion", "Warm oversized hoodie sweatshirt.", "1599", 26, 85),
            product(11, "Vitamin C Serum", "Beauty", "Brightening 10% vitamin C serum with hyaluronic acid.", "899", 44, 72),
            product(12, "Dark Chocolate Box", "Groceries & Food", "70% dark chocolate truffles in a gift box.", "1299", 55, 60),
            product(13, "Arabica Coffee Beans", "Groceries & Food", "Freshly roasted medium-roast beans, 500 g.", "449", 80, 68),
            product(14, "Non-Slip Yoga Mat", "Sports", "Eco-friendly TPE mat with alignment lines.", "1199", 35, 92),
            product(15, "Adjustable Dumbbells", "Sports", "2-in-1 dumbbell set with a compact rack.", "3499", 14, 70),
            product(16, "Insulated Water Bottle", "Sports", "Steel bottle keeps drinks cold for 24 hours.", "899", 48, 88),
            product(17, "Pet Travel Carrier", "Pets", "Foldable airline-approved mesh carrier.", "2299", 11, 65),
            product(18, "Ceramic Coffee Set", "Home & Living", "Two speckled pastel-glaze mugs with saucers.", "1499", 20, 78),
            product(19, "Faux Fiddle-Leaf Plant", "Home & Living", "Low-maintenance faux fig in a woven pot.", "1199", 16, 90),
            product(20, "Plush Teddy Bear", "Toys & Kids", "Ultra-soft 40 cm teddy bear, machine washable.", "699", 50, 76),
            product(21, "Car Cleaning Kit", "Automotive", "Microfibre mitt, glass spray and dash polish.", "899", 28, 64),
            product(22, "Tyre Inflator", "Automotive", "12V portable air compressor with digital display.", "1699", 24, 66)
    );

    @Test
    void understandsTheBeachWeddingPhrase() {
        AssistantService.Reply r = service.chat("find a dress for a beach wedding next week", null, catalogue);

        assertFalse(r.products().isEmpty(), "should find something for a beach wedding");
        // The one dress in the catalogue must lead.
        assertEquals("A-Line Dress", r.products().get(0).get("name"));
        // "beach" must not be treated as a product noun - there is no "beach" product.
        assertTrue(r.reply().contains("A-Line Dress"));
    }

    @Test
    void admitsItCannotConfirmDeliveryTiming() {
        AssistantService.Reply r = service.chat("dress for a beach wedding next week", null, catalogue);
        assertTrue(r.reply().toLowerCase().contains("delivery"),
                "must flag that delivery dates are unknown rather than implying it will arrive");
    }

    @Test
    void appliesBudget() {
        AssistantService.Reply r = service.chat("headphones under 3000", null, catalogue);
        assertFalse(r.products().isEmpty());
        for (Object o : r.products()) {
            BigDecimal price = (BigDecimal) ((java.util.Map<?, ?>) o).get("price");
            assertTrue(price.doubleValue() <= 3000,
                    "budget must be enforced: " + price);
        }
    }

    @Test
    void echoesWhatItUnderstoodSoMistakesAreVisible() {
        AssistantService.Reply r = service.chat("wireles earbuds under 3000", null, catalogue);
        assertFalse(r.products().isEmpty());
        assertFalse(r.understood().isEmpty(), "shopper should be able to see what was extracted");
    }

    @Test
    void neverInventsProductsThatAreNotStocked() {
        AssistantService.Reply r = service.chat("a dress for a beach wedding next week", null, catalogue);
        for (Object o : r.products()) {
            long id = ((Number) ((java.util.Map<?, ?>) o).get("id")).longValue();
            assertTrue(catalogue.stream().anyMatch(p -> p.getId() == id),
                    "every suggested id must exist in the catalogue");
        }
    }

    @Test
    void saysNoInsteadOfGuessingWhenNothingMatches() {
        AssistantService.Reply r = service.chat("a solid oak dining table", null, catalogue);
        assertTrue(r.products().isEmpty());
        assertTrue(r.reply().toLowerCase().contains("could not find"));
        assertTrue(r.reply().toLowerCase().contains("will not invent"),
                "should be explicit that it declines to hallucinate");
    }

    @Test
    void routesOrderStatusQuestionsAway() {
        AssistantService.Reply r = service.chat("where is my order", null, catalogue);
        assertTrue(r.reply().toLowerCase().contains("orders page"));
        assertTrue(r.products().isEmpty());
    }

    @Test
    void handlesGreetingAndThanks() {
        assertTrue(service.chat("hi", null, catalogue).reply().toLowerCase().contains("hello"));
        assertTrue(service.chat("thanks!", null, catalogue).reply().toLowerCase().contains("any time"));
    }

    @Test
    void capsOutOfStockMatches() {
        List<Product> soldOut = catalogue.stream().map(p -> {
            p.setStockQuantity(0);
            return p;
        }).toList();
        AssistantService.Reply r = service.chat("dress", null, soldOut);
        assertTrue(r.products().isEmpty());
        assertTrue(r.reply().toLowerCase().contains("out of stock"));
    }

    @Test
    void contextBoostsWithoutGatingResults() {
        // "for travel" must promote the carrier without excluding everything
        // else that could plausibly be bought for a trip.
        AssistantService.Reply r = service.chat("something for travel", null, catalogue);
        assertFalse(r.products().isEmpty());
        assertTrue(r.products().stream().anyMatch(p ->
                        "Pet Travel Carrier".equals(((java.util.Map<?, ?>) p).get("name"))),
                "the airline-approved foldable carrier is the strongest travel match");
    }

    @Test
    void respectsMessageLengthCap() {
        AssistantService.Reply r = service.chat("x".repeat(5000), null, catalogue);
        assertTrue(r.reply() != null && !r.reply().isBlank());
    }

    @Test
    void reportsRulesSourceWhenNoLlmKeyConfigured() {
        AssistantService.Reply r = service.chat("wireless earbuds", null, catalogue);
        assertEquals("rules", r.source(), "must be honest about which engine answered");
    }
}
