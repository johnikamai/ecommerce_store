package com.ecommerce.order.controller;
@org.springframework.web.bind.annotation.RestController @org.springframework.web.bind.annotation.RequestMapping("/internal") public class InternalReadController {
 private final com.ecommerce.order.repo.OrderRepository orders;private final com.ecommerce.order.repo.AddressRepository addresses;
 public InternalReadController(com.ecommerce.order.repo.OrderRepository o,com.ecommerce.order.repo.AddressRepository a){orders=o;addresses=a;}
 @org.springframework.web.bind.annotation.GetMapping("/orders") public java.util.List<com.ecommerce.order.model.Order> orders(){return orders.findAll();}
 @org.springframework.web.bind.annotation.GetMapping("/addresses/customer/{id}") public Object addresses(@org.springframework.web.bind.annotation.PathVariable Long id){return addresses.findByCustomerId(id);}
}