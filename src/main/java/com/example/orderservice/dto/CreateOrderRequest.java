package com.example.orderservice.dto;

import com.example.orderservice.model.Order;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    
    @NotEmpty(message = "Items cannot be empty")
    private List<OrderItemRequest> items;
    
    @NotNull(message = "Shipping address is required")
    private ShippingAddressRequest shippingAddress;
    
    @NotNull(message = "Payment method is required")
    private String paymentMethod;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        @NotNull(message = "Product ID is required")
        private String productId;
        
        @NotNull(message = "Product name is required")
        private String productName;
        
        @NotNull(message = "Quantity is required")
        private Integer quantity;
        
        @NotNull(message = "Price is required")
        private BigDecimal price;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddressRequest {
        @NotNull(message = "Street is required")
        private String street;
        
        @NotNull(message = "City is required")
        private String city;
        
        @NotNull(message = "State is required")
        private String state;
        
        @NotNull(message = "Zip code is required")
        private String zipCode;
        
        @NotNull(message = "Country is required")
        private String country;
    }
}