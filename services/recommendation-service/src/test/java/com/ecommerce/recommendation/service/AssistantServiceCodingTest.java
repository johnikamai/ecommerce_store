package com.ecommerce.recommendation.service;

import com.ecommerce.recommendation.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantServiceCodingTest {

    private final SearchService search = new SearchService();
    private final AssistantService service = new AssistantService(search, null);

    private static Product product(long id, String name, String category, String description,
                                   String price, int stock, String specs) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setCategory(category);
        p.setDescription(description);
        p.setPrice(new BigDecimal(price));
        p.setStockQuantity(stock);
        p.setSpecifications(specs);
        p.setImageUrl("/products/" + id + ".webp");
        return p;
    }

    private final List<Product> catalog = List.of(
            product(101, "ApexBook Air 14", "Laptops", "Ultra-thin 14\" IPS, Intel Core i5, 16GB RAM, 512GB SSD.", "54999", 20, "CPU: Core i5 | RAM: 16GB | Storage: 512GB SSD"),
            product(102, "NovaLite Slim 15", "Laptops", "15.6\" FHD, Ryzen 5, 16GB RAM, 512GB SSD.", "47999", 25, "CPU: Ryzen 5 | RAM: 16GB | Storage: 512GB SSD"),
            product(103, "Apex Neo 5G", "Mobiles", "6.6\" AMOLED 120Hz 5000mAh.", "17999", 40, "RAM: 6GB"),
            product(104, "A-Line Dress", "Fashion", "Flowy dress.", "3499", 10, "Fabric: Cotton")
    );

    @Test
    void suggestsLaptopsForCodingUnder50000WithReason() {
        AssistantService.Reply r = service.chat("I need a laptop for coding under ₹50,000", null, catalog);

        assertFalse(r.products().isEmpty(), "Should match laptop under 50k");
        assertEquals("NovaLite Slim 15", r.products().get(0).get("name"));
        BigDecimal price = (BigDecimal) r.products().get(0).get("price");
        assertTrue(price.doubleValue() <= 50000, "Should satisfy ₹50,000 budget constraint");
        
        String reason = (String) r.products().get(0).get("reason");
        assertTrue(reason != null && !reason.isBlank(), "Should provide 'Why this fits' reason");
        assertTrue(reason.toLowerCase().contains("ram") || reason.toLowerCase().contains("compilation") || reason.toLowerCase().contains("processor"),
                "Reason should mention RAM/processor/compilation: " + reason);
    }
}
