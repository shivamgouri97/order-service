package com.example.orderservice.service;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final SagaOrchestratorService sagaOrchestratorService;
    
    @Transactional
    public Order createOrder(String userId, CreateOrderRequest request) {
        log.info("Creating order for user: {}", userId);
        
        // Calculate total amount
        BigDecimal totalAmount = request.getItems().stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Create order items
        List<OrderItem> orderItems = request.getItems().stream()
            .map(item -> OrderItem.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .build())
            .collect(Collectors.toList());
        
        // Create shipping address
        Order.ShippingAddress shippingAddress = Order.ShippingAddress.builder()
            .street(request.getShippingAddress().getStreet())
            .city(request.getShippingAddress().getCity())
            .state(request.getShippingAddress().getState())
            .zipCode(request.getShippingAddress().getZipCode())
            .country(request.getShippingAddress().getCountry())
            .build();
        
        // Create order
        Order order = Order.builder()
            .userId(userId)
            .items(orderItems)
            .totalAmount(totalAmount)
            .status(OrderStatus.PENDING)
            .paymentMethod(request.getPaymentMethod())
            .shippingAddress(shippingAddress)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        
        // Start Saga
        order = sagaOrchestratorService.startSaga(order);
        
        log.info("Order created successfully: {}", order.getId());
        return order;
    }
    
    public Optional<Order> getOrderById(String orderId) {
        return orderRepository.findById(orderId);
    }
    
    public List<Order> getOrdersByUserId(String userId) {
        return orderRepository.findByUserId(userId);
    }
    
    public List<Order> getOrdersByUserIdAndStatus(String userId, OrderStatus status) {
        return orderRepository.findByUserIdAndStatus(userId, status);
    }
    
    @Transactional
    public Order cancelOrder(String orderId, String reason) {
        log.info("Cancelling order: {}", orderId);
        
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            throw new RuntimeException("Cannot cancel confirmed order");
        }
        
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new RuntimeException("Order already cancelled");
        }
        
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        order.setCancelledAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        
        return orderRepository.save(order);
    }
    
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}