package com.ecommerce.order.service;

import com.ecommerce.order.model.*;
import com.ecommerce.order.repo.CustomerRepository;
import com.ecommerce.order.repo.OrderItemRepository;
import com.ecommerce.order.repo.OrderRepository;
import com.ecommerce.order.repo.PaymentRepository;
import com.ecommerce.order.repo.ProductRepository;
import com.ecommerce.order.repo.ReturnRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ReturnService {

    private final OrderService orderService;
    private final ReturnRequestRepository returnRequestRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final com.ecommerce.order.client.InventoryClient inventory;
    private final OperationJournal journal;
    private final NotificationService notificationService;

    public ReturnService(OrderService orderService,
                         ReturnRequestRepository returnRequestRepository,
                         OrderItemRepository orderItemRepository,
                         CustomerRepository customerRepository,
                         ProductRepository productRepository,
                         OrderRepository orderRepository,
                         PaymentRepository paymentRepository,
                         com.ecommerce.order.client.InventoryClient inventory, OperationJournal journal,
                         NotificationService notificationService) {
        this.orderService = orderService;
        this.returnRequestRepository = returnRequestRepository;
        this.orderItemRepository = orderItemRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.inventory=inventory;this.journal=journal;
        this.notificationService = notificationService;
    }

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

        String operation="return-process:"+returnRequestId;
        if((approve&&req.getStatus()==ReturnStatus.APPROVED)||(!approve&&req.getStatus()==ReturnStatus.REJECTED)){journal.afterCommit(operation);return req;}
        if (req.getStatus() != ReturnStatus.REQUESTED) {
            throw new IllegalStateException("Return already processed (status: " + req.getStatus() + ")");
        }

        journal.begin(operation,"RETURN_PROCESS",java.util.Map.of("returnId",returnRequestId,"approve",approve));
        req.setProcessedDate(LocalDateTime.now());

        if (approve) {
            req.setStatus(ReturnStatus.APPROVED);

            inventory.adjust(req.getOrderItem().getProductId(),req.getOrderItem().getQuantity(),"return-stock:"+req.getOrderItem().getId());
        } else {
            req.setStatus(ReturnStatus.REJECTED);
        }

        journal.afterCommit(operation);
        return returnRequestRepository.save(req);
    }

    /**
     * Final step: admin confirms the money was returned to the customer.
     */
    @Transactional
    public ReturnRequest markRefunded(Long returnRequestId) {
        ReturnRequest req = returnRequestRepository.findById(returnRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Return request not found: " + returnRequestId));

        String operation="return-refund:"+returnRequestId;
        if(req.getStatus()==ReturnStatus.REFUNDED){journal.afterCommit(operation);return req;}
        if (req.getStatus() != ReturnStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED returns can be refunded (current: " + req.getStatus() + ")");
        }

        journal.begin(operation,"RETURN_REFUND",java.util.Map.of("returnId",returnRequestId));
        req.setStatus(ReturnStatus.REFUNDED);

        // Notify the customer that their refund has been processed.
        Order order = req.getOrderItem().getOrder();
        String productName = productRepository.findById(req.getOrderItem().getProductId()).map(Product::getName).orElse("Returned item");
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

        journal.afterCommit(operation);
        return returnRequestRepository.save(req);
    }
}
