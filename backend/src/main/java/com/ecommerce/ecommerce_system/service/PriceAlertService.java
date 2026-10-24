package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.PriceHistory;
import com.ecommerce.ecommerce_system.repository.CustomerRepository;
import com.ecommerce.ecommerce_system.repository.PriceHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * F7 + F15: Price history recording and wishlist price-drop alerts.
 *
 * recordPriceChange() is called from ProductController.update() whenever
 * the admin saves a new price.  The nightly @Scheduled job scans every
 * wishlist item whose price has fallen since the user saved it and sends
 * an email notification via MailService.
 */
@Service
public class PriceAlertService {

    private static final Logger log = LoggerFactory.getLogger(PriceAlertService.class);

    private final PriceHistoryRepository priceHistoryRepository;
    private final CustomerRepository     customerRepository;
    private final MailService            mailService;

    public PriceAlertService(PriceHistoryRepository priceHistoryRepository,
                             CustomerRepository     customerRepository,
                             MailService            mailService) {
        this.priceHistoryRepository = priceHistoryRepository;
        this.customerRepository     = customerRepository;
        this.mailService            = mailService;
    }

    /**
     * Called whenever a product price changes. Writes an immutable audit row
     * unless the same price was already recorded within the last 5 minutes
     * (prevents duplicate inserts from rapid admin saves).
     */
    public void recordPriceChange(Long productId, BigDecimal oldPrice, BigDecimal newPrice) {
        if (newPrice == null) return;
        if (oldPrice != null && oldPrice.compareTo(newPrice) == 0) return; // no change

        // Deduplicate: skip if same price was written very recently.
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        if (priceHistoryRepository.existsRecent(productId, newPrice, fiveMinutesAgo)) {
            return;
        }

        String reason = oldPrice == null ? "Initial price"
                : newPrice.compareTo(oldPrice) < 0 ? "Price drop" : "Price increase";

        priceHistoryRepository.save(new PriceHistory(productId, oldPrice, newPrice, reason));
        log.debug("Recorded price change for product {}: {} -> {}", productId, oldPrice, newPrice);
    }

    /**
     * Nightly job: finds wishlist items whose price has dropped since the user
     * added the item and sends each customer one email listing all affected
     * products.  Runs at 08:00 every morning (IST-friendly).
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendPriceDropAlerts() {
        List<Object[]> drops = priceHistoryRepository.findWishlistPriceDrops();
        if (drops.isEmpty()) {
            log.debug("Price-drop alert job: no drops found.");
            return;
        }
        log.info("Price-drop alert job: {} wishlist drop(s) found.", drops.size());

        for (Object[] row : drops) {
            Long   customerId   = ((Number) row[0]).longValue();
            Long   productId    = ((Number) row[1]).longValue();
            BigDecimal savedPrice   = (BigDecimal) row[2];
            BigDecimal currentPrice = (BigDecimal) row[3];
            String productName  = (String) row[4];

            customerRepository.findById(customerId).ifPresent(customer -> {
                BigDecimal saving = savedPrice.subtract(currentPrice);
                String html = buildPriceDropEmail(customer.getName(), productName,
                                                  savedPrice, currentPrice, saving, productId);
                mailService.sendHtmlAsync(
                    customer.getEmail(),
                    "\uD83C\uDF89 Price drop on " + productName + " — ShopEase",
                    html
                );
                log.info("Sent price-drop alert to customer {} for product {}", customerId, productId);
            });
        }
    }

    private String buildPriceDropEmail(String name, String productName,
                                       BigDecimal savedPrice, BigDecimal currentPrice,
                                       BigDecimal saving, Long productId) {
        return "<div style='font-family:Arial,sans-serif;max-width:520px;margin:auto'>"
             + "<h2 style='color:#6C2BD9'>ShopEase Price Alert \uD83D\uDCE2</h2>"
             + "<p>Hi " + name + ",</p>"
             + "<p>Good news! A product on your wishlist just got cheaper:</p>"
             + "<div style='background:#f3f0ff;border-left:4px solid #6C2BD9;padding:16px;border-radius:8px'>"
             + "<strong>" + productName + "</strong><br/>"
             + "<span style='text-decoration:line-through;color:#999'>\u20B9" + savedPrice + "</span>&nbsp;"
             + "<span style='font-size:1.3em;color:#16a34a;font-weight:bold'>\u20B9" + currentPrice + "</span><br/>"
             + "<span style='color:#16a34a'>You save \u20B9" + saving + "!</span>"
             + "</div>"
             + "<p><a href='http://localhost:5173/product/" + productId + "' "
             + "style='background:#6C2BD9;color:#fff;padding:10px 20px;border-radius:6px;text-decoration:none'>"
             + "View Product</a></p>"
             + "<p style='color:#888;font-size:0.85em'>You are receiving this because it is on your ShopEase wishlist.</p>"
             + "</div>";
    }
}
