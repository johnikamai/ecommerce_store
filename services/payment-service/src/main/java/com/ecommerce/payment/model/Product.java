package com.ecommerce.payment.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;



@JsonIgnoreProperties(ignoreUnknown=true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    /** Threshold used when a product has no explicit reorder level set. */
    public static final int DEFAULT_REORDER_LEVEL = 10;

    
    
    private Long id;

    
    
    private long version;

    private String name;

    private String description;

    private String category;

    private BigDecimal price;

    private Integer stockQuantity;

    // Stock level at or below which this product is flagged as low stock.
    // Product.stockQuantity is the single source of truth for availability;
    // this only controls *when* the warning fires.
    private Integer reorderLevel = DEFAULT_REORDER_LEVEL;

    // URL of the product photo. Optional; null means "no image yet"
    // (the front end shows a styled placeholder tile instead).
    private String imageUrl;

    // Brand name (optional). When set, the compare table shows it.
    private String brand;

    // Free-form specifications text (optional). When set, the compare table shows it.
    
    private String specifications;

    // Eco-rating from 0 (not sustainable) to 100 (fully sustainable).
    // Optional field; null means not rated yet.
    private Integer sustainabilityScore;

    // Computed, NOT stored in the database. Provides a human-friendly badge
    // derived from the raw score whenever a Product is serialized to JSON.
    
    public String getSustainabilityLabel() {
        if (sustainabilityScore == null) {
            return "Not Rated";
        }
        if (sustainabilityScore >= 80) {
            return "Eco-Friendly";
        } else if (sustainabilityScore >= 50) {
            return "Moderate Impact";
        } else {
            return "High Impact";
        }
    }

    /** Effective reorder level, falling back to the default when unset. */
    
    public int getEffectiveReorderLevel() {
        return reorderLevel == null ? DEFAULT_REORDER_LEVEL : reorderLevel;
    }

    
    public boolean isLowStock() {
        return stockQuantity != null && stockQuantity <= getEffectiveReorderLevel();
    }

    
    public boolean isOutOfStock() {
        return stockQuantity == null || stockQuantity <= 0;
    }
}
