package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeLogRepository extends JpaRepository<TradeLog, Long> {
    Optional<TradeLog> findByTradeId(String tradeId);
    List<TradeLog> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    List<TradeLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<TradeLog> findByUserIdAndSymbolAndStatus(Long userId, String symbol, String status);
}
