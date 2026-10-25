CREATE TABLE IF NOT EXISTS shopease_notification.service_inbox(id VARCHAR(128) PRIMARY KEY);
CREATE TABLE IF NOT EXISTS shopease_notification.product_watchers(id VARCHAR(255) PRIMARY KEY,customer_id BIGINT,product_id BIGINT,source_version BIGINT NOT NULL,active BIT NOT NULL);
CREATE TABLE IF NOT EXISTS shopease_notification.email_deliveries(id VARCHAR(255) PRIMARY KEY,customer_id BIGINT,subject VARCHAR(255),html LONGTEXT,state VARCHAR(255),attempts INT NOT NULL,next_attempt_at DATETIME(6));
INSERT IGNORE INTO shopease_notification.product_watchers(id,customer_id,product_id,source_version,active) SELECT CONCAT(customer_id,':',product_id),customer_id,product_id,0,1 FROM shopease_order.wishlist_items WHERE NOT EXISTS(SELECT 1 FROM shopease_notification._migration_ledger WHERE table_name='watchers-v1');
INSERT IGNORE INTO shopease_notification._migration_ledger(table_name,source_count) VALUES('watchers-v1',0);
CREATE TABLE IF NOT EXISTS shopease_notification.sms_deliveries(id VARCHAR(255) PRIMARY KEY,customer_id BIGINT,message LONGTEXT,state VARCHAR(255),attempts INT NOT NULL,next_attempt_at DATETIME(6));
