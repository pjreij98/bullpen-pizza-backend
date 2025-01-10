package com.la_cocina_backend.repositories;

import com.la_cocina_backend.models.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderId(Long orderId);

    Payment findByTransactionId(String transactionId);
}