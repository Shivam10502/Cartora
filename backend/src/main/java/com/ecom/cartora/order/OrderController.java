package com.ecom.cartora.order;

import com.ecom.cartora.order.dto.OrderResponse;
import com.ecom.cartora.order.dto.CreateOrderRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request.getCouponCode(), request.getShippingAddress());
    }

    @GetMapping
    public List<OrderResponse> getMyOrders() {
        return orderService.getMyOrders();
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrderById(
            @PathVariable("orderId") Long orderId) {
        return orderService.getOrderById(orderId);
    }

    @PutMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(
            @PathVariable("orderId") Long orderId) {
        return orderService.cancelOrder(orderId);
    }
}
