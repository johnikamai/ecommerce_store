package com.ecommerce.ecommerce_system.model;

import java.util.List;

public class OrderRequest {

    private java.math.BigDecimal expectedTotal;
    public java.math.BigDecimal getExpectedTotal() { return expectedTotal; }
    public void setExpectedTotal(java.math.BigDecimal expectedTotal) { this.expectedTotal = expectedTotal; }
    private Long customerId;
    private String couponCode;
    private String shippingAddress;
    private String paymentMethod;
    private List<Item> items;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    public static class Item {
        private Long productId;
        private Integer quantity;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
