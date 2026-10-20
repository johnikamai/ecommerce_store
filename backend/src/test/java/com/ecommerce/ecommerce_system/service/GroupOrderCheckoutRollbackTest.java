package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Customer;
import com.ecommerce.ecommerce_system.model.GroupOrderStatus;
import com.ecommerce.ecommerce_system.model.GroupPaymentMode;
import com.ecommerce.ecommerce_system.model.GroupSplitMethod;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.model.Role;
import com.ecommerce.ecommerce_system.model.User;
import com.ecommerce.ecommerce_system.repository.CustomerRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import com.ecommerce.ecommerce_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: a price change found at checkout must be recorded even though checkout
 * then refuses to proceed.
 *
 * The refusal throws, which rolls back the checkout transaction. If the snapshot
 * rewrite happened inside that same transaction it would be undone with it, so every
 * later attempt would re-see the same price gap, recompute, throw again — and the
 * group could never be placed. The rewrite now commits in its own transaction, so
 * this test asserts both that it survives the rollback and that checkout becomes
 * possible again once members reconfirm.
 */
@SpringBootTest
class GroupOrderCheckoutRollbackTest {

    @Autowired private GroupOrderService groupOrders;
    @Autowired private CustomerRepository customers;
    @Autowired private UserRepository users;
    @Autowired private ProductRepository products;

    private Authentication hostAuth;
    private Authentication friendAuth;
    private Long productId;

    @BeforeEach
    void createFixture() {
        hostAuth = register("rollback-host", "Rollback Host");
        friendAuth = register("rollback-friend", "Rollback Friend");

        Product product = new Product();
        product.setName("Rollback Widget");
        product.setCategory("test");
        product.setDescription("Fixture for the checkout rollback regression");
        product.setPrice(new BigDecimal("100.00"));
        product.setStockQuantity(10);
        productId = products.save(product).getId();
    }

    @Test
    void priceChangeSurvivesTheReconfirmRefusalAndCheckoutStaysPossible() {
        Map<String, Object> created = groupOrders.create(hostAuth, "Rollback group",
                "12 Test Street, Hyderabad 500001", null, null,
                GroupSplitMethod.EQUAL, GroupPaymentMode.HOST_PAYS);
        Long groupId = (Long) created.get("id");

        groupOrders.join(friendAuth, (String) created.get("inviteCode"));
        groupOrders.addItem(hostAuth, groupId, productId, 1);
        groupOrders.lock(hostAuth, groupId);
        groupOrders.confirmShare(hostAuth, groupId);
        groupOrders.confirmShare(friendAuth, groupId);

        int versionBefore = (int) groupOrders.detail(hostAuth, groupId).get("splitVersion");

        // The store repriced the product after everyone had already confirmed.
        Product product = products.findById(productId).orElseThrow();
        product.setPrice(new BigDecimal("150.00"));
        products.save(product);

        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> groupOrders.checkout(hostAuth, groupId, "CASH"));
        assertTrue(refusal.getMessage().contains("Prices changed"),
                "expected the reconfirm prompt, got: " + refusal.getMessage());

        // The rollback of checkout must NOT have taken the correction with it.
        Map<String, Object> afterRefusal = groupOrders.detail(hostAuth, groupId);
        List<?> items = (List<?>) afterRefusal.get("items");
        assertEquals(1, items.size());
        Map<?, ?> firstItem = (Map<?, ?>) items.get(0);
        assertEquals(0, ((BigDecimal) firstItem.get("unitPrice")).compareTo(new BigDecimal("150.00")),
                "the corrected price snapshot must survive the refusal's rollback");
        assertTrue((int) afterRefusal.get("splitVersion") > versionBefore,
                "the split version must bump so stale confirmations are rejected");
        assertFalse((boolean) afterRefusal.get("allConfirmed"),
                "everyone's confirmation must be reset at the new version");

        // Reconfirm and place — without the fix this throws the same refusal forever.
        groupOrders.confirmShare(hostAuth, groupId);
        groupOrders.confirmShare(friendAuth, groupId);

        Map<String, Object> placed = groupOrders.checkout(hostAuth, groupId, "CASH");
        assertNotNull(placed.get("placedOrderId"), "checkout must proceed after a reconfirm");
        assertEquals(GroupOrderStatus.CONFIRMED.name(), placed.get("status"));
    }

    private Authentication register(String username, String name) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setEmail(username + "@example.com");
        customer.setPhone("9000000000");
        customer.setShippingAddress("12 Test Street, Hyderabad 500001");
        customers.save(customer);

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword("not-a-real-password");
        user.setRole(Role.CUSTOMER);
        user.setEnabled(true);
        user.setCustomerId(customer.getId());
        users.save(user);

        return new TestingAuthenticationToken(username, null);
    }
}
