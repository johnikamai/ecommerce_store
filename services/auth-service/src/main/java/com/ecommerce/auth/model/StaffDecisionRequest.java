package com.ecommerce.auth.model;

import lombok.Data;

import java.util.Set;

/** Admin action on a staff profile or a direct staff invite. */
@Data
public class StaffDecisionRequest {
    /** APPROVE, REJECT, REQUEST_INFO, SUSPEND, REACTIVATE, DEACTIVATE */
    private String action;
    private String reason;
    /** Optional department override applied on approval. */
    private Department department;
    /** Optional explicit permission set; when null the department defaults are used. */
    private Set<Permission> permissions;
}
