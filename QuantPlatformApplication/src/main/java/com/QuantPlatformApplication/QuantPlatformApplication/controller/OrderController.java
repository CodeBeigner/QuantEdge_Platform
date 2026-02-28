package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.Order;
import com.QuantPlatformApplication.QuantPlatformApplication.service.OrderManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderManagementService orderService;

    @PostMapping
    public ResponseEntity<Order> placeOrder(@RequestBody Map<String, Object> body) {
        String symbol = (String) body.get("symbol");
        String side = (String) body.get("side");
        String type = (String) body.getOrDefault("orderType", "MARKET");
        int quantity = (int) body.getOrDefault("quantity", 100);
        BigDecimal price = body.containsKey("price")
                ? new BigDecimal(body.get("price").toString())
                : null;
        Long strategyId = body.containsKey("strategyId")
                ? Long.valueOf(body.get("strategyId").toString())
                : null;

        return ResponseEntity.ok(orderService.placeOrder(symbol, side, type, quantity, price, strategyId));
    }

    @GetMapping
    public ResponseEntity<List<Order>> listOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Order> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    @GetMapping("/positions")
    public ResponseEntity<?> getPositions() {
        return ResponseEntity.ok(orderService.getPositions());
    }

    @GetMapping("/portfolio")
    public ResponseEntity<Map<String, Object>> portfolio() {
        return ResponseEntity.ok(orderService.getPortfolioSummary());
    }
}
