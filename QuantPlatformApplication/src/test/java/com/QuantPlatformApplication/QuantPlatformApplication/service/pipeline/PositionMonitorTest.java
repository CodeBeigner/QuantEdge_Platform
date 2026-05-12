package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.data.CandleSource;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.service.paper.PaperTradePersister;
import com.QuantPlatformApplication.QuantPlatformApplication.service.telegram.TelegramBotService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PositionMonitorTest {

    private TradeLog openLong(String tradeId, double entry, double sl, double tp) {
        return TradeLog.builder()
            .tradeId(tradeId).symbol("BTCUSDT").direction("LONG")
            .entryPrice(BigDecimal.valueOf(entry))
            .stopLossPrice(BigDecimal.valueOf(sl))
            .takeProfitPrice(BigDecimal.valueOf(tp))
            .positionSize(BigDecimal.valueOf(0.01))
            .status("OPEN").build();
    }

    private Candle bar(double high, double low) {
        return new Candle(Instant.now(), high - 1, high, low, (high + low) / 2, 100.0, TimeFrame.M15);
    }

    @Test
    void closes_long_when_tp_hit() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        CandleSource source = mock(CandleSource.class);
        PaperTradePersister persister = mock(PaperTradePersister.class);
        TelegramBotService telegram = mock(TelegramBotService.class);

        when(repo.findByUserIdAndStatusOrderByCreatedAtDesc(eq(0L), eq("OPEN")))
            .thenReturn(List.of(openLong("t1", 42000, 41000, 44000)));
        when(source.fetch(eq("BTCUSDT"), eq("15m"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(bar(44500, 43000))); // high=44500 > tp=44000 → TP hit

        new PositionMonitor(repo, source, persister, telegram).runOnce();

        verify(persister).markClosed(eq("t1"), anyDouble(), eq("TP"), anyDouble());
        verify(telegram).sendMessage(contains("Closed"));
    }

    @Test
    void closes_long_when_sl_hit() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        CandleSource source = mock(CandleSource.class);
        PaperTradePersister persister = mock(PaperTradePersister.class);
        TelegramBotService telegram = mock(TelegramBotService.class);

        when(repo.findByUserIdAndStatusOrderByCreatedAtDesc(eq(0L), eq("OPEN")))
            .thenReturn(List.of(openLong("t2", 42000, 41000, 44000)));
        when(source.fetch(eq("BTCUSDT"), eq("15m"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(bar(42100, 40500))); // low=40500 < sl=41000 → SL hit

        new PositionMonitor(repo, source, persister, telegram).runOnce();

        verify(persister).markClosed(eq("t2"), anyDouble(), eq("SL"), anyDouble());
    }

    @Test
    void leaves_open_when_neither_tp_nor_sl_touched() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        CandleSource source = mock(CandleSource.class);
        PaperTradePersister persister = mock(PaperTradePersister.class);
        TelegramBotService telegram = mock(TelegramBotService.class);

        when(repo.findByUserIdAndStatusOrderByCreatedAtDesc(eq(0L), eq("OPEN")))
            .thenReturn(List.of(openLong("t3", 42000, 41000, 44000)));
        when(source.fetch(eq("BTCUSDT"), eq("15m"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(bar(42500, 41500))); // inside band

        new PositionMonitor(repo, source, persister, telegram).runOnce();

        verify(persister, never()).markClosed(anyString(), anyDouble(), anyString(), anyDouble());
    }
}
