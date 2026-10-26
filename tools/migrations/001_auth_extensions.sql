CREATE TABLE IF NOT EXISTS shopease_auth.service_inbox (id VARCHAR(128) PRIMARY KEY);

CREATE TABLE IF NOT EXISTS shopease_auth.customer_order_ledger (order_id BIGINT PRIMARY KEY,customer_id BIGINT,merchandise_total DECIMAL(38,2),total_amount DECIMAL(38,2),status VARCHAR(255),payment_status VARCHAR(255),rewarded BIT(1) NOT NULL,source_version BIGINT NOT NULL);
START TRANSACTION;
INSERT INTO shopease_auth.customer_order_ledger(order_id,customer_id,merchandise_total,total_amount,status,payment_status,rewarded,source_version)
SELECT id,customer_id,COALESCE(merchandise_total,total_amount,0),total_amount,CAST(status AS CHAR),CAST(payment_status AS CHAR),COALESCE(rewards_awarded,0),version FROM __SOURCE__.orders
WHERE NOT EXISTS (SELECT 1 FROM shopease_auth._migration_ledger WHERE table_name='customer-order-ledger-v1');
INSERT IGNORE INTO shopease_auth._migration_ledger(table_name,source_count) VALUES ('customer-order-ledger-v1',0);
COMMIT;
