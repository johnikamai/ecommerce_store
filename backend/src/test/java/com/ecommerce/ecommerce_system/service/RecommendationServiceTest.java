package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Order;
import com.ecommerce.ecommerce_system.model.OrderItem;
import com.ecommerce.ecommerce_system.model.OrderStatus;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.repository.OrderItemRepository;
import com.ecommerce.ecommerce_system.repository.OrderRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import com.ecommerce.ecommerce_system.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Personalised recommendation behaviour a shopper would notice breaking.
 *
 * The ranking has three deliberate rules - never re-suggest something already
 * bought, never suggest something unbuyable, and prefer the categories and
 * price band the shopper actually uses - so those are what get asserted here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private RecommendationService service;

    private static final long CUSTOMER = 7L;

    private static Product product(long id, String name, String category, String price, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setCategory(category);
        p.setPrice(new BigDecimal(price));
        p.setStockQuantity(stock);
        return p;
    }

    /** Builds a placed order holding the given products at quantity 1 each. */
    private static Order order(OrderStatus status, Product... products) {
        Order o = new Order();
        o.setStatus(status);
        List<OrderItem> items = new ArrayList<>();
        for (Product p : products) {
            OrderItem item = new OrderItem();
            item.setProduct(p);
            item.setQuantity(1);
            item.setUnitPrice(p.getPrice());
            item.setLineTotal(p.getPrice());
            items.add(item);
        }
        o.setOrderItems(items);
        return o;
    }

    @BeforeEach
    void stubAggregates() {
        // Both aggregate queries are unstubbed-by-default in most tests; an empty
        // result means "no ratings, no sales", which every ranking rule tolerates.
        when(reviewRepository.aggregateByProduct()).thenReturn(List.of());
        when(orderItemRepository.unitsSoldByProduct()).thenReturn(List.of());
    }

    private void catalogueIs(Product... products) {
        when(productRepository.findAll()).thenReturn(List.of(products));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> personalize(int limit) {
        return service.personalizedFor(CUSTOMER, limit);
    }

    private List<Long> idsOf(List<Map<String, Object>> recs) {
        return recs.stream().map(r -> (Long) r.get("id")).toList();
    }

    @Test
    void returnsEmptyWhenCustomerHasNoPurchaseHistory() {
        when(orderRepository.findByCustomerId(CUSTOMER)).thenReturn(List.of());
        catalogueIs(product(1, "Wireless Earbuds", "Electronics", "2499", 10));

        // Guessing at taste with no history would be fabricated, so it stays empty.
        assertTrue(personalize(8).isEmpty());
    }

    @Test
    void neverRecommendsSomethingAlreadyBought() {
        Product bought = product(1, "Wireless Earbuds", "Electronics", "2499", 10);
        Product other = product(2, "Bluetooth Speaker", "Electronics", "3499", 10);
        when(orderRepository.findByCustomerId(CUSTOMER))
                .thenReturn(List.of(order(OrderStatus.DELIVERED, bought)));
        catalogueIs(bought, other);

        assertEquals(List.of(2L), idsOf(personalize(8)));
    }

    @Test
    void neverRecommendsOutOfStock() {
        when(orderRepository.findByCustomerId(CUSTOMER)).thenReturn(List.of(
                order(OrderStatus.DELIVERED, product(1, "Wireless Earbuds", "Electronics", "2499", 10))));
        catalogueIs(
                product(2, "Bluetooth Speaker", "Electronics", "3499", 0),
                product(3, "Smartwatch", "Electronics", "4999", 10));

        // Product 2 cannot be actioned, so it must not be offered.
        assertEquals(List.of(3L), idsOf(personalize(8)));
    }

    @Test
    void ignoresCancelledOrdersWhenLearningTaste() {
        Product cancelled = product(1, "Wireless Earbuds", "Electronics", "2499", 10);
        when(orderRepository.findByCustomerId(CUSTOMER)).thenReturn(List.of(
                order(OrderStatus.CANCELLED, cancelled)));
        catalogueIs(cancelled, product(2, "Bluetooth Speaker", "Electronics", "3499", 10));

        // A cancelled order is not evidence of taste, so nothing is excluded and
        // no affinity exists to rank on.
        assertTrue(personalize(8).isEmpty());
    }

    @Test
    void prefersCategoriesTheCustomerAlreadySpendsIn() {
        Product phones = product(1, "Smartphone", "Electronics", "40000", 10);
        Product laptop = product(2, "Laptop", "Electronics", "90000", 10);
        Product novel = product(3, "Novel", "Books & Stationery", "500", 10);
        when(orderRepository.findByCustomerId(CUSTOMER)).thenReturn(List.of(
                order(OrderStatus.DELIVERED, phones)));
        catalogueIs(laptop, novel);

        List<Long> ranked = idsOf(personalize(8));
        // Same-category beats the unrelated category despite costing far more,
        // because category affinity outweighs the price-band penalty.
        assertEquals(2L, ranked.get(0));
        assertTrue(ranked.contains(3L), "unrelated item may still appear as a filler pick");
    }

    @Test
    void padsWithTopRatedWhenHistoryIsTooThinToRank() {
        when(orderRepository.findByCustomerId(CUSTOMER)).thenReturn(List.of(
                order(OrderStatus.DELIVERED, product(1, "Wireless Earbuds", "Electronics", "2499", 10))));
        Product inStock = product(2, "Bluetooth Speaker", "Electronics", "3499", 5);
        catalogueIs(inStock);

        List<Map<String, Object>> recs = personalize(8);
        assertEquals(1, recs.size());
        assertEquals(2L, recs.get(0).get("id"));
    }

    @Test
    void everyRecommendationCarriesTheFieldsTheStorefrontRenders() {
        when(orderRepository.findByCustomerId(CUSTOMER)).thenReturn(List.of(
                order(OrderStatus.DELIVERED, product(1, "Wireless Earbuds", "Electronics", "2499", 10))));
        Product candidate = product(2, "Bluetooth Speaker", "Electronics", "3499", 5);
        candidate.setImageUrl("/products/products-2.jpg");
        candidate.setSustainabilityScore(80);
        catalogueIs(candidate);

        Map<String, Object> rec = personalize(8).get(0);

        // imageUrl in particular: without it the card falls back to a generated
        // tile even though the product has a real photo.
        assertEquals("/products/products-2.jpg", rec.get("imageUrl"));
        assertEquals("Eco-Friendly", rec.get("sustainabilityLabel"));
        assertNotNull(rec.get("price"));
        assertNotNull(rec.get("reasonCode"), "reason must be a key, not pre-rendered English");
    }

    @Test
    void respectsTheRequestedLimit() {
        when(orderRepository.findByCustomerId(CUSTOMER)).thenReturn(List.of(
                order(OrderStatus.DELIVERED, product(1, "Wireless Earbuds", "Electronics", "2499", 10))));
        catalogueIs(
                product(2, "Bluetooth Speaker", "Electronics", "3499", 5),
                product(3, "Smartwatch", "Electronics", "4999", 5),
                product(4, "Laptop", "Electronics", "90000", 5),
                product(5, "Monitor", "Electronics", "12000", 5));

        assertEquals(2, personalize(2).size());
    }

    @Test
    void tasteProfileRanksCategoriesBySpend() {
        Product cheap = product(1, "Notebook", "Books & Stationery", "200", 10);
        Product dear = product(2, "Smartphone", "Electronics", "40000", 10);
        when(orderRepository.findByCustomerId(CUSTOMER)).thenReturn(List.of(
                order(OrderStatus.DELIVERED, cheap, dear)));
        catalogueIs(cheap, dear);

        List<Map<String, Object>> profile = service.tasteProfile(CUSTOMER);

        assertEquals(2, profile.size());
        assertEquals("Electronics", profile.get(0).get("category"), "highest spend ranks first");
        assertEquals(new BigDecimal("40000.00"), profile.get(0).get("spend"));
    }
}
