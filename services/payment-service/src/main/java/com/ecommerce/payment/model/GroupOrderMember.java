package com.ecommerce.payment.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A member of a group order, their computed share and settlement state. */


@JsonIgnoreProperties(ignoreUnknown=true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupOrderMember {

    
    
    private Long id;

    
    


    
    
    private Customer customer;

    /** "HOST" for the creator, "MEMBER" for everyone else. */
    
    private String role = "MEMBER";

    private LocalDateTime joinedAt = LocalDateTime.now();

    /** This member's payable share in the current split, null until computed. */
    private BigDecimal shareAmount;

    /**
     * Split version this member last confirmed. Reaching the group's current
     * splitVersion is required before checkout, so a reprice forces reapproval.
     */
    
    private int confirmedSplitVersion = -1;

    
    
    private GroupShareStatus shareStatus = GroupShareStatus.PENDING;

    public boolean isHost() {
        return "HOST".equals(role);
    }

    public boolean hasConfirmed(int currentSplitVersion) {
        return confirmedSplitVersion == currentSplitVersion && shareAmount != null;
    }
}
