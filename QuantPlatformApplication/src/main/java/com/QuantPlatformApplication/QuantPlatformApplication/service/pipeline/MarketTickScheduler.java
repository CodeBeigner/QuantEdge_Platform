package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MultiTimeFrameData;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskParameters;
import com.QuantPlatformApplication.QuantPlatformApplication.service.StrategyOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Live paper-trading driver. Fires every 15 minutes on :00 :15 :30 :45 UTC,
 * builds MultiTimeFrameData for each active symbol from Postgres, and invokes
 * StrategyOrchestrator.evaluateStrategies.
 *
 * Config:
 *   quantedge.paper.symbols     (default BTCUSDT,ETHUSDT)
 *   quantedge.paper.balance     (default 500.0)
 *   quantedge.paper.peakEquity  (default 500.0)
 *   quantedge.paper.mode        (default AUTONOMOUS)
 *   quantedge.paper.cron        (default "0 0/15 * * * *")  — 15m cadence
 */
@Slf4j
@Component
public class MarketTickScheduler {

    private final MultiTimeFrameDataBuilder builder;
    private final StrategyOrchestrator orchestrator;
    private final List<String> symbols;
    private final double balance;
    private final double peakEquity;
    private final RiskParameters riskParams;
    private final String executionMode;

    @Autowired
    public MarketTickScheduler(
            MultiTimeFrameDataBuilder builder,
            StrategyOrchestrator orchestrator,
            @Value("${quantedge.paper.symbols:BTCUSDT,ETHUSDT}") String symbolsCsv,
            @Value("${quantedge.paper.balance:500.0}") double balance,
            @Value("${quantedge.paper.peakEquity:500.0}") double peakEquity,
            RiskParameters riskParams,
            @Value("${quantedge.paper.mode:AUTONOMOUS}") String executionMode) {
        this(builder, orchestrator, Arrays.asList(symbolsCsv.split(",")),
             balance, peakEquity, riskParams, executionMode);
    }

    // Visible for tests — bypasses @Value parsing.
    MarketTickScheduler(MultiTimeFrameDataBuilder builder,
                        StrategyOrchestrator orchestrator,
                        List<String> symbols,
                        double balance, double peakEquity,
                        RiskParameters riskParams, String executionMode) {
        this.builder = builder;
        this.orchestrator = orchestrator;
        this.symbols = symbols;
        this.balance = balance;
        this.peakEquity = peakEquity;
        this.riskParams = riskParams;
        this.executionMode = executionMode;
    }

    @Scheduled(cron = "${quantedge.paper.cron:0 0/15 * * * *}", zone = "UTC")
    public void onTick() {
        log.info("MarketTickScheduler firing for {}", symbols);
        runOnce();
    }

    /** Exposed for tests and manual admin triggers. */
    public void runOnce() {
        LocalDate asOf = LocalDate.now(java.time.ZoneOffset.UTC);
        double currentExposure = 0.0;
        double dailyRealizedLoss = 0.0;
        Set<String> openPositionSymbols = Collections.emptySet();

        for (String symbol : symbols) {
            String trimmed = symbol.trim();
            if (trimmed.isEmpty()) continue;
            try {
                MultiTimeFrameData data = builder.build(trimmed, asOf);
                orchestrator.evaluateStrategies(
                    data,
                    balance, peakEquity,
                    currentExposure, dailyRealizedLoss,
                    new HashSet<>(openPositionSymbols),
                    riskParams,
                    executionMode);
            } catch (Exception e) {
                log.warn("Tick evaluation failed for {}: {}", trimmed, e.getMessage());
            }
        }
    }
}
