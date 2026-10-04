package com.ecommerce.ecommerce_system.config;
import com.ecommerce.ecommerce_system.model.*;
import com.ecommerce.ecommerce_system.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class RewardReconciliationTest {
    @Test void onlyDeliveredUnrefundedSpendingEarnsRewardsAndRepairIsIdempotent() {
        CustomerRepository customers = mock(CustomerRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        Customer referrer = new Customer(); referrer.setId(1L);
        Customer buyer = new Customer(); buyer.setId(2L); buyer.setReferredBy(referrer); buyer.setLoyaltyPoints(9999); buyer.setTier("GOLD");
        Order delivered = new Order(); delivered.setCustomer(buyer); delivered.setStatus(OrderStatus.DELIVERED); delivered.setMerchandiseTotal(new BigDecimal("1000"));
        Order cancelled = new Order(); cancelled.setCustomer(buyer); cancelled.setStatus(OrderStatus.CANCELLED); cancelled.setMerchandiseTotal(new BigDecimal("200000"));
        when(customers.findAll()).thenReturn(List.of(referrer,buyer)); when(orders.findAll()).thenReturn(List.of(delivered,cancelled));
        RewardReconciliationRunner repair = new RewardReconciliationRunner(customers,orders);
        repair.run(); repair.run();
        assertEquals(10,buyer.getLoyaltyPoints()); assertEquals(500,referrer.getLoyaltyPoints());
        assertEquals("BRONZE",buyer.getTier()); assertTrue(buyer.getReferralRewarded());
        assertTrue(delivered.getRewardsAwarded()); assertFalse(cancelled.getRewardsAwarded());
    }
}
