package com.ecommerce.product.model;

import lombok.Data;

import java.math.BigDecimal;

/** Staff request for a sensitive change requiring admin approval. */
@Data
public class ApprovalRequestDto {
    private ApprovalType type;
    private Long productId;
    private BigDecimal requestedPrice;
    private Integer discountPercent;
    private String reason;
}
