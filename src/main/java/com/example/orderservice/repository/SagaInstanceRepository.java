package com.example.orderservice.repository;

import com.example.orderservice.model.SagaInstance;
import com.example.orderservice.model.SagaState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SagaInstanceRepository extends MongoRepository<SagaInstance, String> {
    
    Optional<SagaInstance> findBySagaId(String sagaId);
    
    Optional<SagaInstance> findByOrderId(String orderId);
    
    List<SagaInstance> findByCurrentState(SagaState state);
}