-- Flyway Migration V12: Create AI Quote Assistant Chat Session and Message Tables

CREATE TABLE IF NOT EXISTS ai_quote_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by_id BIGINT,
    structured_quote_json LONGTEXT,
    generated_pdf_url VARCHAR(1000),
    generated_pdf_filename VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_quote_session_user FOREIGN KEY (created_by_id) REFERENCES app_user(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS ai_quote_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    sender VARCHAR(20) NOT NULL,
    content TEXT,
    attachment_url VARCHAR(1000),
    attachment_filename VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_quote_message_session FOREIGN KEY (session_id) REFERENCES ai_quote_session(id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_quote_session_user ON ai_quote_session(created_by_id);
CREATE INDEX idx_ai_quote_session_status ON ai_quote_session(status);
CREATE INDEX idx_ai_quote_message_session ON ai_quote_message(session_id);
