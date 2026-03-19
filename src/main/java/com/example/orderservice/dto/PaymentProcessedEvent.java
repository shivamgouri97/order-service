package com.example.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProcessedEvent {
    
    private String paymentId;
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String status; // SUCCESS or FAILED
    private String transactionId;
    private String sagaId;
    private String failureReason;
    private LocalDateTime timestamp;
}