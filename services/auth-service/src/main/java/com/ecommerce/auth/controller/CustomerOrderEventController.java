package com.ecommerce.auth.controller;
import com.ecommerce.auth.service.CustomerOrderService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
@RestController
public class CustomerOrderEventController {
    private final CustomerOrderService orders;
    public CustomerOrderEventController(CustomerOrderService orders){this.orders=orders;}
    @PostMapping("/internal/customer-order-events")public void receive(@RequestBody JsonNode event){if(!event.path("kind").asText().equals("ORDER_UPDATED"))throw new IllegalArgumentException("Unknown customer order event");orders.receive(event.path("payload"));}
}
