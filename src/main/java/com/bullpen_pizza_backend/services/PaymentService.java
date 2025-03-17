package com.bullpen_pizza_backend.services;

import com.bullpen_pizza_backend.dto.PaymentRequest;
import com.bullpen_pizza_backend.models.*;
import com.bullpen_pizza_backend.repositories.*;
import jakarta.mail.MessagingException;
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

    @Value("${spring.mail.username}")
    private String adminEmail;

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



    public void confirmPayment(String transactionId, Long orderId) throws MessagingException, MessagingException {
        // Fetch the payment and order (unchanged)
        Payment payment = paymentRepository.findByTransactionId(transactionId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Update payment status (unchanged)
        payment.setStatus(PaymentStatus.PAID);
        paymentRepository.save(payment);

        // Update order status (unchanged)
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order);

        // Fetch all OrderItems for this Order (unchanged)
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

        // Base time and calculations (unchanged)
        LocalDateTime orderTime = order.getOrderDate();
        int baseTime = 15; // 15 minutes base preparation time
        int extraTime = 0;

        // Count total items and customizations (unchanged)
        int totalItems = orderItems.stream().mapToInt(OrderItem::getQuantity).sum();
        int totalCustomizations = orderItems.stream()
                .mapToInt(item -> orderItemCustomizationRepository.findByOrderItemId(item.getId()).size())
                .sum();

        // Add extra time based on order size (unchanged)
        if (totalItems > 5) {
            extraTime += 10;
        }
        if (totalItems > 10) {
            extraTime += 20;
        }

        // Add extra time for each customization (2 min per customization) (unchanged)
        extraTime += totalCustomizations * 2;

        // Add extra time for peak hours (12 PM - 2 PM, 6 PM - 8 PM) (unchanged)
        int orderHour = orderTime.getHour();
        if ((orderHour >= 12 && orderHour < 14) || (orderHour >= 18 && orderHour < 20)) {
            extraTime += 20;
        }

        // Calculate pickup time range (add breathing room of 10 minutes) (unchanged)
        LocalDateTime estimatedPickupMin = orderTime.plusMinutes(baseTime + extraTime);
        LocalDateTime estimatedPickupMax = estimatedPickupMin.plusMinutes(10);

        // Format pickup time range (unchanged)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");
        String formattedPickupRange = estimatedPickupMin.format(formatter) + " - " + estimatedPickupMax.format(formatter);

        // Build HTML email content for order details (replacing plain-text StringBuilder)
        StringBuilder orderDetailsHtml = new StringBuilder();
        orderDetailsHtml.append("<table style='width: 100%; border-collapse: collapse; font-family: Arial, sans-serif;'>");
        orderDetailsHtml.append("<tr><td style='padding: 8px; font-weight: bold;'>Customer Email:</td><td style='padding: 8px;'>")
                .append(order.getCustomerEmail()).append("</td></tr>");
        orderDetailsHtml.append("<tr><td style='padding: 8px; font-weight: bold;'>Order ID:</td><td style='padding: 8px;'>#")
                .append(order.getId()).append("</td></tr>");
        orderDetailsHtml.append("<tr><td style='padding: 8px; font-weight: bold;'>Payment Status:</td><td style='padding: 8px;'>")
                .append(order.getPaymentStatus()).append("</td></tr>");
        orderDetailsHtml.append("<tr><td style='padding: 8px; font-weight: bold;'>Order Details:</td><td style='padding: 8px;'></td></tr>");

        // Add item details to the HTML table
        for (OrderItem item : orderItems) {
            MenuItem menuItem = menuItemRepository.findById(item.getMenuItemId())
                    .orElseThrow(() -> new RuntimeException("Menu item not found"));

            orderDetailsHtml.append("<tr><td style='padding: 8px 8px 8px 16px;' colspan='2'>")
                    .append("- ").append(menuItem.getName())
                    .append(" x ").append(item.getQuantity())
                    .append(" @ $").append(String.format("%.2f", menuItem.getPrice())).append(" each</td></tr>");

            // Fetch and append customizations for this OrderItem
            List<OrderItemCustomization> selectedCustomizations = orderItemCustomizationRepository.findByOrderItemId(item.getId());
            for (OrderItemCustomization customization : selectedCustomizations) {
                Customization customizationDetails = customization.getCustomization();
                orderDetailsHtml.append("<tr><td style='padding: 4px 4px 4px 32px;' colspan='2'>")
                        .append("&#x2022; ").append(customizationDetails.getName())
                        .append(" (+ $").append(String.format("%.2f", customizationDetails.getPrice())).append(")</td></tr>");
            }
        }

        orderDetailsHtml.append("<tr><td style='padding: 8px; font-weight: bold;'>Subtotal:</td><td style='padding: 8px;'>$")
                .append(String.format("%.2f", order.getSubTotal())).append("</td></tr>");
        orderDetailsHtml.append("<tr><td style='padding: 8px; font-weight: bold;'>Service Fee:</td><td style='padding: 8px;'>$")
                .append(String.format("%.2f", order.getServiceFee())).append("</td></tr>");
        orderDetailsHtml.append("<tr><td style='padding: 8px; font-weight: bold;'>Taxes:</td><td style='padding: 8px;'>$")
                .append(String.format("%.2f", order.getTax())).append("</td></tr>");
        orderDetailsHtml.append("<tr><td style='padding: 8px; font-weight: bold;'>Total Amount:</td><td style='padding: 8px;'>$")
                .append(String.format("%.2f", order.getTotalAmount())).append("</td></tr>");
        orderDetailsHtml.append("<tr><td style='padding: 8px; font-weight: bold;'>Estimated Pickup Time:</td><td style='padding: 8px;'>")
                .append(formattedPickupRange).append("</td></tr>");
        orderDetailsHtml.append("</table>");

        // Prepare HTML email templates for customer and admin
        String customerEmail = order.getCustomerEmail();
        String customerSubject = "Order Confirmation: Payment Successful - Bullpen Pizza";
        String adminSubject = "New Paid Order Received - Bullpen Pizza";

        // Customer HTML email
        String customerHtmlBody = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "</head>" +
                "<body style='font-family: Arial, sans-serif; margin: 0; padding: 0; background-color: #f4f4f4;'>" +
                "<div style='max-width: 600px; margin: 20px auto; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1);'>" +
                "<div style='background-color: #2ecc71; padding: 20px; text-align: center; border-top-left-radius: 8px; border-top-right-radius: 8px;'>" +
                "<h1 style='color: #ffffff; margin: 0;'>Order Confirmation</h1>" +
                "</div>" +
                "<div style='padding: 20px;'>" +
                "<p>Dear " + order.getCustomerName() + ",</p>" +
                "<p>Thank you for your order at Bullpen Pizza. Here are your order details:</p>" +
                orderDetailsHtml.toString() +
                "<p><strong>Special Note:</strong> " + (order.getSpecialNotes() != null ? order.getSpecialNotes() : "None") + "</p>" +
                "<p>We look forward to serving you soon!</p>" +
                "<p style='margin-top: 20px;'>Best Regards,<br>Bullpen Pizza Team</p>" +
                "</div>" +
                "<div style='background-color: #f1f1f1; padding: 10px; text-align: center; border-bottom-left-radius: 8px; border-bottom-right-radius: 8px;'>" +
                "<p style='color: #777; font-size: 12px; margin: 0;'>© 2025 Bullpen Pizza. All rights reserved.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";

        // Admin HTML email
        String adminHtmlBody = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "</head>" +
                "<body style='font-family: Arial, sans-serif; margin: 0; padding: 0; background-color: #f4f4f4;'>" +
                "<div style='max-width: 600px; margin: 20px auto; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1);'>" +
                "<div style='background-color: #e74c3c; padding: 20px; text-align: center; border-top-left-radius: 8px; border-top-right-radius: 8px;'>" +
                "<h1 style='color: #ffffff; margin: 0;'>New Paid Order</h1>" +
                "</div>" +
                "<div style='padding: 20px;'>" +
                "<p>A new order has been placed by " + order.getCustomerName() + "!</p>" +
                "<p>Here are the order details:</p>" +
                orderDetailsHtml.toString() +
                "<p><strong>Special Note:</strong> " + (order.getSpecialNotes() != null ? order.getSpecialNotes() : "None") + "</p>" +
                "<p>Please prepare the order promptly.</p>" +
                "<p style='margin-top: 20px;'>Best Regards,<br>Bullpen Pizza Team</p>" +
                "</div>" +
                "<div style='background-color: #f1f1f1; padding: 10px; text-align: center; border-bottom-left-radius: 8px; border-bottom-right-radius: 8px;'>" +
                "<p style='color: #777; font-size: 12px; margin: 0;'>© 2025 Bullpen Pizza. All rights reserved.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";

        // Send HTML emails
        try {
            emailService.sendHtmlEmail(customerEmail, customerSubject, customerHtmlBody);
            emailService.sendHtmlEmail(adminEmail, adminSubject, adminHtmlBody);
        } catch (MessagingException e) {
            // Log the error (e.g., using SLF4J or similar)
            System.err.println("Failed to send email: " + e.getMessage());
        }
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