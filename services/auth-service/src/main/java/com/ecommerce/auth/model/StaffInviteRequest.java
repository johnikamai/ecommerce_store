package com.ecommerce.auth.model;

import lombok.Data;

/** Admin-initiated staff account creation (invitation). */
@Data
public class StaffInviteRequest {
    private String fullName;
    private String email;
    private String phone;
    private Department department;
    private String experience;
    private String reason;
    private String password;
}
