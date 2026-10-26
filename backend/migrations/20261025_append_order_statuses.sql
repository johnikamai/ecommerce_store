-- Additive compatibility fix for legacy MySQL ENUM columns.
-- Matches the original ecommerce_db schema; appends values without renaming,
-- removing, reordering existing values or updating any stored rows.
-- Apply once before demonstrating cancellations/refunds and full order tracking.
ALTER TABLE orders MODIFY COLUMN payment_status
  ENUM('FAILED','PAID','PENDING','REFUNDED','CANCELLED') NULL DEFAULT NULL;
ALTER TABLE payments MODIFY COLUMN payment_status
  ENUM('FAILED','PAID','PENDING','REFUNDED','CANCELLED') NULL DEFAULT NULL;
ALTER TABLE orders MODIFY COLUMN status
  ENUM('CANCELLED','DELIVERED','PLACED','SHIPPED','PACKED','OUT_FOR_DELIVERY') NULL DEFAULT NULL;
