package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.*;
import com.ecommerce.ecommerce_system.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutRegressionTest {
    @Mock CustomerRepository customers;
    @Mock ProductRepository products;
    @Mock CouponRepository coupons;
    @Mock OrderRepository orders;
    @Mock PaymentRepository payments;
    @Mock NotificationService notifications;
    @Mock StockAlertService stockAlerts;
    @InjectMocks OrderService service;
    Customer customer;
    OrderRequest request;
    Product product;

    @BeforeEach void setup() {
        customer = new Customer(); customer.setId(1L); customer.setTier("GOLD");
        product = new Product(); product.setId(1L); product.setName("Book");
        product.setPrice(new BigDecimal("1000.00")); product.setStockQuantity(5);
        request = new OrderRequest(); request.setCustomerId(1L); request.setPaymentMethod("CASH");
        request.setShippingAddress("12 Main Street, Hyderabad 500001");
        OrderRequest.Item item = new OrderRequest.Item(); item.setProductId(1L); item.setQuantity(1);
        request.setItems(List.of(item));
    }
    void mockCatalog() {
        when(customers.findById(1L)).thenReturn(Optional.of(customer));
        when(products.findById(1L)).thenReturn(Optional.of(product));
    }
    @Test void quoteIncludesCouponAndTierWithoutSpendingCouponOrStock() {
        mockCatalog();
        Coupon coupon = new Coupon(); coupon.setCode("TEN"); coupon.setActive(true);
        coupon.setDiscountType(CouponType.PERCENT); coupon.setDiscountValue(BigDecimal.TEN);
        coupon.setTimesUsed(0); request.setCouponCode("TEN");
        when(coupons.findByCodeIgnoreCase("TEN")).thenReturn(Optional.of(coupon));
        Map<String,Object> quote = service.quote(request);
        assertEquals(new BigDecimal("100.00"), quote.get("couponDiscount"));
        assertEquals(new BigDecimal("45.00"), quote.get("tierDiscount"));
        assertEquals(new BigDecimal("153.90"), quote.get("tax"));
        assertEquals(new BigDecimal("1008.90"), quote.get("total"));
        assertEquals(5, product.getStockQuantity()); assertEquals(0, coupon.getTimesUsed());
        verify(coupons, never()).save(any()); verify(products, never()).save(any());
        verifyNoInteractions(payments, notifications);
    }
    @Test void staleQuoteDoesNotDeductStock() {
        mockCatalog(); request.setExpectedTotal(new BigDecimal("1.00"));
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(request));
        assertEquals(5, product.getStockQuantity()); verify(products, never()).saveAndFlush(any());
    }
    @Test void emptyOrderRejected() {
        request.setItems(List.of()); assertThrows(IllegalArgumentException.class, () -> service.quote(request));
        verifyNoInteractions(products, customers);
    }
    @Test void duplicateLinesRejected() {
        request.setItems(List.of(request.getItems().get(0), request.getItems().get(0)));
        assertThrows(IllegalArgumentException.class, () -> service.quote(request));
    }
    @Test void onlinePaymentCannotBeSilentlyApproved() {
        request.setPaymentMethod("UPI");
        assertThrows(IllegalArgumentException.class, () -> service.quote(request));
        verifyNoInteractions(payments);
    }
    @Test void refundReversesPointsOnlyOnceAndDropsCancelledSpend() {
        customer.setLoyaltyPoints(20); customer.setTier("GOLD");
        Order order = new Order(); order.setId(1L); order.setCustomer(customer);
        order.setStatus(OrderStatus.CANCELLED); order.setMerchandiseTotal(new BigDecimal("1000"));
        order.setRewardsAwarded(true);
        when(orders.findByCustomerId(1L)).thenReturn(List.of(order));
        service.reverseRewards(order); service.reverseRewards(order);
        assertEquals(10, customer.getLoyaltyPoints()); assertEquals("BRONZE", customer.getTier());
        assertFalse(order.getRewardsAwarded());
    }
}
