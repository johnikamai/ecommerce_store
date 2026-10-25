package com.ecommerce.order.model;

/** Audit/activity events recorded for a group order (powers the live feed). */
public enum GroupActivityType {
    CREATED,
    INVITE_ISSUED,
    JOINED,
    ITEM_ADDED,
    ITEM_UPDATED,
    ITEM_REMOVED,
    MEMBER_REMOVED,
    SPLIT_SET,
    SHARE_CONFIRMED,
    CART_LOCKED,
    CART_UNLOCKED,
    TOTALS_CHANGED,
    CHECKOUT_STARTED,
    ORDER_PLACED,
    PAYMENT_REPORTED,
    PAYMENT_CONFIRMED,
    CANCELLED
}
