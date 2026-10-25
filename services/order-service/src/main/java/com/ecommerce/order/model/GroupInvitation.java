package com.ecommerce.order.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An invitation token for a group order.
 *
 * Each group has a primary code (issued at creation); the host may reissue it
 * or issue per-person codes. Only authenticated users may join, and an accepted
 * code records who used it so invites stay auditable.
 */
@Entity
@Table(name = "group_invitations",
        indexes = @Index(name = "idx_group_invite_code", columnList = "code", unique = true))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_order_id", nullable = false)
    @JsonIgnore
    private GroupOrder groupOrder;

    @Column(nullable = false, unique = true, length = 12)
    private String code;

    /** Optional: the invitee's email, when the host invited a specific person. */
    private String invitedEmail;

    /** PENDING, ACCEPTED, REVOKED, EXPIRED. */
    @Column(nullable = false, length = 12)
    private String status = "PENDING";

    private LocalDateTime createdAt = LocalDateTime.now();

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name="accepted_by_id") private Long acceptedById;
    @Transient private Customer acceptedBy;
    public Customer getAcceptedBy(){if(acceptedBy==null&&acceptedById!=null){acceptedBy=new Customer();acceptedBy.setId(acceptedById);}return acceptedBy;}
    public void setAcceptedBy(Customer value){acceptedBy=value;acceptedById=value==null?null:value.getId();}

    private LocalDateTime acceptedAt;
}
