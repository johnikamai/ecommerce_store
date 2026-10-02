package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.AdminAlert;
import com.ecommerce.ecommerce_system.model.Customer;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.model.Wishlist;
import com.ecommerce.ecommerce_system.repository.AdminAlertRepository;
import com.ecommerce.ecommerce_system.repository.CustomerRepository;
import com.ecommerce.ecommerce_system.repository.WishlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a stock change into alerts for both audiences:
 *
 *   store staff  -> AdminAlert   (low stock / out of stock / restocked)
 *   customers    -> Notification (wishlist holders warned, restock subscribers told)
 *
 * Every caller funnels through {@link #onStockChanged} so the alerts stay correct
 * no matter which code path moved the stock.
 */
@Service
public class StockAlertService {

    @Autowired
    private AdminAlertRepository adminAlertRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RestockService restockService;

    /**
     * Fires after a product's stock has been persisted.
     *
     * Joins the caller's transaction on purpose: if the surrounding operation
     * rolls back (a failed order, say) the alerts roll back with it, so we can
     * never email "back in stock" for stock that was never actually returned.
     *
     * @param product       the product with its new stock level already saved
     * @param previousStock the stock level before the change (null if unknown)
     */
    @Transactional
    public void onStockChanged(Product product, Integer previousStock) {
        int now = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
        int before = previousStock == null ? now : previousStock;
        int level = product.getEffectiveReorderLevel();

        if (now == before) {
            return;
        }

        // ---- store staff -------------------------------------------------
        if (now <= 0 && before > 0) {
            recordAdminAlert(product, AdminAlert.OUT_OF_STOCK,
                    "'" + product.getName() + "' is now out of stock.", now, level);
        } else if (now > 0 && before <= 0) {
            recordAdminAlert(product, AdminAlert.RESTOCKED,
                    "'" + product.getName() + "' is back in stock (" + now + " units).", now, level);
        } else if (now <= level && before > level) {
            recordAdminAlert(product, AdminAlert.LOW_STOCK,
                    "'" + product.getName() + "' is low on stock — " + now + " left (reorder at " + level + ").", now, level);
        }

        // ---- customers ---------------------------------------------------
        if (now <= level && before > level) {
            warnWishlistHolders(product, now, level);
        }

        // Wishlist holders care specifically about a total sell-out.
        if (now <= 0 && before > 0) {
            warnWishlistHoldersOutOfStock(product);
        }

        // "Notify me when in stock" subscribers, fired on every rise above zero.
        if (now > 0 && before <= 0) {
            restockService.checkRestock(product);
        }
    }

    private void recordAdminAlert(Product product, String type, String message, int stock, int level) {
        // Don't pile a fresh alert on top of an unread one of the same kind.
        if (adminAlertRepository.existsUnread(product.getId(), type)) {
            return;
        }
        AdminAlert alert = new AdminAlert();
        alert.setProductId(product.getId());
        alert.setProductName(product.getName());
        alert.setType(type);
        alert.setMessage(message);
        alert.setStockAtAlert(stock);
        alert.setReorderLevel(level);
        adminAlertRepository.save(alert);

        // Also email the store's admin address, if one is configured.
        notificationService.alertAdminLowStock(product.getName(), type, stock, level);
    }

    /** Wishlist holders get an early warning before a product sells out. */
    private void warnWishlistHolders(Product product, int stock, int level) {
        notifyWishlistHolders(product, "LOW_STOCK",
                "Only " + stock + " left of '" + product.getName() + "' — it's in your wishlist.",
                "Low stock: " + product.getName(),
                "You saved <b>" + product.getName() + "</b> to your wishlist and only <b>" + stock
                        + "</b> remain in stock. Grab it before it's gone.");
    }

    private void warnWishlistHoldersOutOfStock(Product product) {
        notifyWishlistHolders(product, "RESTOCK",
                "'" + product.getName() + "' just sold out. You can switch on a restock alert.",
                "Sold out: " + product.getName(),
                "<b>" + product.getName() + "</b> (in your wishlist) has just sold out.<br/><br/>"
                        + "Tap \"Notify me when in stock\" on the product page and we'll email you the moment it returns.");
    }

    private void notifyWishlistHolders(Product product, String type, String message, String subject, String html) {
        List<Wishlist> wishlisted = wishlistRepository.findByProductId(product.getId());
        if (wishlisted.isEmpty()) {
            return;
        }
        // One customer can only have a product on their wishlist once, but guard anyway.
        Set<Long> seen = new HashSet<>();
        for (Wishlist w : wishlisted) {
            Customer customer = w.getCustomer();
            if (customer == null || !seen.add(customer.getId())) {
                continue;
            }
            notificationService.notify(customer, type, message, subject, html);
        }
    }
}