package com.la_cocina_backend.repositories;

import com.la_cocina_backend.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {}