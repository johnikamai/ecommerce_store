package com.ecommerce.ecommerce_system.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
  
    private Long id;

    private String name;

    private String description;

    private String category;

    private BigDecimal price;

    private Integer stockQuantity;

    // URL of the product photo. Optional; null means "no image yet"
    // (the front end shows a styled placeholder tile instead).
    private String imageUrl;

    // Eco-rating from 0 (not sustainable) to 100 (fully sustainable).
    // Optional field; null means not rated yet.
    private Integer sustainabilityScore;

    // Computed, NOT stored in the database. Provides a human-friendly badge
    // derived from the raw score whenever a Product is serialized to JSON.
    @Transient
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
}
