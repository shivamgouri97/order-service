package com.example.orderservice.model;

public enum SagaState {
    STARTED,
    PAYMENT_PENDING,
    COMPLETED,
    COMPENSATING,
    FAILED
}