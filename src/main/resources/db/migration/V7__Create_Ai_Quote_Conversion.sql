-- Flyway migration to create the ai_quote_conversion table.
CREATE TABLE ai_quote_conversion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    original_file_url VARCHAR(1000) NOT NULL,
    extracted_json LONGTEXT NULL,
    generated_quote_file_name VARCHAR(255) NULL,
    generated_quote_file_url VARCHAR(1000) NULL,
    processing_date DATETIME NOT NULL,
    status VARCHAR(50) NOT NULL,
    processing_time_ms BIGINT DEFAULT 0,
    error_message TEXT NULL,
    version INT DEFAULT 1,
    version_history LONGTEXT NULL,
    CONSTRAINT fk_ai_quote_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
