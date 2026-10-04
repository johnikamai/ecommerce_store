package com.ecommerce.ecommerce_system.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundle ladder is duplicated in CartContext.jsx so the cart can preview the
 * saving before checkout. These tests pin the server side of that pair; if a
 * tier changes here it must change there too, or the shopper is shown a
 * discount the server does not honour.
 */
class BundleDiscountTest {

    @Test
    void oneProductEarnsNothing() {
        assertEquals(0, OrderService.bundleDiscountPercent(1).compareTo(BigDecimal.ZERO));
        assertEquals(0, OrderService.bundleDiscountPercent(0).compareTo(BigDecimal.ZERO));
    }

    @Test
    void twoProductsEarnFivePercent() {
        assertEquals(0, OrderService.bundleDiscountPercent(2).compareTo(new BigDecimal("0.05")));
    }

    @Test
    void threeProductsEarnTenPercent() {
        assertEquals(0, OrderService.bundleDiscountPercent(3).compareTo(new BigDecimal("0.10")));
    }

    @Test
    void fourOrMoreEarnFifteenPercent() {
        for (int n : new int[] {4, 5, 9, 40}) {
            assertEquals(0, OrderService.bundleDiscountPercent(n).compareTo(new BigDecimal("0.15")),
                    "expected 15% at " + n + " distinct products");
        }
    }

    @Test
    void tiersOnlyEverIncrease() {
        BigDecimal previous = BigDecimal.ZERO;
        for (int n = 1; n <= 10; n++) {
            BigDecimal current = OrderService.bundleDiscountPercent(n);
            assertTrue(current.compareTo(previous) >= 0,
                    "tier dropped going from " + (n - 1) + " to " + n + " products");
            previous = current;
        }
    }

    @Test
    void nextTierWalksUpThenStops() {
        assertEquals(2, OrderService.nextBundleTier(0));
        assertEquals(2, OrderService.nextBundleTier(1));
        assertEquals(3, OrderService.nextBundleTier(2));
        assertEquals(4, OrderService.nextBundleTier(3));
        assertEquals(0, OrderService.nextBundleTier(4), "top tier reached, nothing left to chase");
        assertEquals(0, OrderService.nextBundleTier(9));
    }
}
