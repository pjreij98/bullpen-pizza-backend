package com.la_cocina_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class CreateOrderRequest {
    private Long customerId; // optional
    private List<CartItemDTO> items;
    private String customerName;
    private String customerEmail;
}
