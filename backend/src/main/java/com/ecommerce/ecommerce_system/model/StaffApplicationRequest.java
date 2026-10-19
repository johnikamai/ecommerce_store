package com.ecommerce.ecommerce_system.model;

import lombok.Data;

/** Payload for the public /api/staff/apply endpoint. */
@Data
public class StaffApplicationRequest {
    private String fullName;
    private String email;
    private String phone;
    private Department department;
    private String experience;
    private String reason;
    private String password;
}
