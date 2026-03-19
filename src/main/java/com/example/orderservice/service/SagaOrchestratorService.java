package com.example.orderservice.service;

import com.example.orderservice.dto.OrderCreatedEvent;
import com.example.orderservice.dto.PaymentProcessedEvent;
import com.example.orderservice.model.*;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.SagaInstanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestratorService {
    
    private final OrderRepository orderRepository;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String ORDER_EVENTS_TOPIC = "order-events";
    
    @Transactional
    public Order startSaga(Order order) {
        log.info("Starting Saga for order: {}", order.getId());
        
        // Generate Saga ID
        String sagaId = UUID.randomUUID().toString();
        order.setSagaId(sagaId);
        order.setStatus(OrderStatus.PENDING);
        
        // Save order
        order = orderRepository.save(order);
        
        // Create Saga instance
        SagaInstance sagaInstance = createSagaInstance(order, sagaId);
        sagaInstanceRepository.save(sagaInstance);
        
        // Publish OrderCreatedEvent
        publishOrderCreatedEvent(order);
        
        // Update Saga state to PAYMENT_PENDING
        updateSagaState(sagaId, SagaState.PAYMENT_PENDING);
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        orderRepository.save(order);
        
        log.info("Saga started successfully for order: {}, sagaId: {}", order.getId(), sagaId);
        return order;
    }
    
    @Transactional
    public void handlePaymentProcessed(PaymentProcessedEvent event) {
        log.info("Handling payment processed event for order: {}, status: {}", 
                 event.getOrderId(), event.getStatus());
        
        Order order = orderRepository.findById(event.getOrderId())
            .orElseThrow(() -> new RuntimeException("Order not found: " + event.getOrderId()));
        
        SagaInstance sagaInstance = sagaInstanceRepository.findBySagaId(event.getSagaId())
            .orElseThrow(() -> new RuntimeException("Saga not found: " + event.getSagaId()));
        
        if ("SUCCESS".equals(event.getStatus())) {
            // Payment successful - complete saga
            completeSaga(order, sagaInstance, event);
        } else {
            // Payment failed - compensate
            compensateSaga(order, sagaInstance, event);
        }
    }
    
    private void completeSaga(Order order, SagaInstance sagaInstance, PaymentProcessedEvent event) {
        log.info("Completing Saga for order: {}", order.getId());
        
        // Update order status
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentId(event.getPaymentId());
        order.setConfirmedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        
        // Update saga state
        sagaInstance.setCurrentState(SagaState.COMPLETED);
        sagaInstance.setCompletedAt(LocalDateTime.now());
        sagaInstance.setUpdatedAt(LocalDateTime.now());
        sagaInstanceRepository.save(sagaInstance);
        
        log.info("Saga completed successfully for order: {}", order.getId());
    }
    
    private void compensateSaga(Order order, SagaInstance sagaInstance, PaymentProcessedEvent event) {
        log.info("Compensating Saga for order: {} due to payment failure", order.getId());
        
        // Update saga state to compensating
        sagaInstance.setCurrentState(SagaState.COMPENSATING);
        sagaInstance.setUpdatedAt(LocalDateTime.now());
        
        try {
            // Store compensation data
            sagaInstance.setCompensationData(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Error storing compensation data", e);
        }
        
        sagaInstanceRepository.save(sagaInstance);
        
        // Cancel order
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason("Payment failed: " + event.getFailureReason());
        order.setCancelledAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        
        // Update saga state to failed
        sagaInstance.setCurrentState(SagaState.FAILED);
        sagaInstance.setCompletedAt(LocalDateTime.now());
        sagaInstance.setUpdatedAt(LocalDateTime.now());
        sagaInstanceRepository.save(sagaInstance);
        
        log.info("Saga compensation completed for order: {}", order.getId());
    }
    
    private SagaInstance createSagaInstance(Order order, String sagaId) {
        try {
            return SagaInstance.builder()
                .sagaId(sagaId)
                .orderId(order.getId())
                .currentState(SagaState.STARTED)
                .orderData(objectMapper.writeValueAsString(order))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        } catch (Exception e) {
            log.error("Error creating saga instance", e);
            throw new RuntimeException("Failed to create saga instance", e);
        }
    }
    
    private void publishOrderCreatedEvent(Order order) {
        String idempotencyKey = UUID.randomUUID().toString();
        
        OrderCreatedEvent event = OrderCreatedEvent.builder()
            .orderId(order.getId())
            .userId(order.getUserId())
            .items(order.getItems().stream()
                .map(item -> OrderCreatedEvent.OrderItem.builder()
                    .productId(item.getProductId())
                    .productName(item.getProductName())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .build())
                .collect(Collectors.toList()))
            .totalAmount(order.getTotalAmount())
            .sagaId(order.getSagaId())
            .idempotencyKey(idempotencyKey)
            .paymentMethod(order.getPaymentMethod())
            .timestamp(LocalDateTime.now())
            .build();
        
        kafkaTemplate.send(ORDER_EVENTS_TOPIC, order.getId(), event);
        log.info("Published OrderCreatedEvent for order: {}", order.getId());
    }
    
    private void updateSagaState(String sagaId, SagaState newState) {
        SagaInstance sagaInstance = sagaInstanceRepository.findBySagaId(sagaId)
            .orElseThrow(() -> new RuntimeException("Saga not found: " + sagaId));
        
        sagaInstance.setCurrentState(newState);
        sagaInstance.setUpdatedAt(LocalDateTime.now());
        sagaInstanceRepository.save(sagaInstance);
        
        log.info("Updated Saga state to {} for sagaId: {}", newState, sagaId);
    }
}