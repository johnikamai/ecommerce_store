package com.ecommerce.product.controller;
import com.ecommerce.product.repo.ProductRepository;
import com.ecommerce.product.service.CompatibilityService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/compatibility")
public class CompatibilityController {
    private final ProductRepository products;private final CompatibilityService compatibility;
    public CompatibilityController(ProductRepository products,CompatibilityService compatibility){this.products=products;this.compatibility=compatibility;}
    @GetMapping public Object check(@RequestParam Long productA,@RequestParam Long productB){return compatibility.check(products.findById(productA).orElseThrow(),products.findById(productB).orElseThrow());}
}
