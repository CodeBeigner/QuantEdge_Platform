package com.QuantPlatformApplication.QuantPlatformApplication.service.paper;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperMetricsServiceTest {

    private TradeLog closed(String tid, String direction, double entry, double exit, double size) {
        double pnl = "LONG".equals(direction)
            ? (exit - entry) * size
            : (entry - exit) * size;
        return TradeLog.builder()
            .tradeId(tid).userId(0L).symbol("BTCUSDT").direction(direction)
            .entryPrice(BigDecimal.valueOf(entry))
            .stopLossPrice(BigDecimal.valueOf(entry * 0.99))
            .takeProfitPrice(BigDecimal.valueOf(entry * 1.02))
            .positionSize(BigDecimal.valueOf(size))
            .status("CLOSED")
            .outcome(Map.of("exit_price", exit, "realized_pnl", pnl, "outcome", pnl > 0 ? "TP" : "SL"))
            .openedAt(Instant.now().minusSeconds(3600))
            .closedAt(Instant.now())
            .build();
    }

    @Test
    void metrics_zeroTrades_returnsZeros() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        when(repo.findAll()).thenReturn(List.of());

        PaperMetricsService svc = new PaperMetricsService(repo);
        PaperMetricsService.Metrics m = svc.computeRolling(28);

        assertThat(m.tradeCount()).isEqualTo(0);
        assertThat(m.winRate()).isEqualTo(0.0);
        assertThat(m.sharpe()).isEqualTo(0.0);
        assertThat(m.maxDrawdownPct()).isEqualTo(0.0);
    }

    @Test
    void metrics_computesWinRateAndPnl() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        when(repo.findAll()).thenReturn(List.of(
            closed("t1", "LONG", 100, 102, 1.0),  // +2 win
            closed("t2", "LONG", 100, 99,  1.0),  // -1 loss
            closed("t3", "LONG", 100, 101.5, 1.0) // +1.5 win
        ));

        PaperMetricsService svc = new PaperMetricsService(repo);
        PaperMetricsService.Metrics m = svc.computeRolling(28);

        assertThat(m.tradeCount()).isEqualTo(3);
        assertThat(m.winRate()).isEqualTo(2.0 / 3.0);
        assertThat(m.totalPnl()).isEqualTo(2.5);
    }

    @Test
    void gateStatus_reflectsCriteriaFromSpec() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        when(repo.findAll()).thenReturn(List.of());

        PaperMetricsService svc = new PaperMetricsService(repo);
        PaperMetricsService.Gate g = svc.gateStatus(svc.computeRolling(28));

        // Zero trades: all criteria fail.
        assertThat(g.tradeCountPass()).isFalse();
        assertThat(g.allPass()).isFalse();
    }
}
