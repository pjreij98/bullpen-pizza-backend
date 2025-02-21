package com.bullpen_pizza_backend.repositories;

import com.bullpen_pizza_backend.models.Order;
import com.bullpen_pizza_backend.models.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatusOrderById(OrderStatus status);

}