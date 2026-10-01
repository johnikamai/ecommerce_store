package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.model.RestockRequest;
import com.ecommerce.ecommerce_system.repository.RestockRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RestockService {

    @Autowired
    private RestockRequestRepository restockRequestRepository;

    @Autowired
    private NotificationService notificationService;

    /**
     * Called whenever a product's stock changes.
     * If the product was previously OUT of stock but is now available,
     * notify every customer who subscribed, then mark their request as notified.
     */
    @Transactional
    public void checkRestock(Product product) {
        Integer stock = product.getStockQuantity();
        if (stock == null || stock <= 0) {
            return; // still out of stock (or zero) -> nothing to do
        }

        // Only customers who subscribed AND haven't been notified yet.
        List<RestockRequest> subscribers =
                restockRequestRepository.findByProductIdAndNotifiedFalse(product.getId());

        for (RestockRequest req : subscribers) {
            if (req.getNotified() != null && req.getNotified()) {
                continue; // safety: skip already-notified
            }

            // Create the in-app notification AND send the restock email.
            notificationService.restocked(req.getCustomer(), product.getName());

            // Mark the subscription as handled so we never notify twice.
            req.setNotified(true);
            restockRequestRepository.save(req);
        }
    }
}
