package com.la_cocina_backend.services;

import com.la_cocina_backend.dto.PaymentRequest;
import com.la_cocina_backend.models.*;
import com.la_cocina_backend.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    @Autowired
    private EmailService emailService;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Autowired
    OrderItemCustomizationRepository orderItemCustomizationRepository;

    @Autowired
    MenuItemRepository menuItemRepository;

    @Autowired
    CustomizationRepository customizationRepository;

    @Autowired
    private OrderRepository orderRepository;

    // Read your Stripe secret key from application.yml or env var
    @Value("${stripe.secretKey}")
    private String stripeSecretKey;

    /**
     * Create a PaymentIntent in Stripe
     */
    public Map<String, Object> createStripePaymentIntent(Order order, PaymentRequest request) {
        try {
            // 1. Configure Stripe
            Stripe.apiKey = stripeSecretKey;

            // 2. Convert BigDecimal to long for Stripe (in cents)
            long amountInCents = request.getAmount()
                    .multiply(new BigDecimal("100")).longValue();

            // 3. Create PaymentIntent
            PaymentIntentCreateParams params =
                    PaymentIntentCreateParams.builder()
                            .setAmount(amountInCents)
                            .setCurrency(request.getCurrency())
                            .setDescription(request.getDescription())
                            // Example: pass orderId in metadata
                            .putMetadata("orderId", order.getId().toString())
                            .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            // 4. Create a Payment record (status = INITIATED)
            Payment payment = new Payment();
            payment.setOrderId(order.getId());
            payment.setAmount(request.getAmount());
            payment.setPaymentMethod("STRIPE");
            payment.setTransactionId(paymentIntent.getId()); // store PaymentIntent ID
            payment.setStatus(PaymentStatus.INITIATED);
            paymentRepository.save(payment);


//            // Fetch all OrderItems for this Order
//            List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
//
//            // Construct a custom response
//            Map<String, Object> res = new HashMap<>();
//            res.put("order", order);
//            res.put("orderItems", orderItems);
//            // Send emails
//            String customerEmail = order.getCustomerEmail(); // Assuming your order object has this field
//            String adminEmail = "pjreij98@gmail.com"; // Replace with the restaurant admin email
//
//            String customerSubject = "Order Confirmation - La Cocina";
//            String customerBody = "Thank you for your order! Your order ID is #" + order.getId() + ".\n\n"
//                    + "Details:\n"
//                    + res.toString() + "\n"
//                    + "Total: $" + order.getTotalAmount();
//
//            String adminSubject = "New Order Received - La Cocina";
//            String adminBody = "A new order has been placed by " + order.getCustomerName() + "!\n\n"
//                    + "Order ID: #" + order.getId() + "\n"
//                    + "Details:\n"
//                    + res.toString() + "\n"
//                    + "Total: $" + order.getTotalAmount();
//
//            // Send emails
//            emailService.sendEmail(customerEmail, customerSubject, customerBody);
//            emailService.sendEmail(adminEmail, adminSubject, adminBody);


            // 5. Return clientSecret to frontend
            Map<String, Object> response = new HashMap<>();
            response.put("clientSecret", paymentIntent.getClientSecret());
            response.put("paymentIntentId", paymentIntent.getId());
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error creating Stripe PaymentIntent", e);
        }
    }



    public void confirmPayment(String transactionId, Long orderId) {
        // Fetch the payment and order
        Payment payment = paymentRepository.findByTransactionId(transactionId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Update payment status
        payment.setStatus(PaymentStatus.PAID);
        paymentRepository.save(payment);

        // Update order status
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order);

        // Fetch all OrderItems for this Order
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

        // Base time and calculations
        LocalDateTime orderTime = order.getOrderDate();
        int baseTime = 15; // 15 minutes base preparation time
        int extraTime = 0;

        // Count total items and customizations
        int totalItems = orderItems.stream().mapToInt(OrderItem::getQuantity).sum();
        int totalCustomizations = orderItems.stream()
                .mapToInt(item -> orderItemCustomizationRepository.findByOrderItemId(item.getId()).size())
                .sum();

        // Add extra time based on order size
        if (totalItems > 5) {
            extraTime += 10;
        }
        if (totalItems > 10) {
            extraTime += 20;
        }

        // Add extra time for each customization (2 min per customization)
        extraTime += totalCustomizations * 2;

        // Add extra time for peak hours (12 PM - 2 PM, 6 PM - 8 PM)
        int orderHour = orderTime.getHour();
        if ((orderHour >= 12 && orderHour < 14) || (orderHour >= 18 && orderHour < 20)) {
            extraTime += 20;
        }

        // Calculate pickup time range (add breathing room of 10 minutes)
        LocalDateTime estimatedPickupMin = orderTime.plusMinutes(baseTime + extraTime);
        LocalDateTime estimatedPickupMax = estimatedPickupMin.plusMinutes(10);

        // Format pickup time range
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");
        String formattedPickupRange = estimatedPickupMin.format(formatter) + " - " + estimatedPickupMax.format(formatter);


        // Build formatted email content
        StringBuilder orderDetails = new StringBuilder();
        orderDetails.append("Customer Email: ").append(order.getCustomerEmail()).append("\n");
        orderDetails.append("Order ID: #").append(order.getId()).append("\n");
        orderDetails.append("Payment Status: ").append(order.getPaymentStatus()).append("\n");
        orderDetails.append("Order Details:\n");

        // Add item details to the email body
        for (OrderItem item : orderItems) {
            MenuItem menuItem = menuItemRepository.findById(item.getMenuItemId())
                    .orElseThrow(() -> new RuntimeException("Menu item not found"));

            orderDetails.append("- ").append(menuItem.getName())
                    .append(" x ").append(item.getQuantity())
                    .append(" @ $").append(menuItem.getPrice()).append(" each\n");

            // Fetch and append customizations for this OrderItem
            List<OrderItemCustomization> selectedCustomizations = orderItemCustomizationRepository.findByOrderItemId(item.getId());
            for (OrderItemCustomization customization : selectedCustomizations) {
                Customization customizationDetails = customization.getCustomization();
                orderDetails.append("    * ").append(customizationDetails.getName())
                        .append(" (+ $").append(customizationDetails.getPrice()).append(")\n");
            }
        }

        orderDetails.append("\nSubtotal: $").append(order.getSubTotal()).append("\n");
        orderDetails.append("Service Fee: $").append(order.getServiceFee()).append("\n");
        orderDetails.append("\nTotal Amount: $").append(order.getTotalAmount()).append("\n");
        orderDetails.append("Estimated Pickup Time: ").append(formattedPickupRange).append("\n");


        // Prepare email contents
        String customerEmail = order.getCustomerEmail();
        String adminEmail = "pjreij98@gmail.com"; // Replace with admin email

        String customerSubject = "Order Confirmation: Payment Successful - La Cocina";
        String adminSubject = "New Paid Order Received - La Cocina";

        String customerBody = "Dear " + order.getCustomerName() + ",\n\n"
                + "Thank you for your order at La Cocina. Here are your order details:\n\n"
                + orderDetails.toString()
                + "Special Note: " + order.getSpecialNotes()
                + "\n\nWe look forward to serving you soon!\n\n"
                + "Best Regards,\nLa Cocina Team";

        String adminBody = "A new order has been placed by " + order.getCustomerName() + "!\n\n"
                + "Here are the order details:\n\n"
                + orderDetails.toString()
                + "Special Note: " + order.getSpecialNotes()
                + "\n\nPlease prepare the order promptly.\n\n"
                + "Best Regards,\nLa Cocina Team";

        // Send emails
        emailService.sendEmail(customerEmail, customerSubject, customerBody);
        emailService.sendEmail(adminEmail, adminSubject, adminBody);
    }


    /**
     * Example: Create or get a PayPal order/approval link
     */
//    public Map<String, Object> createPayPalOrder(Order order, PaymentRequest request) {
//        // 1. Use PayPal SDK to create an order. For example:
//        //    - Build a Payment object in PayPal.
//        //    - Return the approval link to the frontend.
//        // This code is pseudo-code. Real usage requires PayPal OAuth or SDK calls.
//        Map<String, Object> result = new HashMap<>();
//        try {
//            // Pseudo:
//            // PayPalClient client = new PayPalClient("clientId", "secret");
//            // Payment payPalPayment = new Payment();
//            // payPalPayment.setIntent("sale");
//            // ...
//            // Approval URL -> user is redirected
//            String approvalUrl = "https://www.sandbox.paypal.com/checkoutnow?token=EXAMPLE123";
//
//            // 2. Save a Payment record in DB
//            Payment payment = new Payment();
//            payment.setOrderId(order.getId());
//            payment.setAmount(request.getAmount());
//            payment.setPaymentMethod("PAYPAL");
//            payment.setTransactionId("PAYPAL_TEMP_ID"); // or the token
//            payment.setStatus(PaymentStatus.INITIATED);
//            paymentRepository.save(payment);
//
//            // 3. Return the link
//            result.put("approvalLink", approvalUrl);
//            return result;
//        } catch (Exception e) {
//            e.printStackTrace();
//            throw new RuntimeException("Error creating PayPal order", e);
//        }
//    }

    // Additional methods for confirming payments, webhooks, etc.
}