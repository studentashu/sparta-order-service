package com.training.orderservice.repository;

import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderItem;
import com.training.orderservice.entity.OrderStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence-layer tests for {@link OrderRepository}, run with @DataJpaTest against
 * an in-memory H2 database. Verifies the two custom queries and the order → order_items
 * cascade actually behave as the service layer assumes.
 */
@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private EntityManager entityManager;

    private Order newOrder(Long customerId, OrderStatus status) {
        Order order = new Order(customerId, "Test Customer", "test@example.com", "1 Test Street");
        order.setStatus(status);
        return order;
    }

    @Test
    void findByIdWithItems_loadsOrderWithItsItems() {
        Order order = newOrder(101L, OrderStatus.CONFIRMED);
        order.addItem(new OrderItem(order, 55L, "Wireless Mouse", new BigDecimal("25.00"), 2));
        order.addItem(new OrderItem(order, 78L, "Mechanical Keyboard", new BigDecimal("95.50"), 1));
        Long id = orderRepository.save(order).getId();

        // Flush to the DB and detach everything, so the query truly hits the database
        // rather than returning the still-cached instance.
        entityManager.flush();
        entityManager.clear();

        Optional<Order> found = orderRepository.findByIdWithItems(id);

        assertThat(found).isPresent();
        assertThat(found.get().getItems()).hasSize(2);
        assertThat(found.get().getItems())
                .extracting(OrderItem::getProductId)
                .containsExactlyInAnyOrder(55L, 78L);
    }

    @Test
    void findByIdWithItems_returnsEmptyWhenOrderMissing() {
        assertThat(orderRepository.findByIdWithItems(999_999L)).isEmpty();
    }

    @Test
    void findByOptionalFilters_appliesStatusAndCustomerFilters() {
        orderRepository.save(newOrder(101L, OrderStatus.PENDING));
        orderRepository.save(newOrder(101L, OrderStatus.CONFIRMED));
        orderRepository.save(newOrder(202L, OrderStatus.CONFIRMED));
        entityManager.flush();
        entityManager.clear();

        PageRequest page = PageRequest.of(0, 10);

        // no filters -> all three
        assertThat(orderRepository.findByOptionalFilters(null, null, page).getTotalElements()).isEqualTo(3);
        // status only -> the two CONFIRMED
        assertThat(orderRepository.findByOptionalFilters(OrderStatus.CONFIRMED, null, page).getTotalElements()).isEqualTo(2);
        // customer only -> the two for customer 101
        assertThat(orderRepository.findByOptionalFilters(null, 101L, page).getTotalElements()).isEqualTo(2);
        // both -> the single CONFIRMED order for customer 202
        assertThat(orderRepository.findByOptionalFilters(OrderStatus.CONFIRMED, 202L, page).getTotalElements()).isEqualTo(1);
    }

    @Test
    void findByStatusAndUpdatedAtBefore_returnsOnlyOrdersOlderThanThreshold() {
        orderRepository.save(newOrder(101L, OrderStatus.PENDING));
        entityManager.flush();
        entityManager.clear();

        LocalDateTime future = LocalDateTime.now().plusMinutes(1);
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);

        assertThat(orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PENDING, future, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
        assertThat(orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PENDING, past, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(0);
    }

    @Test
    void findByStatusAndUpdatedAtBefore_excludesOtherStatuses() {
        orderRepository.save(newOrder(101L, OrderStatus.CONFIRMED));
        entityManager.flush();
        entityManager.clear();

        LocalDateTime future = LocalDateTime.now().plusMinutes(1);

        assertThat(orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PENDING, future, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(0);
    }

    @Test
    void savingOrderCascadesToOrderItems() {
        Order order = newOrder(101L, OrderStatus.CONFIRMED);
        order.addItem(new OrderItem(order, 55L, "Wireless Mouse", new BigDecimal("25.00"), 2));

        orderRepository.save(order); // items are never saved explicitly — cascade must handle them
        entityManager.flush();

        assertThat(orderItemRepository.count()).isEqualTo(1);
    }

    @Test
    void deletingOrderCascadesToOrderItems() {
        Order order = newOrder(101L, OrderStatus.CONFIRMED);
        order.addItem(new OrderItem(order, 55L, "Wireless Mouse", new BigDecimal("25.00"), 2));
        order.addItem(new OrderItem(order, 78L, "Mechanical Keyboard", new BigDecimal("95.50"), 1));
        Order saved = orderRepository.save(order);
        entityManager.flush();
        assertThat(orderItemRepository.count()).isEqualTo(2);

        orderRepository.delete(saved);
        entityManager.flush();

        // orphanRemoval + cascade REMOVE should delete the child rows too
        assertThat(orderItemRepository.count()).isZero();
    }
}
