package com.training.orderservice.service.impl;

import com.training.orderservice.client.ProductServiceClient;
import com.training.orderservice.client.dto.ProductSnapshot;
import com.training.orderservice.config.ReconciliationSweepProperties;
import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderItem;
import com.training.orderservice.entity.OrderReconciliationLog;
import com.training.orderservice.entity.OrderStatus;
import com.training.orderservice.entity.ReconciliationEventType;
import com.training.orderservice.entity.ReconciliationLogStatus;
import com.training.orderservice.exception.InsufficientStockException;
import com.training.orderservice.repository.OrderReconciliationLogRepository;
import com.training.orderservice.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Per-candidate reconciliation work, each call its own transaction so one bad order can't
 * roll back the rest of the sweep's batch. Kept as a separate bean (rather than private
 * methods on {@link ReconciliationSweepServiceImpl}) so {@code @Transactional} is actually
 * honored — Spring's proxy-based AOP doesn't intercept self-invoked calls within the same class.
 */
@Component
public class ReconciliationOrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationOrderProcessor.class);

    private final OrderRepository orderRepository;
    private final OrderReconciliationLogRepository reconciliationLogRepository;
    private final ProductServiceClient productServiceClient;
    private final ReconciliationSweepProperties properties;
    private final MeterRegistry meterRegistry;

    public ReconciliationOrderProcessor(OrderRepository orderRepository,
                                         OrderReconciliationLogRepository reconciliationLogRepository,
                                         ProductServiceClient productServiceClient,
                                         ReconciliationSweepProperties properties,
                                         MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.reconciliationLogRepository = reconciliationLogRepository;
        this.productServiceClient = productServiceClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void processStuckPendingOrder(Long orderId) {
        OrderReconciliationLog logEntry = reconciliationLogRepository
                .findByOrderIdAndEventTypeAndLogStatus(orderId, ReconciliationEventType.STUCK_PENDING, ReconciliationLogStatus.OPEN)
                .orElseGet(() -> reconciliationLogRepository.save(OrderReconciliationLog.forStuckPending(orderId)));

        if (logEntry.getAttemptCount() >= properties.maxAttempts()) {
            exhaust(logEntry, orderId);
            return;
        }

        if (properties.dryRun()) {
            log.info("[dry-run] would retry stock commit for stuck PENDING order {}", orderId);
            return;
        }

        Order order = orderRepository.findByIdWithItems(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            // Already resolved through another path (e.g. a manual updateStatus call) since
            // the candidate query ran — nothing left for the sweep to do.
            return;
        }

        logEntry.recordAttempt();
        try {
            confirmOrder(order);
            logEntry.markResolved();
            reconciliationLogRepository.save(logEntry);
            meterRegistry.counter("reconciliation.orders.resolved", "eventType", "STUCK_PENDING").increment();
            log.info("Reconciliation sweep confirmed previously-stuck order {}", orderId);
        } catch (Exception ex) {
            meterRegistry.counter("reconciliation.orders.retry_failed", "eventType", "STUCK_PENDING").increment();
            log.warn("Reconciliation sweep retry failed for order {} (attempt {}/{}): {}",
                    orderId, logEntry.getAttemptCount(), properties.maxAttempts(), ex.getMessage());
            if (logEntry.getAttemptCount() >= properties.maxAttempts()) {
                exhaust(logEntry, orderId);
            } else {
                reconciliationLogRepository.save(logEntry);
            }
        }
    }

    @Transactional
    public void processFailedRestore(Long logEntryId) {
        OrderReconciliationLog logEntry = reconciliationLogRepository.findById(logEntryId).orElse(null);
        if (logEntry == null || logEntry.getLogStatus() != ReconciliationLogStatus.OPEN) {
            return;
        }

        if (logEntry.getAttemptCount() >= properties.maxAttempts()) {
            exhaust(logEntry, logEntry.getOrderId());
            return;
        }

        if (properties.dryRun()) {
            log.info("[dry-run] would retry stock restore for order {} product {}",
                    logEntry.getOrderId(), logEntry.getProductId());
            return;
        }

        logEntry.recordAttempt();
        try {
            productServiceClient.restoreStock(logEntry.getProductId(), logEntry.getQuantity(), logEntry.getOrderId());
            logEntry.markResolved();
            reconciliationLogRepository.save(logEntry);
            meterRegistry.counter("reconciliation.orders.resolved", "eventType", "RESTORE_FAILED").increment();
            log.info("Reconciliation sweep restored stock for order {} product {}",
                    logEntry.getOrderId(), logEntry.getProductId());
        } catch (Exception ex) {
            meterRegistry.counter("reconciliation.orders.retry_failed", "eventType", "RESTORE_FAILED").increment();
            log.warn("Reconciliation sweep restore retry failed for order {} product {} (attempt {}/{}): {}",
                    logEntry.getOrderId(), logEntry.getProductId(), logEntry.getAttemptCount(), properties.maxAttempts(),
                    ex.getMessage());
            if (logEntry.getAttemptCount() >= properties.maxAttempts()) {
                exhaust(logEntry, logEntry.getOrderId());
            } else {
                reconciliationLogRepository.save(logEntry);
            }
        }
    }

    private void confirmOrder(Order order) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            ProductSnapshot snapshot = productServiceClient.getProduct(item.getProductId());
            if (snapshot.stockQuantity() < item.getQuantity()) {
                throw new InsufficientStockException("Product " + item.getProductId() + " has only "
                        + snapshot.stockQuantity() + " units available, requested " + item.getQuantity());
            }
            // Safe to re-invoke: the real client passes orderId as an Idempotency-Key
            // (SDD 29.1), so a retried reduceStock does not double-deduct stock.
            productServiceClient.reduceStock(item.getProductId(), item.getQuantity(), order.getId());
            total = total.add(item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        order.setTotalAmount(total);
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
    }

    private void exhaust(OrderReconciliationLog logEntry, Long orderId) {
        logEntry.markExhausted();
        reconciliationLogRepository.save(logEntry);
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(OrderStatus.RECONCILIATION_FAILED);
            orderRepository.save(order);
        });
        meterRegistry.counter("reconciliation.orders.exhausted", "eventType", logEntry.getEventType().name()).increment();
        log.error("Reconciliation sweep exhausted retries for order {} ({}) - marked RECONCILIATION_FAILED, needs manual review",
                orderId, logEntry.getEventType());
    }
}