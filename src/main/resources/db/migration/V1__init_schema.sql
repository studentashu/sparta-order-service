-- =====================================================================
-- Order Service — Schema DDL (PostgreSQL, sequence-based IDs)
-- =====================================================================
CREATE SEQUENCE orders_id_seq START WITH 1000 INCREMENT BY 50;
CREATE SEQUENCE order_items_id_seq START WITH 1000 INCREMENT BY 50;

CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY DEFAULT nextval('orders_id_seq'),
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(150) NOT NULL,
    customer_email VARCHAR(150) NOT NULL,
    shipping_address VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_orders_status CHECK (
        status IN ('PENDING','CONFIRMED','REJECTED','CANCELLED','SHIPPED','DELIVERED')
    ),
    CONSTRAINT chk_orders_total_amount_non_negative CHECK (total_amount >= 0)
);
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);

CREATE TABLE order_items (
    order_item_id BIGINT PRIMARY KEY DEFAULT nextval('order_items_id_seq'),
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    unit_price_snapshot DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    subtotal DECIMAL(12,2) GENERATED ALWAYS AS (unit_price_snapshot * quantity) STORED,
    CONSTRAINT fk_order_items_order_id
        FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    CONSTRAINT chk_order_items_quantity_positive CHECK (quantity >= 1),
    CONSTRAINT chk_order_items_unit_price_non_negative CHECK (unit_price_snapshot >= 0),
    CONSTRAINT uq_order_items_order_product UNIQUE (order_id, product_id)
);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
