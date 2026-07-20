package com.training.orderservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Durable record of a reconciliation-worthy event (Section 31): an order stuck in PENDING
 * past the sweep threshold, or a best-effort stock-restore call (BR-6) that failed during
 * cancellation. Without this table, a failed restore was only ever logged and forgotten —
 * this is what lets the sweep find and retry it later.
 */
@Entity
@Table(name = "order_reconciliation_log")
public class OrderReconciliationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reconciliation_log_seq")
    @SequenceGenerator(name = "reconciliation_log_seq", sequenceName = "order_reconciliation_log_id_seq", allocationSize = 50)
    @Column(name = "reconciliation_log_id")
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private ReconciliationEventType eventType;

    // Only populated for RESTORE_FAILED — identifies the specific line item whose
    // compensating restoreStock call failed, since a cancellation can partially fail.
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "quantity")
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_status", nullable = false, length = 20)
    private ReconciliationLogStatus logStatus = ReconciliationLogStatus.OPEN;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected OrderReconciliationLog() {
    }

    private OrderReconciliationLog(Long orderId, ReconciliationEventType eventType, Long productId, Integer quantity) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.productId = productId;
        this.quantity = quantity;
    }

    public static OrderReconciliationLog forStuckPending(Long orderId) {
        return new OrderReconciliationLog(orderId, ReconciliationEventType.STUCK_PENDING, null, null);
    }

    public static OrderReconciliationLog forRestoreFailure(Long orderId, Long productId, int quantity) {
        return new OrderReconciliationLog(orderId, ReconciliationEventType.RESTORE_FAILED, productId, quantity);
    }

    public void recordAttempt() {
        this.attemptCount++;
        this.lastAttemptAt = LocalDateTime.now();
    }

    public void markResolved() {
        this.logStatus = ReconciliationLogStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }

    public void markExhausted() {
        this.logStatus = ReconciliationLogStatus.EXHAUSTED;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public ReconciliationEventType getEventType() {
        return eventType;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public ReconciliationLogStatus getLogStatus() {
        return logStatus;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
}