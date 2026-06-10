CREATE TABLE purchase_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    quantity DECIMAL(19,2) NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    line_total DECIMAL(19,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_purchase_order_item_po_id (purchase_order_id),
    CONSTRAINT fk_purchase_order_item_purchase_order
        FOREIGN KEY (purchase_order_id)
        REFERENCES purchase_order (id)
        ON DELETE CASCADE
);
