package com.ecommerce.ecommerce_system.config;

import com.ecommerce.ecommerce_system.model.*;
import com.ecommerce.ecommerce_system.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** One-time, opt-in repair of rewards that the legacy checkout granted before delivery. */
@Component
@ConditionalOnProperty(name = "app.rewards.reconcile", havingValue = "true")
public class RewardReconciliationRunner implements CommandLineRunner {
    private final CustomerRepository customers;
    private final OrderRepository orders;
    public RewardReconciliationRunner(CustomerRepository customers, OrderRepository orders) {
        this.customers = customers; this.orders = orders;
    }
    @Override @Transactional
    public void run(String... args) {
        List<Customer> profiles = customers.findAll();
        List<Order> history = orders.findAll();
        Map<Long, Integer> points = new HashMap<>();
        Map<Long, BigDecimal> spending = new HashMap<>();
        Set<Long> completed = new HashSet<>();
        for (Order order : history) {
            boolean earned = order.getStatus() == OrderStatus.DELIVERED && order.getPaymentStatus() != PaymentStatus.REFUNDED;
            order.setRewardsAwarded(earned);
            if (!earned || order.getCustomer() == null) continue;
            Long id = order.getCustomer().getId();
            BigDecimal goods = order.getMerchandiseTotalForScoring();
            points.merge(id, goods.divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).intValue(), Integer::sum);
            spending.merge(id, goods, BigDecimal::add); completed.add(id);
        }
        for (Customer customer : profiles) {
            boolean referredPurchase = completed.contains(customer.getId()) && customer.getReferredBy() != null;
            customer.setReferralRewarded(referredPurchase);
            if (referredPurchase) points.merge(customer.getReferredBy().getId(), 500, Integer::sum);
        }
        for (Customer customer : profiles) {
            customer.setLoyaltyPoints(points.getOrDefault(customer.getId(), 0));
            BigDecimal spend = spending.getOrDefault(customer.getId(), BigDecimal.ZERO);
            customer.setTier(spend.compareTo(BigDecimal.valueOf(150000)) >= 0 ? "GOLD" : spend.compareTo(BigDecimal.valueOf(50000)) >= 0 ? "SILVER" : "BRONZE");
        }
        orders.saveAll(history); customers.saveAll(profiles);
        System.out.println("Reward reconciliation completed for " + profiles.size() + " customer profiles.");
    }
}
