package com.ecommerce.order.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One shared group order: a named collaborative cart that becomes a single
 * ShopEase {@link Order} at checkout, with each member's contribution recorded.
 *
 * The combined order is placed through the normal {@code OrderService}, so
 * stock, coupons, loyalty and tracking behave exactly like an individual
 * checkout; only the settlement between friends lives here.
 */
@Entity
@Table(name = "group_orders", indexes = {
        @Index(name = "idx_group_orders_invite", columnList = "inviteCode", unique = true),
        @Index(name = "idx_group_orders_host", columnList = "host_id")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Column(nullable = false, length = 80)
    private String name;

    /** The creator; acts as the group host and owns the combined parent order. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name="host_id",nullable=false) private Long hostId;
    @Transient private Customer host;
    public Customer getHost(){if(host==null&&hostId!=null){host=new Customer();host.setId(hostId);}return host;}
    public void setHost(Customer value){host=value;hostId=value==null?null:value.getId();}

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupOrderStatus status = GroupOrderStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupSplitMethod splitMethod = GroupSplitMethod.EQUAL;

    /** Who pays the gateway. Host Pays is the only mode enabled today. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupPaymentMode paymentMode = GroupPaymentMode.HOST_PAYS;

    /** Unique shareable token used in the invitation link. */
    @Column(nullable = false, unique = true, length = 12)
    private String inviteCode;

    /** One delivery address for the whole group (captured at creation). */
    @Column(nullable = false, length = 1000)
    private String shippingAddress;

    /** Optional "order by" moment; after it the code no longer accepts joins. */
    private LocalDateTime deadline;

    /** Optional group-level promo code, redeemed once on the combined order. */
    private String couponCode;

    /** The single parent order created at checkout, owned by the host. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placed_order_id")
    @JsonIgnore
    private Order placedOrder;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lockedAt;
    private LocalDateTime placedAt;

    /** Bumped every time the split is recomputed; members must reconfirm. */
    @Column(nullable = false, columnDefinition = "int default 0")
    private int splitVersion = 0;

    @OneToMany(mappedBy = "groupOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<GroupOrderMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "groupOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<GroupCartItem> cartItems = new ArrayList<>();

    @OneToMany(mappedBy = "groupOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<GroupInvitation> invitations = new ArrayList<>();

    @Transient
    @JsonIgnore
    private List<GroupPayment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "groupOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<GroupOrderActivity> activities = new ArrayList<>();

    @OneToOne(mappedBy = "groupOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private GroupBillSplit billSplit;

    public void addMember(GroupOrderMember member) {
        member.setGroupOrder(this);
        members.add(member);
    }

    public void addCartItem(GroupCartItem item) {
        item.setGroupOrder(this);
        cartItems.add(item);
    }

    public void addInvitation(GroupInvitation invitation) {
        invitation.setGroupOrder(this);
        invitations.add(invitation);
    }

    public void addPayment(GroupPayment payment) {
        payment.setGroupOrder(this);
        payments.add(payment);
    }

    public void addActivity(GroupOrderActivity activity) {
        activity.setGroupOrder(this);
        activities.add(activity);
    }

    /** The member row for a customer id, or null. Scans a small in-memory list. */
    public GroupOrderMember memberFor(Long customerId) {
        return members.stream()
                .filter(m -> m.getCustomer() != null && m.getCustomer().getId().equals(customerId))
                .findFirst()
                .orElse(null);
    }
}
