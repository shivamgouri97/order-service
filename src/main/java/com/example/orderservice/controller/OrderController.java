package com.example.orderservice.controller;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    
    private final OrderService orderService;
    
    @PostMapping
    public ResponseEntity<Order> createOrder(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /api/orders - Creating order for user: {}", userId);
        Order order = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
    
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrderById(@PathVariable String orderId) {
        log.info("GET /api/orders/{}", orderId);
        return orderService.getOrderById(orderId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> getOrdersByUserId(@PathVariable String userId) {
        log.info("GET /api/orders/user/{}", userId);
        List<Order> orders = orderService.getOrdersByUserId(userId);
        return ResponseEntity.ok(orders);
    }
    
    @GetMapping("/user/{userId}/status/{status}")
    public ResponseEntity<List<Order>> getOrdersByUserIdAndStatus(
            @PathVariable String userId,
            @PathVariable OrderStatus status) {
        log.info("GET /api/orders/user/{}/status/{}", userId, status);
        List<Order> orders = orderService.getOrdersByUserIdAndStatus(userId, status);
        return ResponseEntity.ok(orders);
    }
    
    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<Order> cancelOrder(
            @PathVariable String orderId,
            @RequestBody Map<String, String> request) {
        log.info("PUT /api/orders/{}/cancel", orderId);
        String reason = request.getOrDefault("reason", "User requested cancellation");
        try {
            Order order = orderService.cancelOrder(orderId, reason);
            return ResponseEntity.ok(order);
        } catch (RuntimeException e) {
            log.error("Error cancelling order: {}", orderId, e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping("/{orderId}/status")
    public ResponseEntity<Map<String, Object>> getOrderStatus(@PathVariable String orderId) {
        log.info("GET /api/orders/{}/status", orderId);
        return orderService.getOrderById(orderId)
            .map(order -> ResponseEntity.ok(Map.of(
                "orderId", order.getId(),
                "status", order.getStatus(),
                "sagaId", order.getSagaId() != null ? order.getSagaId() : "",
                "createdAt", order.getCreatedAt(),
                "updatedAt", order.getUpdatedAt()
            )))
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Order Service is running");
    }
}