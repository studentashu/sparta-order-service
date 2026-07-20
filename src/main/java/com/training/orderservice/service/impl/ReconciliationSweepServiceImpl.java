package com.training.orderservice.service.impl;

import com.training.orderservice.config.ReconciliationSweepProperties;
import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderReconciliationLog;
import com.training.orderservice.entity.OrderStatus;
import com.training.orderservice.entity.ReconciliationEventType;
import com.training.orderservice.entity.ReconciliationLogStatus;
import com.training.orderservice.repository.OrderRepository;
import com.training.orderservice.repository.OrderReconciliationLogRepository;
import com.training.orderservice.service.ReconciliationSweepService;
import com.training.orderservice.web.CorrelationIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implements the reconciliation sweep described in SDD Section 31. Runs as a single-instance
 * scheduled job (no distributed lock — this service is deployed as a single instance today;
 * revisit if that ever changes) with two independent passes per run: orders stuck in PENDING
 * past the configured threshold, and stock-restore compensations (BR-6) that previously failed.
 * Per-candidate work (its own transaction, so one bad order can't roll back the rest of the
 * batch) is delegated to {@link ReconciliationOrderProcessor}.
 */
@Service
@ConditionalOnProperty(prefix = "reconciliation.sweep", name = "enabled", havingValue = "true")
public class ReconciliationSweepServiceImpl implements ReconciliationSweepService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationSweepServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderReconciliationLogRepository reconciliationLogRepository;
    private final ReconciliationOrderProcessor orderProcessor;
    private final ReconciliationSweepProperties properties;
    private final MeterRegistry meterRegistry;

    public ReconciliationSweepServiceImpl(OrderRepository orderRepository,
                                           OrderReconciliationLogRepository reconciliationLogRepository,
                                           ReconciliationOrderProcessor orderProcessor,
                                           ReconciliationSweepProperties properties,
                                           MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.reconciliationLogRepository = reconciliationLogRepository;
        this.orderProcessor = orderProcessor;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Scheduled(fixedDelayString = "${reconciliation.sweep.fixed-delay:PT5M}")
    public void runSweep() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "reconciliation-sweep-" + UUID.randomUUID());
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            log.info("Reconciliation sweep starting (dryRun={})", properties.dryRun());
            int stuckCount = sweepStuckPendingOrders();
            int restoreCount = sweepFailedRestores();
            log.info("Reconciliation sweep finished: {} stuck-pending candidate(s), {} failed-restore candidate(s)",
                    stuckCount, restoreCount);
        } finally {
            sample.stop(meterRegistry.timer("reconciliation.sweep.duration"));
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private int sweepStuckPendingOrders() {
        LocalDateTime threshold = LocalDateTime.now().minus(properties.stuckThreshold());
        Pageable page = PageRequest.of(0, properties.batchSize());
        Page<Order> candidates = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PENDING, threshold, page);

        meterRegistry.counter("reconciliation.orders.found", "eventType", "STUCK_PENDING")
                .increment(candidates.getNumberOfElements());

        for (Order candidate : candidates) {
            orderProcessor.processStuckPendingOrder(candidate.getId());
        }
        return candidates.getNumberOfElements();
    }

    private int sweepFailedRestores() {
        List<OrderReconciliationLog> candidates = reconciliationLogRepository.findByEventTypeAndLogStatus(
                ReconciliationEventType.RESTORE_FAILED, ReconciliationLogStatus.OPEN,
                PageRequest.of(0, properties.batchSize()));

        meterRegistry.counter("reconciliation.orders.found", "eventType", "RESTORE_FAILED")
                .increment(candidates.size());

        for (OrderReconciliationLog logEntry : candidates) {
            orderProcessor.processFailedRestore(logEntry.getId());
        }
        return candidates.size();
    }
}