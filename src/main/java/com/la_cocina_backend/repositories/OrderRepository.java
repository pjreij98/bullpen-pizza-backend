package com.la_cocina_backend.repositories;

import com.la_cocina_backend.models.Order;
import com.la_cocina_backend.models.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatusNotOrderById(OrderStatus status);

}