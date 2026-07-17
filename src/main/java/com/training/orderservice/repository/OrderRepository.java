package com.training.orderservice.repository;

import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("SELECT o FROM Order o WHERE (:status IS NULL OR o.status = :status) "
            + "AND (:customerId IS NULL OR o.customerId = :customerId)")
    Page<Order> findByOptionalFilters(@Param("status") OrderStatus status,
                                       @Param("customerId") Long customerId,
                                       Pageable pageable);
}
