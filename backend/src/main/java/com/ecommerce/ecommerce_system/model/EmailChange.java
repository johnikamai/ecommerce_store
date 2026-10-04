package com.ecommerce.ecommerce_system.model;
import jakarta.persistence.*;
import lombok.*;
@Entity @Data @NoArgsConstructor @AllArgsConstructor
public class EmailChange {
    @Id private String username;
    private String email;
}
