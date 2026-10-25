package com.ecommerce.product.model;
import lombok.Data;
/** Auth identity projection; no JPA relationship across services. */
@Data public class User {private Long id;private String username;}
