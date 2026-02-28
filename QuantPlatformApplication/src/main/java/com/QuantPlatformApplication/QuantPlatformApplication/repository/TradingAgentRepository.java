package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradingAgent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TradingAgentRepository extends JpaRepository<TradingAgent, Long> {
    List<TradingAgent> findByActiveTrue();

    List<TradingAgent> findByStrategyId(Long strategyId);
}
