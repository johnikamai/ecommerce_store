package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.*;
import com.ecommerce.ecommerce_system.repository.CustomerRepository;
import com.ecommerce.ecommerce_system.repository.OrderItemRepository;
import com.ecommerce.ecommerce_system.repository.OrderRepository;
import com.ecommerce.ecommerce_system.repository.PaymentRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import com.ecommerce.ecommerce_system.repository.ReturnRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.time.LocalDateTime;

@Service
public class ReturnService {

    @Autowired private OrderService orderService;

    @Autowired
    private ReturnRequestRepository returnRequestRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private StockAlertService stockAlertService;

    @Autowired
    private NotificationService notificationService;

    /**
     * Customer requests a return on one line item.
     * Only items inside a DELIVERED order can be returned.
     */
    @Transactional
    public ReturnRequest requestReturn(Long orderItemId, Long customerId, String reason) {
        OrderItem orderItem = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found: " + orderItemId));

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        if (!orderItem.getOrder().getCustomer().getId().equals(customerId)) throw new IllegalArgumentException("This item does not belong to the customer");
        // Business rule: you can only return something that has been delivered.
        Order order = orderItem.getOrder();
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Return not allowed: order item is not in DELIVERED state (current: " + order.getStatus() + ")");
        }

        ReturnRequest returnRequest = new ReturnRequest();
        returnRequest.setOrderItem(orderItem);
        returnRequest.setCustomer(customer);
        returnRequest.setReason(reason);
        returnRequest.setStatus(ReturnStatus.REQUESTED);
        returnRequest.setRequestDate(LocalDateTime.now());

        return returnRequestRepository.save(returnRequest);
    }

    /**
     * Admin approves or rejects a pending return request.
     * On APPROVE: restore the product's stock, then fire restock notifications.
     */
    @Transactional
    public ReturnRequest process(Long returnRequestId, boolean approve) {
        ReturnRequest req = returnRequestRepository.findById(returnRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Return request not found: " + returnRequestId));

        if (req.getStatus() != ReturnStatus.REQUESTED) {
            throw new IllegalStateException("Return already processed (status: " + req.getStatus() + ")");
        }

        req.setProcessedDate(LocalDateTime.now());

        if (approve) {
            req.setStatus(ReturnStatus.APPROVED);

            // Restore the exact quantity back into stock.
            Product product = req.getOrderItem().getProduct();
            Integer quantity = req.getOrderItem().getQuantity();
            Integer stockBefore = product.getStockQuantity();
            int newStock = (stockBefore == null ? 0 : stockBefore) + quantity;
            product.setStockQuantity(newStock);
            Product savedProduct = productRepository.save(product);

            // Same alert pipeline as every other stock change: staff, wishlist
            // holders and restock subscribers all get told.
            stockAlertService.onStockChanged(savedProduct, stockBefore);
        } else {
            req.setStatus(ReturnStatus.REJECTED);
        }

        return returnRequestRepository.save(req);
    }

    /**
     * Final step: admin confirms the money was returned to the customer.
     */
    @Transactional
    public ReturnRequest markRefunded(Long returnRequestId) {
        ReturnRequest req = returnRequestRepository.findById(returnRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Return request not found: " + returnRequestId));

        if (req.getStatus() != ReturnStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED returns can be refunded (current: " + req.getStatus() + ")");
        }

        req.setStatus(ReturnStatus.REFUNDED);

        // Notify the customer that their refund has been processed.
        Order order = req.getOrderItem().getOrder();
        String productName = req.getOrderItem().getProduct().getName();
        notificationService.refunded(req.getCustomer(), req.getId(), productName);

        // Refund is money leaving the business, so it has to move on the payment
        // record as well - otherwise the payments ledger still reports this order
        // as PAID and revenue counts refunded money.
        PaymentStatus previous = order.getPaymentStatus();
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        orderRepository.save(order);
        orderService.reverseRewards(order);

        if (previous == PaymentStatus.PAID) {
            paymentRepository.findByOrderId(order.getId()).ifPresent(payment -> {
                payment.setPaymentStatus(PaymentStatus.REFUNDED);
                payment.setStatusUpdatedAt(LocalDateTime.now());
                payment.setStatusUpdatedBy("return:" + req.getId());
                paymentRepository.save(payment);
            });
        }

        return returnRequestRepository.save(req);
    }
}
