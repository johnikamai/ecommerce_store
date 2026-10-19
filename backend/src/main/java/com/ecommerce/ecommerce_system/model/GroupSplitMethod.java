package com.ecommerce.ecommerce_system.model;

/** How the combined bill is divided among group members. */
public enum GroupSplitMethod {
    /** Total divided equally; remainder paise go deterministically to the earliest joiners. */
    EQUAL,
    /** Each member pays their own items; shipping/tax split equally, discounts pro-rata. */
    ITEM_BASED,
    /** Host proposes exact amounts; must sum to the payable total and be approved by members. */
    CUSTOM
}
