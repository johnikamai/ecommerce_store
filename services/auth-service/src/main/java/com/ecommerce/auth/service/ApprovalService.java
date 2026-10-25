package com.ecommerce.auth.service;

import com.ecommerce.auth.model.*;
import com.ecommerce.auth.repo.ApprovalRequestRepository;
import com.ecommerce.auth.client.ProductClient;
import com.ecommerce.auth.client.ProductView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Sensitive price/discount changes require admin approval. The change is only
 * applied when the request is approved, inside the same transaction, and is
 * idempotent (re-approving an already executed request never double-applies).
 */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository approvalRepository;
    private final ProductClient productRepository;
    private final StaffNotificationService notificationService;
    private final StaffActivityService activityService;

    public ApprovalService(ApprovalRequestRepository approvalRepository,
                           ProductClient productRepository,
                           StaffNotificationService notificationService,
                           StaffActivityService activityService) {
        this.approvalRepository = approvalRepository;
        this.productRepository = productRepository;
        this.notificationService = notificationService;
        this.activityService = activityService;
    }

    @Transactional
    public ApprovalRequest create(ApprovalRequestDto dto, User requestedBy) {
        if (dto.getType() == null) {
            throw new IllegalArgumentException("Approval type is required");
        }
        if (dto.getProductId() == null) {
            throw new IllegalArgumentException("A product must be selected");
        }
        ProductView product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + dto.getProductId()));

        if (dto.getType() == ApprovalType.PRICE_CHANGE
                && (dto.getRequestedPrice() == null || dto.getRequestedPrice().signum() <= 0)) {
            throw new IllegalArgumentException("A positive new price is required");
        }
        if (dto.getType() == ApprovalType.DISCOUNT
                && (dto.getDiscountPercent() == null || dto.getDiscountPercent() <= 0 || dto.getDiscountPercent() >= 100)) {
            throw new IllegalArgumentException("Discount must be between 1 and 99 percent");
        }

        ApprovalRequest request = new ApprovalRequest();
        request.setType(dto.getType());
        request.setStatus(ApprovalStatus.PENDING);
        request.setRequestedBy(requestedBy);
        request.setRequestedByUsername(requestedBy.getUsername());
        request.setProductId(product.getId());
        request.setProductName(product.getName());
        request.setPreviousPrice(product.getPrice());
        request.setRequestedPrice(dto.getRequestedPrice());
        request.setDiscountPercent(dto.getDiscountPercent());
        request.setReason(dto.getReason());
        request.setCreatedAt(LocalDateTime.now());
        ApprovalRequest saved = approvalRepository.save(request);

        activityService.record(requestedBy.getUsername(), "APPROVAL_REQUESTED", "APPROVAL", saved.getId(),
                dto.getType() + " for product " + product.getId());
        return saved;
    }

    public List<ApprovalRequest> list(ApprovalStatus status) {
        return status == null ? approvalRepository.findAllByOrderByCreatedAtDesc()
                : approvalRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public List<ApprovalRequest> listForUser(Long userId) {
        return approvalRepository.findByRequestedByIdOrderByCreatedAtDesc(userId);
    }

    public ApprovalRequest get(Long id) {
        return approvalRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Approval request not found: " + id));
    }

    public Map<String, Object> summary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pending", approvalRepository.countByStatus(ApprovalStatus.PENDING));
        map.put("approved", approvalRepository.countByStatus(ApprovalStatus.APPROVED));
        map.put("rejected", approvalRepository.countByStatus(ApprovalStatus.REJECTED));
        return map;
    }

    @Transactional
    public ApprovalRequest decide(Long id, boolean approve, String reason, String adminUsername) {
        ApprovalRequest request = get(id);
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request has already been decided");
        }

        request.setDecidedBy(adminUsername);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecisionReason(reason);

        if (approve) {
            executeChange(request);
            request.setStatus(ApprovalStatus.APPROVED);
        } else {
            request.setStatus(ApprovalStatus.REJECTED);
        }
        ApprovalRequest saved = approvalRepository.save(request);

        activityService.record(adminUsername, approve ? "APPROVAL_APPROVED" : "APPROVAL_REJECTED",
                "APPROVAL", saved.getId(), request.getType() + " for product " + request.getProductId());
        if (request.getRequestedBy() != null) {
            notificationService.notify(request.getRequestedBy(),
                    (approve ? "Approved: " : "Rejected: ") + request.getType()
                            + " for " + request.getProductName()
                            + (reason != null && !reason.isBlank() ? " (" + reason + ")" : ""),
                    "APPROVAL");
        }
        return saved;
    }

    private void executeChange(ApprovalRequest request) {
        if (request.getExecutedAt() != null) {
            return; // already applied - never double-apply
        }
        ProductView product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + request.getProductId()));
        if (request.getType() == ApprovalType.PRICE_CHANGE) {
            product.setPrice(request.getRequestedPrice());
        } else if (request.getType() == ApprovalType.DISCOUNT) {
            product.setPrice(discountedPrice(product.getPrice(), request.getDiscountPercent()));
        }
        productRepository.applyPrice(request.getId(), product, request.getPreviousPrice());
        request.setExecutedAt(LocalDateTime.now());
    }

    private BigDecimal discountedPrice(BigDecimal price, int percent) {
        return price.multiply(BigDecimal.valueOf(100 - percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
