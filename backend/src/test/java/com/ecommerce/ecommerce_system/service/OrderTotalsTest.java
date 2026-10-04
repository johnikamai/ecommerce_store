package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Delivery and tax arithmetic.
 *
 * These are static so the cart preview, the checkout screen and the invoice all
 * quote the same figures instead of three implementations that quietly drift
 * apart once a promotion is involved.
 */
class OrderTotalsTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Test
    void smallBasketPaysTheFlatShippingRate() {
        assertEquals(new BigDecimal("49.00"), OrderService.shippingCostFor(new BigDecimal("250.00")));
    }

    @Test
    void largeBasketShipsFree() {
        assertEquals(BigDecimal.ZERO.setScale(2), OrderService.shippingCostFor(new BigDecimal("999.00")));
        assertEquals(BigDecimal.ZERO.setScale(2), OrderService.shippingCostFor(new BigDecimal("1500.00")));
    }

    /**
     * The threshold is compared against the pre-discount subtotal. Had it used
     * the post-discount figure, a large basket plus a coupon would cross the line
     * and the customer would get the goods and the delivery for the coupon price.
     */
    @Test
    void thresholdIsMeasuredBeforeDiscounts() {
        BigDecimal subtotal = new BigDecimal("1000.00");
        BigDecimal afterCoupon = subtotal.subtract(new BigDecimal("300.00"));

        assertEquals(BigDecimal.ZERO.setScale(2), OrderService.shippingCostFor(subtotal),
                "the subtotal alone already qualifies for free delivery");
        assertEquals(new BigDecimal("49.00"), OrderService.shippingCostFor(afterCoupon),
                "a discounted total must not unlock free shipping");
    }

    @Test
    void emptyBasketIsNotChargedShipping() {
        assertEquals(BigDecimal.ZERO.setScale(2), OrderService.shippingCostFor(BigDecimal.ZERO));
        assertEquals(BigDecimal.ZERO.setScale(2), OrderService.shippingCostFor(null));
    }

    @Test
    void taxIsAFractionOfTheDiscountedGoodsValue() {
        assertEquals(new BigDecimal("180.00"), OrderService.taxFor(new BigDecimal("1000.00")));
        assertEquals(new BigDecimal("90.00"), OrderService.taxFor(new BigDecimal("500.00")));
    }

    /**
     * Shipping is a delivery service, not merchandise, so the tax due is worked out
     * on the goods alone. Taxing the grand total instead would add nearly nine
     * rupees of tax to a 49 rupee delivery.
     */
    @Test
    void shippingIsNotTaxed() {
        BigDecimal goods = new BigDecimal("500.00");
        BigDecimal shipping = OrderService.shippingCostFor(goods);
        BigDecimal tax = OrderService.taxFor(goods);

        assertEquals(new BigDecimal("90.00"), tax);
        assertEquals(new BigDecimal("639.00"), goods.add(shipping).add(tax));
        assertEquals(new BigDecimal("98.82"), OrderService.taxFor(goods.add(shipping)),
                "this is what the order would cost if the call site ever taxed the grand total");
    }

    @Test
    void taxRoundsToTwoPlaces() {
        // 333.33 * 0.18 = 59.9994, which rounds up to a clean 60.00. The point of
        // the assertion is the scale, not the magnitude.
        assertEquals(2, OrderService.taxFor(new BigDecimal("333.33")).scale());
        assertEquals(new BigDecimal("60.00"), OrderService.taxFor(new BigDecimal("333.33")));
    }

    @Test
    void nothingToTaxCostsNothing() {
        assertEquals(new BigDecimal("0.00"), OrderService.taxFor(BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.00"), OrderService.taxFor(null));
    }

    /**
     * The discount stack has to survive the new charges. A single item is below
     * both the shipping threshold and any bundle tier, so the arithmetic is fully
     * determined and catches an accidental reorder of the operations.
     */
    @Test
    void chargesStackOnTopOfAnUndiscountedBasket() {
        BigDecimal goods = new BigDecimal("250.00");
        BigDecimal shipping = OrderService.shippingCostFor(goods);
        BigDecimal tax = OrderService.taxFor(goods);

        assertEquals(new BigDecimal("344.00"), goods.add(shipping).add(tax));
    }

    @Test
    void loyaltyIsEarnedOnGoodsNotOnWhatTheCustomerPaid() {
        // 900 sits below the free-shipping threshold, so this basket carries a
        // real delivery charge and the over-crediting is visible: eleven points
        // from the grand total against nine from the goods.
        BigDecimal goods = new BigDecimal("900.00");
        assertEquals(new BigDecimal("49.00"), OrderService.shippingCostFor(goods));

        BigDecimal grandTotal = goods
                .add(OrderService.shippingCostFor(goods))
                .add(OrderService.taxFor(goods));

        int pointsFromGrandTotal = grandTotal.divide(HUNDRED, 0, java.math.RoundingMode.DOWN).intValue();
        int pointsFromGoods = goods.divide(HUNDRED, 0, java.math.RoundingMode.DOWN).intValue();

        assertEquals(9, pointsFromGoods, "900 of goods is nine points");
        assertEquals(11, pointsFromGrandTotal, "the grand total would wrongly pay eleven");
    }

    /**
     * Orders placed before shipping and tax existed have no merchandiseTotal
     * stored. Reading that as zero would quietly strip every long-standing
     * customer of their tier the next time a tier was recomputed.
     */
    @Test
    void ordersFromBeforeShippingAndTaxStillCountTheirSpend() {
        Order legacy = new Order();
        legacy.setTotalAmount(new BigDecimal("40000.00"));

        assertEquals(new BigDecimal("40000.00"), legacy.getMerchandiseTotalForScoring());
    }

    @Test
    void aCurrentOrderReportsItsDiscountedGoodsValue() {
        Order current = new Order();
        current.setMerchandiseTotal(new BigDecimal("750.00"));
        current.setShippingAmount(new BigDecimal("49.00"));
        current.setTaxAmount(new BigDecimal("135.00"));
        current.setTotalAmount(new BigDecimal("934.00"));

        assertEquals(new BigDecimal("750.00"), current.getMerchandiseTotalForScoring());
    }
}