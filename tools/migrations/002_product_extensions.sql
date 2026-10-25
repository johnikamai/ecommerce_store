CREATE TABLE IF NOT EXISTS shopease_product.product_sales (product_id BIGINT PRIMARY KEY, units_sold BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS shopease_product.product_purchases (id VARCHAR(128) PRIMARY KEY, customer_id BIGINT NOT NULL, product_id BIGINT NOT NULL, quantity BIGINT NOT NULL, INDEX idx_purchase_owner(customer_id,product_id));
CREATE TABLE IF NOT EXISTS shopease_product.service_outbox (id VARCHAR(128) PRIMARY KEY,target VARCHAR(255) NOT NULL,payload LONGTEXT NOT NULL,delivered BIT(1) NOT NULL,attempts INT NOT NULL,created_at DATETIME(6),next_attempt_at DATETIME(6),last_error VARCHAR(120));
CREATE TABLE IF NOT EXISTS shopease_product.inventory_reservations (id VARCHAR(128) PRIMARY KEY,customer_id BIGINT NOT NULL,state VARCHAR(20) NOT NULL,request_fingerprint VARCHAR(64) NOT NULL,payload LONGTEXT NOT NULL,version BIGINT NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS shopease_product.product_operations (id VARCHAR(128) PRIMARY KEY,request_fingerprint VARCHAR(64) NOT NULL);
-- Import derived values exactly once; never reset projections after service writes.
START TRANSACTION;
INSERT INTO shopease_product.product_sales(product_id,units_sold)
SELECT oi.product_id,SUM(oi.quantity) FROM __SOURCE__.order_items oi
WHERE NOT EXISTS (SELECT 1 FROM shopease_product._migration_ledger WHERE table_name='product-projections-v1') GROUP BY oi.product_id;
INSERT INTO shopease_product.product_purchases(id,customer_id,product_id,quantity)
SELECT CONCAT(o.customer_id,':',oi.product_id),o.customer_id,oi.product_id,SUM(oi.quantity)
FROM __SOURCE__.order_items oi JOIN __SOURCE__.orders o ON o.id=oi.order_id
WHERE o.status <> 'CANCELLED' AND NOT EXISTS (SELECT 1 FROM shopease_product._migration_ledger WHERE table_name='product-projections-v1') GROUP BY o.customer_id,oi.product_id;
INSERT IGNORE INTO shopease_product._migration_ledger(table_name,source_count) VALUES ('product-projections-v1',0);
COMMIT;
