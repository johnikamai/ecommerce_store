package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.*;
import com.ecommerce.ecommerce_system.repository.ApprovalRequestRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApprovalServiceTest {

    private ApprovalRequestRepository approvalRepository;
    private ProductRepository productRepository;
    private StaffNotificationService notificationService;
    private StaffActivityService activityService;
    private ApprovalService service;

    @BeforeEach
    void setUp() {
        approvalRepository = mock(ApprovalRequestRepository.class);
        productRepository = mock(ProductRepository.class);
        notificationService = mock(StaffNotificationService.class);
        activityService = mock(StaffActivityService.class);
        service = new ApprovalService(approvalRepository, productRepository, notificationService, activityService);
        when(approvalRepository.save(any(ApprovalRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Product product(String price) {
        Product p = new Product();
        p.setId(9L);
        p.setName("Test Product");
        p.setPrice(new BigDecimal(price));
        return p;
    }

    @Test
    void approvingPriceChangeAppliesNewPrice() {
        Product product = product("100.00");
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));

        ApprovalRequest request = new ApprovalRequest();
        request.setId(1L);
        request.setType(ApprovalType.PRICE_CHANGE);
        request.setStatus(ApprovalStatus.PENDING);
        request.setProductId(9L);
        request.setRequestedPrice(new BigDecimal("80.00"));
        when(approvalRepository.findById(1L)).thenReturn(Optional.of(request));

        ApprovalRequest result = service.decide(1L, true, "looks good", "admin");

        assertEquals(ApprovalStatus.APPROVED, result.getStatus());
        assertEquals(new BigDecimal("80.00"), product.getPrice());
        assertNotNull(result.getExecutedAt());
        verify(productRepository).save(product);
    }

    @Test
    void approvingDiscountAppliesPercentage() {
        Product product = product("100.00");
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));

        ApprovalRequest request = new ApprovalRequest();
        request.setId(2L);
        request.setType(ApprovalType.DISCOUNT);
        request.setStatus(ApprovalStatus.PENDING);
        request.setProductId(9L);
        request.setDiscountPercent(20);
        when(approvalRepository.findById(2L)).thenReturn(Optional.of(request));

        ApprovalRequest result = service.decide(2L, true, null, "admin");

        assertEquals(ApprovalStatus.APPROVED, result.getStatus());
        assertEquals(new BigDecimal("80.00"), product.getPrice());
    }

    @Test
    void rejectingDoesNotChangeProduct() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(3L);
        request.setType(ApprovalType.PRICE_CHANGE);
        request.setStatus(ApprovalStatus.PENDING);
        request.setProductId(9L);
        request.setRequestedPrice(new BigDecimal("50.00"));
        when(approvalRepository.findById(3L)).thenReturn(Optional.of(request));

        ApprovalRequest result = service.decide(3L, false, "too low", "admin");

        assertEquals(ApprovalStatus.REJECTED, result.getStatus());
        verify(productRepository, never()).save(any());
    }

    @Test
    void alreadyDecidedRequestIsRejectedWithConflict() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(4L);
        request.setStatus(ApprovalStatus.APPROVED);
        when(approvalRepository.findById(4L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.decide(4L, true, null, "admin"));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void approvingAlreadyExecutedRequestDoesNotDoubleApply() {
        Product product = product("100.00");
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));

        ApprovalRequest request = new ApprovalRequest();
        request.setId(5L);
        request.setType(ApprovalType.PRICE_CHANGE);
        request.setStatus(ApprovalStatus.PENDING);
        request.setProductId(9L);
        request.setRequestedPrice(new BigDecimal("80.00"));
        request.setExecutedAt(LocalDateTime.now());
        when(approvalRepository.findById(5L)).thenReturn(Optional.of(request));

        ApprovalRequest result = service.decide(5L, true, null, "admin");

        assertEquals(ApprovalStatus.APPROVED, result.getStatus());
        assertEquals(new BigDecimal("100.00"), product.getPrice());
        verify(productRepository, never()).save(any());
    }
}
