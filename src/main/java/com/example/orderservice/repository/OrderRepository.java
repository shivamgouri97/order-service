package com.example.orderservice.repository;

import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {
    
    List<Order> findByUserId(String userId);
    
    List<Order> findByUserIdAndStatus(String userId, OrderStatus status);
    
    Optional<Order> findBySagaId(String sagaId);
    
    List<Order> findByStatus(OrderStatus status);
}