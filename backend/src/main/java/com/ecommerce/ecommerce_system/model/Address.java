package com.ecommerce.ecommerce_system.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "addresses")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Data
@NoArgsConstructor
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Kept as a plain id reference to avoid shipping full customer JSON in lists.
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    private String label; // e.g. Home, Work

    private String name; // receiver name

    private String addressLine;

    private String city;

    private String state;

    private String pincode;

    private String phone;

    private Boolean isDefault = false;

    /** Concise printable snapshot used when placing an order. */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isBlank()) sb.append(name.trim());
        if (addressLine != null && !addressLine.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(addressLine.trim());
        }
        if (city != null && !city.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city.trim());
        }
        if (state != null && !state.isBlank()) {
            sb.append(" - ").append(state.trim());
        }
        if (pincode != null && !pincode.isBlank()) {
            sb.append(" ").append(pincode.trim());
        }
        if (phone != null && !phone.isBlank()) {
            sb.append(" (Phone: ").append(phone.trim()).append(")");
        }
        return sb.toString();
    }
}