CREATE TABLE IF NOT EXISTS shopease_recommendation.query_history(id VARCHAR(255) PRIMARY KEY,customer_id BIGINT,endpoint VARCHAR(255),request_payload LONGTEXT,created_at DATETIME(6));
