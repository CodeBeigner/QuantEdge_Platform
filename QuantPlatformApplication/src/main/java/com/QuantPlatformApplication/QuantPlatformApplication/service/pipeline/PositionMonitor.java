package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.data.CandleSource;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.service.paper.PaperTradePersister;
import com.QuantPlatformApplication.QuantPlatformApplication.service.telegram.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Scans OPEN TradeLog rows every minute, checks the latest 15m bar's high/low
 * against each position's TP/SL levels, and closes any that hit a barrier.
 *
 * Uses the same CandleSource as the backtest/live tick — no Binance REST call
 * in the hot path.
 *
 * Simple first pass: longs close on high>=TP or low<=SL; shorts invert.
 * Funding accrual + partial fills are explicitly out of Plan 4 scope.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PositionMonitor {

    private static final long SYSTEM_PAPER_USER_ID = 0L;

    private final TradeLogRepository tradeLogRepo;
    private final CandleSource candleSource;
    private final PaperTradePersister persister;
    private final TelegramBotService telegram;

    @Scheduled(cron = "${quantedge.positions.cron:0 * * * * *}", zone = "UTC")
    public void onTick() {
        runOnce();
    }

    public void runOnce() {
        List<TradeLog> open = tradeLogRepo.findByUserIdAndStatusOrderByCreatedAtDesc(
            SYSTEM_PAPER_USER_ID, "OPEN");
        if (open.isEmpty()) return;

        LocalDate asOf = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = asOf.minusDays(1);

        for (TradeLog tl : open) {
            try {
                List<Candle> recent = candleSource.fetch(tl.getSymbol(), "15m", from, asOf);
                if (recent.isEmpty()) continue;
                Candle last = recent.get(recent.size() - 1);
                evaluate(tl, last);
            } catch (Exception e) {
                log.warn("PositionMonitor failed for tradeId={}: {}", tl.getTradeId(), e.getMessage());
            }
        }
    }

    private void evaluate(TradeLog tl, Candle last) {
        double entry = tl.getEntryPrice().doubleValue();
        double sl = tl.getStopLossPrice().doubleValue();
        double tp = tl.getTakeProfitPrice().doubleValue();
        double size = tl.getPositionSize().doubleValue();
        boolean isLong = "LONG".equals(tl.getDirection());

        double exitPrice;
        String outcome;
        if (isLong && last.high() >= tp) { exitPrice = tp; outcome = "TP"; }
        else if (isLong && last.low()  <= sl) { exitPrice = sl; outcome = "SL"; }
        else if (!isLong && last.low() <= tp) { exitPrice = tp; outcome = "TP"; }
        else if (!isLong && last.high() >= sl) { exitPrice = sl; outcome = "SL"; }
        else return; // still open

        double pnl = isLong
            ? (exitPrice - entry) * size
            : (entry - exitPrice) * size;

        persister.markClosed(tl.getTradeId(), exitPrice, outcome, pnl);
        telegram.sendMessage(String.format(
            "✅ *Closed* %s %s @ $%.2f (%s, P&L $%+.2f)",
            tl.getDirection(), tl.getSymbol(), exitPrice, outcome, pnl));
        log.info("Closed tradeId={} {} {} @ {} → {} pnl={}",
            tl.getTradeId(), tl.getDirection(), tl.getSymbol(), exitPrice, outcome, pnl);
    }
}
