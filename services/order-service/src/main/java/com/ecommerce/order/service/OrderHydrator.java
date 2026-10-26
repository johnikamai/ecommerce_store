package com.ecommerce.order.service;
import com.ecommerce.order.model.*;
import com.ecommerce.order.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class OrderHydrator {
    private final CustomerRepository customers;private final ProductRepository products;private final OrderRepository orders;
    public OrderHydrator(CustomerRepository customers,ProductRepository products,OrderRepository orders){this.customers=customers;this.products=products;this.orders=orders;}
    @Transactional(readOnly=true)
    public Order load(Long id){var order=orders.findById(id).orElseThrow();order.setCustomer(customers.findById(order.getCustomerId()).orElseThrow());for(var line:order.getOrderItems())line.setProduct(products.findById(line.getProductId()).orElseThrow());order.getStatusEvents().size();return order;}
}
