package com.QuantPlatformApplication.QuantPlatformApplication.service.broker;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.Order;
import com.QuantPlatformApplication.QuantPlatformApplication.service.OrderManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal paper trading broker adapter.
 * Wraps the existing OrderManagementService with the BrokerAdapter interface.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaperBrokerAdapter implements BrokerAdapter {

    private final OrderManagementService orderService;

    @Override
    public String getName() {
        return "PAPER";
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public Map<String, Object> placeOrder(String symbol, String side, String type, double quantity, Double price) {
        try {
            BigDecimal limitPrice = (price != null && price > 0) ? BigDecimal.valueOf(price) : null;
            int qty = (int) Math.round(quantity);
            Order order = orderService.placeOrder(symbol, side, type, qty, limitPrice, null);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", order.getId());
            result.put("status", order.getStatus());
            result.put("symbol", order.getSymbol());
            result.put("side", order.getSide());
            result.put("orderType", order.getOrderType());
            result.put("quantity", order.getQuantity());
            String topStatus = switch (order.getStatus()) {
                case "FILLED" -> "FILLED";
                case "REJECTED" -> "REJECTED";
                default -> "SUBMITTED";
            };
            return Map.of("status", topStatus, "orderId", order.getId(), "broker", "PAPER", "result", result);
        } catch (Exception e) {
            return Map.of("status", "REJECTED", "error", e.getMessage(), "broker", "PAPER");
        }
    }

    @Override
    public Map<String, Object> cancelOrder(String orderId) {
        try {
            orderService.cancelOrder(Long.parseLong(orderId));
            return Map.of("status", "CANCELLED", "orderId", orderId);
        } catch (Exception e) {
            return Map.of("status", "FAILED", "error", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getOrder(String orderId) {
        try {
            var order = orderService.findById(Long.parseLong(orderId));
            if (order == null) {
                return Map.of("orderId", orderId, "status", "NOT_FOUND", "broker", "PAPER");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("orderId", order.getId());
            out.put("status", order.getStatus());
            out.put("symbol", order.getSymbol());
            out.put("side", order.getSide());
            out.put("orderType", order.getOrderType());
            out.put("quantity", order.getQuantity());
            out.put("broker", "PAPER");
            return out;
        } catch (NumberFormatException e) {
            return Map.of("orderId", orderId, "status", "INVALID_ID", "broker", "PAPER");
        }
    }

    @Override
    public List<Map<String, Object>> getOpenOrders() {
        try {
            return orderService.getOpenOrders().stream()
                    .map(o -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("orderId", o.getId());
                        m.put("status", o.getStatus());
                        m.put("symbol", o.getSymbol());
                        m.put("side", o.getSide());
                        m.put("quantity", o.getQuantity());
                        m.put("broker", "PAPER");
                        return m;
                    })
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> getPositions() {
        try {
            return List.of(Map.of("positions", orderService.getPositions()));
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public Map<String, Object> getAccount() {
        try {
            return orderService.getPortfolioSummary();
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> reconcilePositions() {
        return Map.of("status", "OK", "message", "Paper broker - no external positions to reconcile");
    }
}
