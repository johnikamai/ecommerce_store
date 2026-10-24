package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.repository.OrderItemRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import com.ecommerce.ecommerce_system.repository.ReviewRepository;
import com.ecommerce.ecommerce_system.service.GiftFinderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/gift-finder")
public class GiftFinderController {

    private final GiftFinderService giftFinderService;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;

    public GiftFinderController(GiftFinderService giftFinderService,
                                ProductRepository productRepository,
                                ReviewRepository reviewRepository,
                                OrderItemRepository orderItemRepository) {
        this.giftFinderService = giftFinderService;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @PostMapping("/suggest")
    public GiftFinderService.Result suggest(@RequestBody(required = false) GiftFinderService.GiftRequest request) {
        Map<Long, Double> ratings = new HashMap<>();
        for (Object[] row : reviewRepository.aggregateByProduct()) {
            ratings.put(((Number) row[0]).longValue(), ((Number) row[2]).doubleValue());
        }

        Map<Long, Long> unitsSold = new HashMap<>();
        for (Object[] row : orderItemRepository.unitsSoldByProduct()) {
            unitsSold.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        return giftFinderService.suggest(request, productRepository.findAll(), ratings, unitsSold);
    }
}
