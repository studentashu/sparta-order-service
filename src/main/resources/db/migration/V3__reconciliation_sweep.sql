-- =====================================================================
-- Reconciliation sweep (SDD Section 31) — schema support
-- =====================================================================

-- New terminal status for orders where the sweep exhausted its retry
-- budget and the case needs manual review.
ALTER TABLE orders DROP CONSTRAINT chk_orders_status;
ALTER TABLE orders ADD CONSTRAINT chk_orders_status CHECK (
    status IN ('PENDING','CONFIRMED','REJECTED','CANCELLED','SHIPPED','DELIVERED','RECONCILIATION_FAILED')
);

-- Supports the sweep's "status = PENDING AND updated_at < threshold" query.
CREATE INDEX idx_orders_status_updated_at ON orders(status, updated_at);

CREATE SEQUENCE order_reconciliation_log_id_seq START WITH 1000 INCREMENT BY 50;

-- Durable record of reconciliation work: an order stuck in PENDING past the
-- threshold, or a best-effort stock-restore call (BR-6) that failed during
-- cancellation. Without this table, a failed restore is only ever logged and
-- the system has no way to know a compensation is still owed.
CREATE TABLE order_reconciliation_log (
    reconciliation_log_id BIGINT PRIMARY KEY DEFAULT nextval('order_reconciliation_log_id_seq'),
    order_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    product_id BIGINT,
    quantity INT,
    log_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMP,
    resolved_at TIMESTAMP,
    CONSTRAINT chk_reconciliation_event_type CHECK (event_type IN ('STUCK_PENDING','RESTORE_FAILED')),
    CONSTRAINT chk_reconciliation_log_status CHECK (log_status IN ('OPEN','RESOLVED','EXHAUSTED')),
    CONSTRAINT chk_reconciliation_attempt_count_non_negative CHECK (attempt_count >= 0)
);
CREATE INDEX idx_reconciliation_log_order_id ON order_reconciliation_log(order_id);
CREATE INDEX idx_reconciliation_log_event_status ON order_reconciliation_log(event_type, log_status);