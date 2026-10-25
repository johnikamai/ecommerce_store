package com.ecommerce.order.controller;
import com.ecommerce.order.repo.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
/** Restores the explicitly requested admin deletion contract. InternalServiceFilter
 * requires X-Service-Key; Gateway exposes no /internal routes. Never deletes orders. */
@RestController @RequestMapping("/internal/customers")
public class CustomerCleanupController {
    private final OrderRepository orders; private final GroupOrderRepository groups;
    private final AddressRepository addresses; private final WishlistRepository wishlists;
    public CustomerCleanupController(OrderRepository o,GroupOrderRepository g,AddressRepository a,WishlistRepository w){orders=o;groups=g;addresses=a;wishlists=w;}
    @GetMapping("/{id}/deletion-check")
    public Map<String,Boolean> check(@PathVariable Long id){
        if(!orders.findByCustomerId(id).isEmpty()||!groups.findByHostId(id).isEmpty()||!groups.findByMemberCustomerId(id).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Cannot delete a customer with order or group history; deactivate instead");
        return Map.of("eligible",true);
    }
    @PostMapping("/{id}/cleanup") @Transactional
    public Map<String,Boolean> cleanup(@PathVariable Long id){check(id);addresses.deleteByCustomerId(id);wishlists.deleteByCustomerId(id);return Map.of("cleaned",true);}
}
