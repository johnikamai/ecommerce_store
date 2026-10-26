package com.ecommerce.payment.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;



@JsonIgnoreProperties(ignoreUnknown=true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    
    
    private Long id;

    
    
    @JsonBackReference
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private Order order;

    
    
    private Product product;

    private Integer quantity;

    private BigDecimal unitPrice;

    // Sub-total captured at order time (unitPrice x quantity). Stored rather than
    // recomputed so a line's amount can never drift if the product price changes.
    private BigDecimal lineTotal;
}

