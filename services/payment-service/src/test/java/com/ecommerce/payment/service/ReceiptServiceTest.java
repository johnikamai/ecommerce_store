package com.ecommerce.payment.service;

import com.ecommerce.payment.model.Order;
import com.ecommerce.payment.model.OrderStatus;
import com.ecommerce.payment.model.Payment;
import com.ecommerce.payment.model.PaymentMode;
import com.ecommerce.payment.model.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceiptServiceTest {

    private final ReceiptService receiptService = new ReceiptService();

    @Test
    void cancelledPaymentHasNoOutstandingBalance() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.CANCELLED);
        order.setTotalAmount(new BigDecimal("100.00"));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setPaymentMode(PaymentMode.CASH);
        payment.setPaymentStatus(PaymentStatus.CANCELLED);

        var receipt = receiptService.build(payment);

        assertEquals(BigDecimal.ZERO, receipt.get("amountPaid"));
        assertEquals(BigDecimal.ZERO, receipt.get("balanceDue"));
    }
}