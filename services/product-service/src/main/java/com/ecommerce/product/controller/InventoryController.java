package com.ecommerce.product.controller;
import com.ecommerce.product.service.InventoryService;
import com.ecommerce.product.repo.ProductRepository;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/internal")
public class InventoryController {
    private final InventoryService inventory;private final ProductRepository products;
    public InventoryController(InventoryService inventory,ProductRepository products){this.inventory=inventory;this.products=products;}
    public record Adjustment(String operationId,Long productId,int delta){}
    @PostMapping("/inventory/adjust")public Object adjust(@RequestBody Adjustment body){return inventory.adjust(body.operationId(),body.productId(),body.delta());}
    @PostMapping("/inventory/reservations")public Object reserve(@RequestBody InventoryService.ReserveRequest body){return inventory.reserve(body);}
    @PostMapping("/inventory/reservations/{id}/commit")public Object commit(@PathVariable String id){return inventory.commit(id);}
    @PostMapping("/inventory/reservations/{id}/release")public Object release(@PathVariable String id){return inventory.release(id);}
    @PutMapping("/products/{id}/price")public Object price(@PathVariable Long id,@RequestBody InventoryService.PriceRequest body){return inventory.applyPrice(id,body);}
    @PostMapping("/products/batch")public Object batch(@RequestBody List<Long> ids){if(ids.size()>1000)throw new IllegalArgumentException("At most 1000 products");return products.findAllById(ids);}
}
