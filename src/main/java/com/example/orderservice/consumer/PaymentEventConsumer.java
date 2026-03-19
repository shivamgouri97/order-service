package com.example.orderservice.consumer;

import com.example.orderservice.dto.PaymentProcessedEvent;
import com.example.orderservice.service.SagaOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {
    
    private final SagaOrchestratorService sagaOrchestratorService;
    
    @KafkaListener(
        topics = "payment-events",
        groupId = "order-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentProcessedEvent(
            @Payload PaymentProcessedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("Received PaymentProcessedEvent from topic: {}, partition: {}, offset: {}", 
                 topic, partition, offset);
        log.info("Payment ID: {}, Order ID: {}, Status: {}, Saga ID: {}", 
                 event.getPaymentId(), event.getOrderId(), event.getStatus(), event.getSagaId());
        
        try {
            // Handle payment processed event
            sagaOrchestratorService.handlePaymentProcessed(event);
            
            // Manually acknowledge the message
            acknowledgment.acknowledge();
            log.info("Successfully processed and acknowledged payment event for order: {}", 
                     event.getOrderId());
            
        } catch (Exception e) {
            log.error("Error processing payment event for order: {}", event.getOrderId(), e);
            // Don't acknowledge - message will be retried
            throw e;
        }
    }
}