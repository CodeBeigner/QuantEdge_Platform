package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.Order;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.PortfolioPosition;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MarketDataEntity;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.OrderRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.PortfolioPositionRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.MarketDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Order Management Service — Full order lifecycle with paper-trading fill
 * simulation.
 *
 * Supports: MARKET and LIMIT orders, BUY and SELL sides.
 * Simulates fills with realistic slippage modeling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderManagementService {

    private final OrderRepository orderRepo;
    private final PortfolioPositionRepository positionRepo;
    private final MarketDataRepository marketDataRepo;

    /**
     * Place a new order (simulated paper trading).
     */
    @Transactional
    public Order placeOrder(String symbol, String side, String orderType, int quantity, BigDecimal limitPrice,
            Long strategyId) {
        Order order = new Order();
        order.setSymbol(symbol);
        order.setSide(side.toUpperCase());
        order.setOrderType(orderType.toUpperCase());
        order.setQuantity(quantity);
        order.setPrice(limitPrice);
        order.setStrategyId(strategyId);
        order.setStatus("PENDING");
        order = orderRepo.save(order);

        // Auto-fill market orders immediately
        if ("MARKET".equals(order.getOrderType())) {
            return fillOrder(order);
        }
        return order;
    }

    /**
     * Simulate order fill with slippage.
     */
    @Transactional
    public Order fillOrder(Order order) {
        // Get latest price
        List<MarketDataEntity> recent = marketDataRepo.findRecentBySymbol(order.getSymbol(), 1);
        if (recent.isEmpty()) {
            order.setStatus("REJECTED");
            order.setSlippage(BigDecimal.ZERO);
            return orderRepo.save(order);
        }

        BigDecimal marketPrice = recent.get(0).getClose();

        // Simulate slippage (0.01% to 0.05%)
        double slippageBps = 1 + (Math.random() * 4); // 1-5 basis points
        BigDecimal slippage = marketPrice.multiply(BigDecimal.valueOf(slippageBps / 10000));

        BigDecimal filledPrice = "BUY".equals(order.getSide())
                ? marketPrice.add(slippage)
                : marketPrice.subtract(slippage);

        order.setFilledPrice(filledPrice.setScale(4, RoundingMode.HALF_UP));
        order.setSlippage(slippage.setScale(6, RoundingMode.HALF_UP));
        order.setStatus("FILLED");
        order = orderRepo.save(order);

        // Update portfolio position
        updatePosition(order);
        log.info("Order {} filled: {} {} {} @ ${}", order.getId(), order.getSide(),
                order.getQuantity(), order.getSymbol(), filledPrice);

        return order;
    }

    /**
     * Cancel a pending order.
     */
    @Transactional
    public Order cancelOrder(Long orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        if (!"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("Cannot cancel order in status: " + order.getStatus());
        }
        order.setStatus("CANCELLED");
        return orderRepo.save(order);
    }

    /**
     * Get all orders.
     */
    public List<Order> getAllOrders() {
        return orderRepo.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Get portfolio positions.
     */
    public List<PortfolioPosition> getPositions() {
        return positionRepo.findAll();
    }

    /**
     * Get portfolio summary.
     */
    public Map<String, Object> getPortfolioSummary() {
        List<PortfolioPosition> positions = positionRepo.findAll();
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalPnl = BigDecimal.ZERO;

        for (PortfolioPosition pos : positions) {
            BigDecimal value = pos.getCurrentPrice() != null
                    ? pos.getCurrentPrice().multiply(BigDecimal.valueOf(pos.getQuantity()))
                    : BigDecimal.ZERO;
            totalValue = totalValue.add(value);
            if (pos.getUnrealizedPnl() != null)
                totalPnl = totalPnl.add(pos.getUnrealizedPnl());
        }

        return Map.of(
                "totalValue", totalValue,
                "unrealizedPnl", totalPnl,
                "positionCount", positions.size(),
                "positions", positions);
    }

    /**
     * Update portfolio position after a fill.
     */
    private void updatePosition(Order order) {
        PortfolioPosition pos = positionRepo.findBySymbol(order.getSymbol())
                .orElseGet(() -> {
                    PortfolioPosition p = new PortfolioPosition();
                    p.setSymbol(order.getSymbol());
                    p.setQuantity(0);
                    p.setAvgCost(BigDecimal.ZERO);
                    p.setRealizedPnl(BigDecimal.ZERO);
                    return p;
                });

        if ("BUY".equals(order.getSide())) {
            // Update average cost
            BigDecimal totalCost = pos.getAvgCost().multiply(BigDecimal.valueOf(pos.getQuantity()))
                    .add(order.getFilledPrice().multiply(BigDecimal.valueOf(order.getQuantity())));
            int newQty = pos.getQuantity() + order.getQuantity();
            pos.setQuantity(newQty);
            pos.setAvgCost(newQty > 0 ? totalCost.divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
        } else {
            // SELL — realize P&L
            BigDecimal pnl = order.getFilledPrice().subtract(pos.getAvgCost())
                    .multiply(BigDecimal.valueOf(order.getQuantity()));
            pos.setRealizedPnl(pos.getRealizedPnl().add(pnl));
            pos.setQuantity(pos.getQuantity() - order.getQuantity());
        }

        pos.setCurrentPrice(order.getFilledPrice());
        pos.setUnrealizedPnl(
                pos.getCurrentPrice().subtract(pos.getAvgCost())
                        .multiply(BigDecimal.valueOf(pos.getQuantity())));
        positionRepo.save(pos);
    }
}
