package com.example.orderservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "saga_instances")
public class SagaInstance {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String sagaId;
    
    @Indexed
    private String orderId;
    
    private SagaState currentState;
    
    private String orderData;
    
    private String compensationData;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private LocalDateTime completedAt;
    
    @Indexed(expireAfterSeconds = 86400) // 24 hours TTL
    private LocalDateTime expiresAt;
}