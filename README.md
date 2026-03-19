# Order Service

## Overview
Order Service is the Saga Orchestrator in the distributed order management system. It coordinates the distributed transaction between order creation and payment processing using the Saga pattern.

## Features
- ✅ **Saga Orchestrator**: Coordinates distributed transactions
- ✅ **Event-Driven Architecture**: Publishes OrderCreatedEvent, consumes PaymentProcessedEvent
- ✅ **Compensation Logic**: Handles payment failures with order cancellation
- ✅ **Circuit Breaker**: Resilience4j for fault tolerance
- ✅ **Retry Mechanism**: Exponential backoff for transient failures
- ✅ **Service Discovery**: Registers with Eureka
- ✅ **Monitoring**: Prometheus metrics and health checks
- ✅ **MongoDB**: Document-based storage for orders and saga state

## Architecture

### Saga Pattern (Orchestrator)
```
Client → Order Service (Create Order)
            ↓
    Save Order (PENDING)
            ↓
    Create Saga Instance (STARTED)
            ↓
    Publish OrderCreatedEvent → Kafka
            ↓
    Update Status (PAYMENT_PENDING)
            ↓
    Wait for PaymentProcessedEvent
            ↓
    ┌─────────────────┬─────────────────┐
    ↓                 ↓                 ↓
SUCCESS           FAILED          TIMEOUT
    ↓                 ↓                 ↓
CONFIRMED         CANCELLED         FAILED
(Complete Saga)  (Compensate)    (Compensate)
```

### Saga State Machine
```
STARTED → PAYMENT_PENDING → COMPLETED (Success)
                    ↓
                COMPENSATING → FAILED (Payment Failed)
```

## API Endpoints

### Order APIs
- `POST /api/orders` - Create new order (starts Saga)
- `GET /api/orders/{orderId}` - Get order by ID
- `GET /api/orders/user/{userId}` - Get all orders for user
- `GET /api/orders/user/{userId}/status/{status}` - Get orders by status
- `PUT /api/orders/{orderId}/cancel` - Cancel order
- `GET /api/orders/{orderId}/status` - Get order status with history

### Health Check
- `GET /api/orders/health` - Service health check
- `GET /actuator/health` - Actuator health endpoint
- `GET /actuator/prometheus` - Prometheus metrics

## Configuration

### Application Properties
```yaml
Server Port: 8082
MongoDB: mongodb+srv://admin:admin@cluster0.39v1o.mongodb.net/orders_db
Kafka: localhost:9092
Eureka: http://localhost:8761/eureka/
```

### Environment Variables
- `SPRING_DATA_MONGODB_URI`: MongoDB connection string
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`: Kafka bootstrap servers
- `EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE`: Eureka server URL

## Data Model

### Order
```java
{
  "id": "string",
  "userId": "string",
  "items": [
    {
      "productId": "string",
      "productName": "string",
      "quantity": "number",
      "price": "decimal",
      "subtotal": "decimal"
    }
  ],
  "totalAmount": "decimal",
  "status": "PENDING|PAYMENT_PENDING|CONFIRMED|CANCELLED|FAILED",
  "sagaId": "string",
  "paymentId": "string",
  "paymentMethod": "string",
  "shippingAddress": {
    "street": "string",
    "city": "string",
    "state": "string",
    "zipCode": "string",
    "country": "string"
  },
  "cancelReason": "string",
  "createdAt": "datetime",
  "updatedAt": "datetime",
  "confirmedAt": "datetime",
  "cancelledAt": "datetime"
}
```

### SagaInstance
```java
{
  "id": "string",
  "sagaId": "string",
  "orderId": "string",
  "currentState": "STARTED|PAYMENT_PENDING|COMPLETED|COMPENSATING|FAILED",
  "orderData": "string (JSON)",
  "compensationData": "string (JSON)",
  "createdAt": "datetime",
  "updatedAt": "datetime",
  "completedAt": "datetime",
  "expiresAt": "datetime" // TTL: 24 hours
}
```

## Events

### Published Events
**OrderCreatedEvent** (to order-events topic)
```json
{
  "orderId": "string",
  "userId": "string",
  "items": [
    {
      "productId": "string",
      "productName": "string",
      "quantity": "number",
      "price": "decimal"
    }
  ],
  "totalAmount": "decimal",
  "sagaId": "string",
  "idempotencyKey": "string",
  "paymentMethod": "string",
  "timestamp": "datetime"
}
```

### Consumed Events
**PaymentProcessedEvent** (from payment-events topic)
```json
{
  "paymentId": "string",
  "orderId": "string",
  "userId": "string",
  "amount": "decimal",
  "currency": "string",
  "status": "SUCCESS|FAILED",
  "transactionId": "string",
  "sagaId": "string",
  "failureReason": "string",
  "timestamp": "datetime"
}
```

## Saga Flow

### Success Flow
1. Client creates order via POST /api/orders
2. Order Service creates order with status PENDING
3. Saga instance created with state STARTED
4. OrderCreatedEvent published to Kafka
5. Order status updated to PAYMENT_PENDING
6. Payment Service processes payment
7. PaymentProcessedEvent (SUCCESS) received
8. Order status updated to CONFIRMED
9. Saga state updated to COMPLETED

### Failure Flow (Compensation)
1. Client creates order via POST /api/orders
2. Order Service creates order with status PENDING
3. Saga instance created with state STARTED
4. OrderCreatedEvent published to Kafka
5. Order status updated to PAYMENT_PENDING
6. Payment Service fails to process payment
7. PaymentProcessedEvent (FAILED) received
8. Saga state updated to COMPENSATING
9. Order status updated to CANCELLED
10. Compensation data stored
11. Saga state updated to FAILED

## Running the Service

### Prerequisites
- Java 21
- Maven 3.8+
- MongoDB (or MongoDB Atlas)
- Kafka
- Eureka Server

### Build
```bash
mvn clean package
```

### Run
```bash
mvn spring-boot:run
```

### Docker
```bash
docker build -t order-service:latest .
docker run -p 8082:8082 order-service:latest
```

## Testing

### Create Order
```bash
curl -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Id: usr_123456" \
  -d '{
    "items": [
      {
        "productId": "prod_789",
        "productName": "Laptop",
        "quantity": 1,
        "price": 1299.99
      }
    ],
    "shippingAddress": {
      "street": "123 Main St",
      "city": "New York",
      "state": "NY",
      "zipCode": "10001",
      "country": "USA"
    },
    "paymentMethod": "CREDIT_CARD"
  }'
```

### Get Order
```bash
curl http://localhost:8082/api/orders/{orderId}
```

### Get Order Status
```bash
curl http://localhost:8082/api/orders/{orderId}/status
```

### Cancel Order
```bash
curl -X PUT http://localhost:8082/api/orders/{orderId}/cancel \
  -H "Content-Type: application/json" \
  -d '{"reason": "Changed my mind"}'
```

## Monitoring

### Metrics
- Order creation rate
- Order confirmation rate
- Order cancellation rate
- Saga completion rate
- Saga compensation rate
- Average order value

### Saga Monitoring
- Active sagas
- Completed sagas
- Failed sagas
- Saga duration
- Compensation rate

## Error Handling

### Saga Timeout
- Timeout: 5 minutes
- Action: Mark saga as FAILED
- Order status: FAILED

### Payment Failure
- Trigger: PaymentProcessedEvent with FAILED status
- Action: Compensate saga
- Order status: CANCELLED

### Kafka Failure
- Retry: Automatic with Kafka
- DLT: Failed messages sent to DLT
- Manual intervention required

## Dependencies
- Spring Boot 4.0.2
- Spring Cloud 2024.0.0
- Spring Data MongoDB
- Spring Kafka
- Resilience4j
- Lombok
- Micrometer (Prometheus)

## Future Enhancements
- [ ] Add inventory reservation
- [ ] Implement order tracking
- [ ] Add shipping integration
- [ ] Implement order notifications
- [ ] Add order history
- [ ] Implement order search
- [ ] Add bulk order creation
- [ ] Implement order analytics

## License
MIT License