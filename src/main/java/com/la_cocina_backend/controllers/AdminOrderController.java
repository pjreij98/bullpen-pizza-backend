package com.la_cocina_backend.controllers;

import com.la_cocina_backend.models.*;
import com.la_cocina_backend.repositories.MenuItemRepository;
import com.la_cocina_backend.repositories.OrderItemCustomizationRepository;
import com.la_cocina_backend.repositories.OrderItemRepository;
import com.la_cocina_backend.repositories.OrderRepository;
import com.la_cocina_backend.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private OrderItemCustomizationRepository orderItemCustomizationRepository;

    @Autowired
    private EmailService emailService;

    @PutMapping("/{orderId}/ready")
    public ResponseEntity<?> markOrderAsReady(@PathVariable Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Update the order status to COMPLETED (or READY)
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);

        // Send email notification to the customer
        String customerEmail = order.getCustomerEmail();
        String subject = "Your Order is Ready for Pickup!";
        String body = "Dear " + order.getCustomerName() + ",\n\n"
                + "Your order #" + order.getId() + " is now ready for pickup.\n\n"
                + "Please visit us to collect your order. Thank you for choosing La Cocina!\n\n"
                + "Best Regards,\nLa Cocina Team";

        emailService.sendEmail(customerEmail, subject, body);

        return ResponseEntity.ok("Order marked as ready and email sent to customer.");
    }


    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getPendingOrdersWithDetails() {
        List<Order> pendingOrders = orderRepository.findByStatusNotOrderById(OrderStatus.COMPLETED);

        List<Map<String, Object>> orderDetailsList = pendingOrders.stream().map(order -> {
            Map<String, Object> orderDetails = new HashMap<>();
            orderDetails.put("id", order.getId());
            orderDetails.put("orderDate", order.getOrderDate());
            orderDetails.put("customerName", order.getCustomerName());
            orderDetails.put("customerEmail", order.getCustomerEmail());
            orderDetails.put("status", order.getStatus());
            orderDetails.put("paymentStatus", order.getPaymentStatus());
            orderDetails.put("subTotal", order.getSubTotal());
            orderDetails.put("serviceFee", order.getServiceFee());
            orderDetails.put("totalAmount", order.getTotalAmount());
            orderDetails.put("specialNotes", order.getSpecialNotes());

            // Fetch Order Items
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
            List<Map<String, Object>> itemsList = orderItems.stream().map(item -> {
                Map<String, Object> itemDetails = new HashMap<>();
                MenuItem menuItem = menuItemRepository.findById(item.getMenuItemId())
                        .orElseThrow(() -> new RuntimeException("Menu item not found"));

                itemDetails.put("name", menuItem.getName());
                itemDetails.put("quantity", item.getQuantity());
                itemDetails.put("price", menuItem.getPrice());

                // Fetch Customizations
                List<OrderItemCustomization> customizations = orderItemCustomizationRepository.findByOrderItemId(item.getId());
                List<Map<String, Object>> customizationsList = customizations.stream().map(customization -> {
                    Map<String, Object> customizationDetails = new HashMap<>();
                    customizationDetails.put("name", customization.getCustomization().getName());
                    customizationDetails.put("price", customization.getCustomization().getPrice());
                    return customizationDetails;
                }).toList();

                itemDetails.put("customizations", customizationsList);
                return itemDetails;
            }).toList();

            orderDetails.put("items", itemsList);
            return orderDetails;
        }).toList();

        return ResponseEntity.ok(orderDetailsList);
    }


}
