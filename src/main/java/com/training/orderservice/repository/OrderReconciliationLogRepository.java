package com.training.orderservice.repository;

import com.training.orderservice.entity.OrderReconciliationLog;
import com.training.orderservice.entity.ReconciliationEventType;
import com.training.orderservice.entity.ReconciliationLogStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderReconciliationLogRepository extends JpaRepository<OrderReconciliationLog, Long> {

    Optional<OrderReconciliationLog> findByOrderIdAndEventTypeAndLogStatus(
            Long orderId, ReconciliationEventType eventType, ReconciliationLogStatus logStatus);

    List<OrderReconciliationLog> findByEventTypeAndLogStatus(
            ReconciliationEventType eventType, ReconciliationLogStatus logStatus, Pageable pageable);
}