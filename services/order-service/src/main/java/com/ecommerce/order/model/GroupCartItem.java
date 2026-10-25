package com.ecommerce.order.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One product line in a group cart, attributed to the member who added it. */
@Entity
@Table(name = "group_cart_items",
        indexes = @Index(name = "idx_group_cart_group", columnList = "group_order_id"))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupCartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_order_id", nullable = false)
    @JsonIgnore
    private GroupOrder groupOrder;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name="product_id",nullable=false) private Long productId;
    @Transient private Product product;
    public Product getProduct(){if(product==null&&productId!=null){product=new Product();product.setId(productId);}return product;}
    public void setProduct(Product value){product=value;productId=value==null?null:value.getId();}

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name="added_by_id",nullable=false) private Long addedById;
    @Transient private Customer addedBy;
    public Customer getAddedBy(){if(addedBy==null&&addedById!=null){addedBy=new Customer();addedBy.setId(addedById);}return addedBy;}
    public void setAddedBy(Customer value){addedBy=value;addedById=value==null?null:value.getId();}

    @Column(nullable = false)
    private Integer quantity;

    /** Price when the item was added; revalidated against the live price at checkout. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    private LocalDateTime addedAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Current line total. Live quantity x snapshot unit price, like OrderItem. */
    @Transient
    public BigDecimal getLineTotal() {
        if (unitPrice == null || quantity == null) return BigDecimal.ZERO;
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
