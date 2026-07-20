package com.training.orderservice.service.impl;

import com.training.orderservice.config.ReconciliationSweepProperties;
import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderReconciliationLog;
import com.training.orderservice.entity.OrderStatus;
import com.training.orderservice.entity.ReconciliationEventType;
import com.training.orderservice.entity.ReconciliationLogStatus;
import com.training.orderservice.repository.OrderRepository;
import com.training.orderservice.repository.OrderReconciliationLogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ReconciliationSweepServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderReconciliationLogRepository reconciliationLogRepository;

    @Mock
    private ReconciliationOrderProcessor orderProcessor;

    private ReconciliationSweepServiceImpl sweepService;

    @BeforeEach
    void setUp() {
        ReconciliationSweepProperties properties =
                new ReconciliationSweepProperties(true, false, Duration.ofMinutes(15), 5, 100);
        sweepService = new ReconciliationSweepServiceImpl(orderRepository, reconciliationLogRepository,
                orderProcessor, properties, new SimpleMeterRegistry());
    }

    @Test
    void runSweep_delegatesStuckPendingOrdersAndFailedRestoresToProcessor() {
        Order stuckOrder = mock(Order.class);
        when(stuckOrder.getId()).thenReturn(1001L);
        when(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.PENDING), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stuckOrder)));

        OrderReconciliationLog restoreLog = mock(OrderReconciliationLog.class);
        when(restoreLog.getId()).thenReturn(9001L);
        when(reconciliationLogRepository.findByEventTypeAndLogStatus(
                eq(ReconciliationEventType.RESTORE_FAILED), eq(ReconciliationLogStatus.OPEN), any()))
                .thenReturn(List.of(restoreLog));

        sweepService.runSweep();

        verify(orderProcessor).processStuckPendingOrder(1001L);
        verify(orderProcessor).processFailedRestore(9001L);
    }

    @Test
    void runSweep_doesNothingWhenNoCandidatesFound() {
        when(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.PENDING), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(reconciliationLogRepository.findByEventTypeAndLogStatus(
                eq(ReconciliationEventType.RESTORE_FAILED), eq(ReconciliationLogStatus.OPEN), any()))
                .thenReturn(List.of());

        sweepService.runSweep();

        verify(orderProcessor, org.mockito.Mockito.never()).processStuckPendingOrder(any());
        verify(orderProcessor, org.mockito.Mockito.never()).processFailedRestore(any());
    }
}