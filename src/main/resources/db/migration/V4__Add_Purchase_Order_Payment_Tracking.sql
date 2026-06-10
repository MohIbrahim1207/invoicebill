ALTER TABLE purchase_order
    ADD COLUMN paid_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN balance_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN payment_status VARCHAR(32) NOT NULL DEFAULT 'UNPAID';

UPDATE purchase_order
SET paid_amount = 0.00,
    balance_amount = COALESCE(po_amount, amount, amount_invoiced, 0.00),
    payment_status = 'UNPAID';

CREATE TABLE purchase_order_payment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    payment_date DATE NOT NULL,
    remarks VARCHAR(1000),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_purchase_order_payment_po_id (purchase_order_id),
    CONSTRAINT fk_purchase_order_payment_purchase_order
        FOREIGN KEY (purchase_order_id)
        REFERENCES purchase_order (id)
        ON DELETE CASCADE
);
