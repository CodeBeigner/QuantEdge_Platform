package com.QuantPlatformApplication.QuantPlatformApplication.service.paper;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Rolling paper-trading metrics for the validation-gate dashboard.
 * Gate criteria come straight from spec §4.7:
 *   Sharpe  > 1.5
 *   MaxDD   < 15%
 *   WinRate 55%–65%
 *   Trades  > 50
 *   Window  >= 4 weeks
 *
 * Dashboard-only per Plan 4 scope — no enforcement.
 */
@Service
@RequiredArgsConstructor
public class PaperMetricsService {

    private static final long SYSTEM_PAPER_USER_ID = 0L;

    private final TradeLogRepository tradeLogRepo;

    public Metrics computeRolling(int windowDays) {
        Instant since = Instant.now().minus(windowDays, ChronoUnit.DAYS);
        List<TradeLog> closed = tradeLogRepo.findAll().stream()
            .filter(tl -> tl.getUserId() == SYSTEM_PAPER_USER_ID)
            .filter(tl -> "CLOSED".equals(tl.getStatus()))
            .filter(tl -> tl.getClosedAt() != null && tl.getClosedAt().isAfter(since))
            .toList();

        int n = closed.size();
        if (n == 0) return new Metrics(0, 0, 0, 0, 0, 0, windowDays);

        double totalPnl = 0;
        int wins = 0;
        double[] pnls = new double[n];
        for (int i = 0; i < n; i++) {
            double pnl = extractPnl(closed.get(i));
            pnls[i] = pnl;
            totalPnl += pnl;
            if (pnl > 0) wins++;
        }

        double mean = totalPnl / n;
        double var = 0;
        for (double p : pnls) var += (p - mean) * (p - mean);
        double sd = n > 1 ? Math.sqrt(var / (n - 1)) : 0.0;
        double sharpe = sd == 0 ? 0.0 : mean / sd * Math.sqrt(252.0);

        double peak = 0, equity = 0, maxDd = 0;
        for (double p : pnls) {
            equity += p;
            peak = Math.max(peak, equity);
            if (peak > 0) maxDd = Math.max(maxDd, (peak - equity) / peak);
        }

        double winRate = wins / (double) n;
        return new Metrics(n, winRate, sharpe, maxDd, totalPnl, wins, windowDays);
    }

    public Gate gateStatus(Metrics m) {
        boolean sharpe    = m.sharpe() > 1.5;
        boolean drawdown  = m.maxDrawdownPct() < 0.15;
        boolean winRate   = m.winRate() >= 0.55 && m.winRate() <= 0.65;
        boolean trades    = m.tradeCount() > 50;
        boolean window    = m.windowDays() >= 28;
        boolean all = sharpe && drawdown && winRate && trades && window;
        return new Gate(sharpe, drawdown, winRate, trades, window, all);
    }

    @SuppressWarnings("unchecked")
    private double extractPnl(TradeLog tl) {
        Map<String, Object> out = tl.getOutcome();
        if (out == null) return 0.0;
        Object v = out.get("realized_pnl");
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    public record Metrics(
        int tradeCount,
        double winRate,
        double sharpe,
        double maxDrawdownPct,
        double totalPnl,
        int winningTrades,
        int windowDays
    ) {}

    public record Gate(
        boolean sharpePass,
        boolean drawdownPass,
        boolean winRatePass,
        boolean tradeCountPass,
        boolean windowPass,
        boolean allPass
    ) {}
}
