package com.ecommerce.ecommerce_system.model;

/**
 * Who is responsible for the money.
 *
 * HOST_PAYS (implemented): host pays the whole combined order through the
 * existing ShopEase checkout; friends' shares are recorded for offline
 * settlement ("Owed to Host"), never treated as online payments.
 *
 * INDIVIDUAL (disabled): each member pays their own share online. Requires a
 * real gateway with per-share charges; ShopEase today only supports COD and an
 * opt-in demo mode, so the backend refuses this mode.
 */
public enum GroupPaymentMode {
    HOST_PAYS,
    INDIVIDUAL
}
