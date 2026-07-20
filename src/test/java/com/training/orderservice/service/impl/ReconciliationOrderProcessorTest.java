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
import com.training.orderservice.exception.ProductServiceUnavailableException;
import com.training.orderservice.repository.OrderRepository;
import com.training.orderservice.repository.OrderReconciliationLogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationOrderProcessorTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderReconciliationLogRepository reconciliationLogRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    private ReconciliationOrderProcessor processor;

    private void init(boolean dryRun, int maxAttempts) {
        ReconciliationSweepProperties properties =
                new ReconciliationSweepProperties(true, dryRun, Duration.ofMinutes(15), maxAttempts, 100);
        processor = new ReconciliationOrderProcessor(orderRepository, reconciliationLogRepository,
                productServiceClient, properties, new SimpleMeterRegistry());
    }

    private Order pendingOrderWithItem() {
        Order order = new Order(101L, "Jane Doe", "jane@example.com", "221B Baker Street");
        order.addItem(new OrderItem(order, 55L, "Wireless Mouse", new BigDecimal("25.00"), 2));
        return order;
    }

    // ----- processStuckPendingOrder -----

    @Test
    void processStuckPendingOrder_confirmsOrderWhenRetrySucceeds() {
        init(false, 5);
        when(reconciliationLogRepository.findByOrderIdAndEventTypeAndLogStatus(
                1001L, ReconciliationEventType.STUCK_PENDING, ReconciliationLogStatus.OPEN))
                .thenReturn(Optional.empty());
        when(reconciliationLogRepository.save(any(OrderReconciliationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        Order order = pendingOrderWithItem();
        when(orderRepository.findByIdWithItems(1001L)).thenReturn(Optional.of(order));
        when(productServiceClient.getProduct(55L))
                .thenReturn(new ProductSnapshot(55L, "Wireless Mouse", new BigDecimal("25.00"), 10));

        processor.processStuckPendingOrder(1001L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(productServiceClient).reduceStock(eq(55L), eq(2), eq(order.getId()));
        verify(orderRepository).save(order);
    }

    @Test
    void processStuckPendingOrder_keepsOpenAndIncrementsAttemptWhenRetryFails() {
        init(false, 5);
        when(reconciliationLogRepository.findByOrderIdAndEventTypeAndLogStatus(
                1001L, ReconciliationEventType.STUCK_PENDING, ReconciliationLogStatus.OPEN))
                .thenReturn(Optional.empty());
        when(reconciliationLogRepository.save(any(OrderReconciliationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        Order order = pendingOrderWithItem();
        when(orderRepository.findByIdWithItems(1001L)).thenReturn(Optional.of(order));
        // Not enough stock this time either -> confirmOrder() throws, retry fails.
        when(productServiceClient.getProduct(55L))
                .thenReturn(new ProductSnapshot(55L, "Wireless Mouse", new BigDecimal("25.00"), 0));

        processor.processStuckPendingOrder(1001L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(order);
        verify(productServiceClient, never()).reduceStock(any(), any(Integer.class), any());
    }

    @Test
    void processStuckPendingOrder_exhaustsAfterMaxAttempts() {
        init(false, 1);
        OrderReconciliationLog existingLog = OrderReconciliationLog.forStuckPending(1001L);
        existingLog.recordAttempt(); // attemptCount = 1, already at the max of 1
        when(reconciliationLogRepository.findByOrderIdAndEventTypeAndLogStatus(
                1001L, ReconciliationEventType.STUCK_PENDING, ReconciliationLogStatus.OPEN))
                .thenReturn(Optional.of(existingLog));
        Order order = pendingOrderWithItem();
        when(orderRepository.findById(1001L)).thenReturn(Optional.of(order));

        processor.processStuckPendingOrder(1001L);

        assertThat(existingLog.getLogStatus()).isEqualTo(ReconciliationLogStatus.EXHAUSTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECONCILIATION_FAILED);
        verify(orderRepository).save(order);
        verify(productServiceClient, never()).getProduct(any());
    }

    @Test
    void processStuckPendingOrder_dryRunDoesNotMutateAnything() {
        init(true, 5);
        when(reconciliationLogRepository.findByOrderIdAndEventTypeAndLogStatus(
                1001L, ReconciliationEventType.STUCK_PENDING, ReconciliationLogStatus.OPEN))
                .thenReturn(Optional.empty());
        when(reconciliationLogRepository.save(any(OrderReconciliationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        processor.processStuckPendingOrder(1001L);

        verify(orderRepository, never()).findByIdWithItems(any());
        verify(productServiceClient, never()).getProduct(any());
    }

    @Test
    void processStuckPendingOrder_skipsWhenOrderNoLongerPending() {
        init(false, 5);
        when(reconciliationLogRepository.findByOrderIdAndEventTypeAndLogStatus(
                1001L, ReconciliationEventType.STUCK_PENDING, ReconciliationLogStatus.OPEN))
                .thenReturn(Optional.empty());
        when(reconciliationLogRepository.save(any(OrderReconciliationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        Order order = pendingOrderWithItem();
        order.setStatus(OrderStatus.CANCELLED); // resolved another way since the candidate query ran
        when(orderRepository.findByIdWithItems(1001L)).thenReturn(Optional.of(order));

        processor.processStuckPendingOrder(1001L);

        verify(productServiceClient, never()).getProduct(any());
        verify(orderRepository, never()).save(any());
    }

    // ----- processFailedRestore -----

    @Test
    void processFailedRestore_resolvesLogWhenRestoreSucceeds() {
        init(false, 5);
        OrderReconciliationLog logEntry = OrderReconciliationLog.forRestoreFailure(2002L, 55L, 2);
        when(reconciliationLogRepository.findById(9001L)).thenReturn(Optional.of(logEntry));

        processor.processFailedRestore(9001L);

        assertThat(logEntry.getLogStatus()).isEqualTo(ReconciliationLogStatus.RESOLVED);
        verify(productServiceClient).restoreStock(55L, 2, 2002L);
        verify(reconciliationLogRepository).save(logEntry);
    }

    @Test
    void processFailedRestore_exhaustsAfterMaxAttempts() {
        init(false, 1);
        OrderReconciliationLog logEntry = OrderReconciliationLog.forRestoreFailure(2002L, 55L, 2);
        logEntry.recordAttempt(); // already at the max of 1
        when(reconciliationLogRepository.findById(9001L)).thenReturn(Optional.of(logEntry));
        Order order = pendingOrderWithItem();
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(2002L)).thenReturn(Optional.of(order));

        processor.processFailedRestore(9001L);

        assertThat(logEntry.getLogStatus()).isEqualTo(ReconciliationLogStatus.EXHAUSTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECONCILIATION_FAILED);
        verify(productServiceClient, never()).restoreStock(any(), any(Integer.class), any());
    }

    @Test
    void processFailedRestore_keepsLogOpenAndRetriesLaterWhenRestoreFailsAgain() {
        init(false, 5);
        OrderReconciliationLog logEntry = OrderReconciliationLog.forRestoreFailure(2002L, 55L, 2);
        when(reconciliationLogRepository.findById(9001L)).thenReturn(Optional.of(logEntry));
        doThrow(new ProductServiceUnavailableException("still down"))
                .when(productServiceClient).restoreStock(55L, 2, 2002L);

        processor.processFailedRestore(9001L);

        assertThat(logEntry.getLogStatus()).isEqualTo(ReconciliationLogStatus.OPEN);
        assertThat(logEntry.getAttemptCount()).isEqualTo(1);
        verify(reconciliationLogRepository).save(logEntry);
    }

    @Test
    void processFailedRestore_doesNothingWhenLogAlreadyResolved() {
        init(false, 5);
        OrderReconciliationLog logEntry = OrderReconciliationLog.forRestoreFailure(2002L, 55L, 2);
        logEntry.markResolved();
        when(reconciliationLogRepository.findById(9001L)).thenReturn(Optional.of(logEntry));

        processor.processFailedRestore(9001L);

        verify(productServiceClient, never()).restoreStock(any(), any(Integer.class), any());
    }
}