package com.training.orderservice.repository;

import com.training.orderservice.entity.OrderReconciliationLog;
import com.training.orderservice.entity.ReconciliationEventType;
import com.training.orderservice.entity.ReconciliationLogStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OrderReconciliationLogRepositoryTest {

    @Autowired
    private OrderReconciliationLogRepository reconciliationLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findByOrderIdAndEventTypeAndLogStatus_findsOnlyMatchingOpenEntry() {
        reconciliationLogRepository.save(OrderReconciliationLog.forStuckPending(1001L));
        entityManager.flush();
        entityManager.clear();

        Optional<OrderReconciliationLog> found = reconciliationLogRepository.findByOrderIdAndEventTypeAndLogStatus(
                1001L, ReconciliationEventType.STUCK_PENDING, ReconciliationLogStatus.OPEN);

        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo(1001L);

        assertThat(reconciliationLogRepository.findByOrderIdAndEventTypeAndLogStatus(
                1001L, ReconciliationEventType.RESTORE_FAILED, ReconciliationLogStatus.OPEN)).isEmpty();
    }

    @Test
    void findByEventTypeAndLogStatus_excludesResolvedEntries() {
        OrderReconciliationLog resolved = OrderReconciliationLog.forRestoreFailure(2002L, 55L, 2);
        resolved.markResolved();
        reconciliationLogRepository.save(resolved);
        reconciliationLogRepository.save(OrderReconciliationLog.forRestoreFailure(2003L, 78L, 1));
        entityManager.flush();
        entityManager.clear();

        var openEntries = reconciliationLogRepository.findByEventTypeAndLogStatus(
                ReconciliationEventType.RESTORE_FAILED, ReconciliationLogStatus.OPEN, PageRequest.of(0, 10));

        assertThat(openEntries).hasSize(1);
        assertThat(openEntries.get(0).getOrderId()).isEqualTo(2003L);
    }
}