package com.ecommerce.order.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A member's share of the group bill and its settlement state.
 *
 * The host's own share links to the parent order payment (PAID once the order
 * is paid online, PENDING for COD). Friends' shares start OWED (offline debt to
 * the host) and move REPORTED_PAID -> CONFIRMED_RECEIVED as people settle up.
 * Nothing here ever calls a gateway; there is no real per-share charge today.
 */


@JsonIgnoreProperties(ignoreUnknown=true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupPayment {
    @com.fasterxml.jackson.annotation.JsonProperty("groupOrderId")
    public Long getGroupOrderId(){return groupOrder==null?null:groupOrder.getId();}
    @com.fasterxml.jackson.annotation.JsonProperty("groupOrderId")
    public void setGroupOrderId(Long id){if(id!=null){groupOrder=new GroupOrder();groupOrder.setId(id);}}

    
    
    private Long id;

    
    
    @JsonIgnore
    private GroupOrder groupOrder;

    
    
    private GroupOrderMember member;

    
    private BigDecimal amount;

    
    
    private GroupShareStatus status = GroupShareStatus.PENDING;

    /** HOST_PAYS or INDIVIDUAL — mirrors the group's payment mode at creation. */
    
    
    private GroupPaymentMode mode = GroupPaymentMode.HOST_PAYS;

    /** Optional reference note when someone reports a cash/UPI transfer. */
    private String transactionRef;

    /** Customer id who last changed the status (member reporting or host confirming). */
    private Long statusUpdatedBy;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime statusUpdatedAt = LocalDateTime.now();
}
