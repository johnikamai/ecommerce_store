package com.ecommerce.auth.client;

import lombok.Data;
import java.math.BigDecimal;

/** Product data transferred over HTTP; deliberately not a JPA entity. */
@Data
public class ProductView {
    private Long id;
    private String name;
    private BigDecimal price;
}
