package com.ecommerce.payment.model;

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
@Entity
@Table(name = "group_payments",
        indexes = @Index(name = "idx_group_payment_group", columnList = "group_order_id"))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="group_order_id",nullable=false) private Long groupOrderId;
    @Column(name="member_id",nullable=false) private Long memberId;
    @Convert(converter=com.ecommerce.payment.config.MemberSnapshotConverter.class)
    @Column(name="member_snapshot",columnDefinition="longtext") private GroupOrderMember member;
    public void setMember(GroupOrderMember value){member=value;memberId=value==null?null:value.getId();}

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupShareStatus status = GroupShareStatus.PENDING;

    /** HOST_PAYS or INDIVIDUAL — mirrors the group's payment mode at creation. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupPaymentMode mode = GroupPaymentMode.HOST_PAYS;

    /** Optional reference note when someone reports a cash/UPI transfer. */
    private String transactionRef;

    /** Customer id who last changed the status (member reporting or host confirming). */
    private Long statusUpdatedBy;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime statusUpdatedAt = LocalDateTime.now();
}
