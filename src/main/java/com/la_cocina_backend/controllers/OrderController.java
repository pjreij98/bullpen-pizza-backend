package com.la_cocina_backend.controllers;

import com.la_cocina_backend.dto.CreateOrderRequest;
import com.la_cocina_backend.models.*;
import com.la_cocina_backend.repositories.CustomizationRepository;
import com.la_cocina_backend.repositories.OrderItemCustomizationRepository;
import com.la_cocina_backend.repositories.OrderItemRepository;
import com.la_cocina_backend.repositories.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CustomizationRepository customizationRepository;

    @Autowired
    private OrderItemCustomizationRepository orderItemCustomizationRepository;

    @PostMapping
    public Order createOrder(@RequestBody CreateOrderRequest request) {
        // 1. Create a new Order
        Order newOrder = new Order();
        newOrder.setOrderDate(LocalDateTime.now());
        newOrder.setStatus(OrderStatus.PENDING);
        newOrder.setPaymentStatus(PaymentStatus.NOT_PAID);
        newOrder.setCustomerId(request.getCustomerId());
        newOrder.setCustomerName(request.getCustomerName());
        newOrder.setCustomerEmail(request.getCustomerEmail());
        newOrder.setTotalAmount(BigDecimal.ZERO); // temporarily set 0
        newOrder.setSpecialNotes(request.getSpecialNotes());

        // Save the order first to get an ID
        newOrder = orderRepository.save(newOrder);

        // 2. Create OrderItems from the CartItemDTO list
        BigDecimal total = BigDecimal.ZERO;

        if (request.getItems() != null) {
            for (var cartItem : request.getItems()) {
                OrderItem orderItem = new OrderItem();
                orderItem.setOrderId(newOrder.getId());
                orderItem.setMenuItemId(cartItem.getMenuItemId());
                orderItem.setQuantity(cartItem.getQuantity());

                // Use price from the cart or look up in your MenuItem table
                BigDecimal itemPrice = cartItem.getPriceAtOrderTime();
                orderItem.setItemPrice(itemPrice);

                // Accumulate total
                BigDecimal itemTotal = itemPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
                total = total.add(itemTotal);

                // Save each OrderItem
                orderItem = orderItemRepository.save(orderItem);

                // Handle Customizations for this OrderItem
                if (cartItem.getCustomizations() != null) {
                    for (var customization : cartItem.getCustomizations()) {
                        OrderItemCustomization orderItemCustomization = new OrderItemCustomization();
                        orderItemCustomization.setOrderItem(orderItem);

                        // Fetch the Customization object using its ID
                        Customization customizationEntity = customizationRepository.findById(customization.getId())
                                .orElseThrow(() -> new RuntimeException("Customization not found"));

                        orderItemCustomization.setCustomization(customizationEntity);

                        // Save OrderItemCustomization
                        orderItemCustomizationRepository.save(orderItemCustomization);
                    }
                }
            }
        }

        // 3. Update the order’s totalAmount
        newOrder.setTotalAmount(total);
        newOrder = orderRepository.save(newOrder);

        // Return the completed order object
        return newOrder;
    }

    @GetMapping("/{orderId}")
    public Map<String, Object> getOrderDetails(@PathVariable Long orderId) {
        // 1. Fetch the Order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // 2. Fetch all OrderItems for this Order
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);

        // 3. Construct a custom response
        Map<String, Object> response = new HashMap<>();
        response.put("order", order);
        response.put("orderItems", orderItems);

        return response;
    }
}
