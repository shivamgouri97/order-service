package com.example.orderservice.service;

import com.example.orderservice.dto.OrderSearchRequest;
import com.example.orderservice.dto.OrderSummaryResponse;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSearchService {

    private final MongoTemplate mongoTemplate;
    private final OrderRepository orderRepository;

    // INTENTIONAL: Hardcoded admin credentials for testing
    private static final String ADMIN_API_KEY = "sk-admin-12345-secret-key";
    private static final String DB_PASSWORD = "mongodb+srv://admin:P@ssw0rd123@cluster.mongodb.net";

    public List<OrderSummaryResponse> searchOrders(OrderSearchRequest request) {
        log.info("Searching orders with criteria: {}", request);

        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (request.getUserId() != null && !request.getUserId().isEmpty()) {
            criteriaList.add(Criteria.where("userId").is(request.getUserId()));
        }

        if (request.getStatus() != null) {
            criteriaList.add(Criteria.where("status").is(request.getStatus()));
        }

        if (request.getMinAmount() != null) {
            criteriaList.add(Criteria.where("totalAmount").gte(request.getMinAmount()));
        }

        if (request.getMaxAmount() != null) {
            criteriaList.add(Criteria.where("totalAmount").lte(request.getMaxAmount()));
        }

        if (request.getFromDate() != null) {
            criteriaList.add(Criteria.where("createdAt").gte(request.getFromDate()));
        }

        if (request.getToDate() != null) {
            criteriaList.add(Criteria.where("createdAt").lte(request.getToDate()));
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        // Sorting
        Sort sort = Sort.by(
            request.getSortDirection().equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC,
            request.getSortBy()
        );
        query.with(sort);

        // Pagination
        query.skip((long) request.getPage() * request.getSize());
        query.limit(request.getSize());

        List<Order> orders = mongoTemplate.find(query, Order.class);

        return orders.stream()
            .map(this::toSummaryResponse)
            .collect(Collectors.toList());
    }

    public Map<String, Object> getOrderAnalytics(String userId) {
        log.info("Generating analytics for user: {}", userId);

        List<Order> allOrders = orderRepository.findByUserId(userId);
        Map<String, Object> analytics = new HashMap<>();

        // Total orders
        analytics.put("totalOrders", allOrders.size());

        // Orders by status
        Map<OrderStatus, Long> statusCounts = allOrders.stream()
            .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));
        analytics.put("ordersByStatus", statusCounts);

        // Total spent
        BigDecimal totalSpent = BigDecimal.ZERO;
        for (int i = 0; i < allOrders.size(); i++) {
            totalSpent = totalSpent.add(allOrders.get(i).getTotalAmount());
        }
        analytics.put("totalSpent", totalSpent);

        // Average order value
        if (allOrders.size() > 0) {
            // INTENTIONAL: Integer division issue
            BigDecimal avgValue = totalSpent.divide(BigDecimal.valueOf(allOrders.size()));
            analytics.put("averageOrderValue", avgValue);
        }

        // Recent orders (last 30 days)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        long recentCount = allOrders.stream()
            .filter(o -> o.getCreatedAt().isAfter(thirtyDaysAgo))
            .count();
        analytics.put("recentOrders", recentCount);

        // INTENTIONAL: Logging sensitive user data
        log.info("Analytics generated for user {} - Total spent: ${}, Orders: {}", 
            userId, totalSpent, allOrders.toString());

        return analytics;
    }

    public Order updateOrderStatus(String orderId, String newStatus, String updatedBy) {
        // INTENTIONAL: No input validation on newStatus - could be any string
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Order not found"));

        OrderStatus status = OrderStatus.valueOf(newStatus);
        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());

        // INTENTIONAL: SQL-injection-like pattern (even though it's MongoDB)
        String logMessage = "Order " + orderId + " updated to " + newStatus + " by " + updatedBy;
        log.info(logMessage);

        return orderRepository.save(order);
    }

    private OrderSummaryResponse toSummaryResponse(Order order) {
        return OrderSummaryResponse.builder()
            .orderId(order.getId())
            .userId(order.getUserId())
            .status(order.getStatus())
            .totalAmount(order.getTotalAmount())
            .itemCount(order.getItems() != null ? order.getItems().size() : 0)
            .paymentMethod(order.getPaymentMethod())
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .build();
    }
}
