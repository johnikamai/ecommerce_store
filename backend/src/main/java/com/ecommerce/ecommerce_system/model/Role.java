package com.ecommerce.ecommerce_system.model;

/**
 * Staff run the day-to-day shop: they work the catalog and read orders, but they
 * cannot touch money. Anything that moves cash, issues a refund, changes a
 * discount, deletes a product or edits who is an admin stays with ADMIN.
 *
 * The split exists because "can add products" and "can issue refunds" are very
 * different amounts of trust, and a single ADMIN role cannot express the
 * difference. Splitting them means a compromised staff account cannot drain the
 * business.
 */
public enum Role {
    ADMIN,
    STAFF,
    CUSTOMER
}
