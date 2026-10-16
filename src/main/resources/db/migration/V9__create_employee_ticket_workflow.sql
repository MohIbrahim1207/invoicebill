-- Migration for Employee Ticket / Expense Workflow Module

INSERT IGNORE INTO app_roles (name) VALUES ('ROLE_EMPLOYEE');
INSERT IGNORE INTO app_roles (name) VALUES ('ROLE_MANAGER');
INSERT IGNORE INTO app_roles (name) VALUES ('ROLE_ACCOUNTS');
INSERT IGNORE INTO app_roles (name) VALUES ('ROLE_SUPER_ADMIN');

CREATE TABLE IF NOT EXISTS employee_tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_no VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    purpose VARCHAR(255),
    category VARCHAR(100),
    department VARCHAR(100),
    travel_location VARCHAR(255),
    start_date DATE,
    end_date DATE,
    estimated_cost DECIMAL(15,2),
    actual_cost DECIMAL(15,2),
    description TEXT,
    priority VARCHAR(50) DEFAULT 'MEDIUM',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    receipt_url VARCHAR(500),
    manager_signature_url VARCHAR(500),
    accounts_receipt_url VARCHAR(500),
    bank_transaction_id VARCHAR(100),
    amount_paid DECIMAL(15,2),
    payment_date DATE,
    employee_id BIGINT NOT NULL,
    assigned_manager_id BIGINT,
    reviewed_by_admin_id BIGINT,
    accounts_user_id BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_emp_ticket_employee FOREIGN KEY (employee_id) REFERENCES app_user(id),
    CONSTRAINT fk_emp_ticket_manager FOREIGN KEY (assigned_manager_id) REFERENCES app_user(id),
    CONSTRAINT fk_emp_ticket_admin FOREIGN KEY (reviewed_by_admin_id) REFERENCES app_user(id),
    CONSTRAINT fk_emp_ticket_accounts FOREIGN KEY (accounts_user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS workflow_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    remarks TEXT,
    approved_by BIGINT,
    approved_at DATETIME NOT NULL,
    CONSTRAINT fk_wf_hist_ticket FOREIGN KEY (ticket_id) REFERENCES employee_tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_wf_hist_user FOREIGN KEY (approved_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
