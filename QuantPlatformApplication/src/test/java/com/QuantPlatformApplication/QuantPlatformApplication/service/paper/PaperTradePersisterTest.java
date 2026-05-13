package com.QuantPlatformApplication.QuantPlatformApplication.service.paper;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Action;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskCheckResult;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TradeSignal;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperTradePersisterTest {

    @Test
    void persist_writesTradeLogWithExplanation() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        when(repo.save(any(TradeLog.class))).thenAnswer(inv -> inv.getArgument(0));

        TradeSignal sig = TradeSignal.builder()
            .symbol("BTCUSDT").action(Action.BUY).entryPrice(42000.0)
            .stopLossPrice(41000.0).takeProfitPrice(44000.0)
            .strategyName("TrendContinuation").confidence(0.7)
            .biasExplanation("1h EMA stack bullish, 4h regime trending")
            .triggerExplanation("15m retest of 20EMA on rising volume")
            .build();
        RiskCheckResult risk = RiskCheckResult.approve(0.025, 25.0, 5.0, 10);

        PaperTradePersister persister = new PaperTradePersister(repo);
        Long tradeId = persister.persist(sig, risk, 0.62);

        ArgumentCaptor<TradeLog> captor = ArgumentCaptor.forClass(TradeLog.class);
        verify(repo).save(captor.capture());
        TradeLog saved = captor.getValue();

        assertThat(saved.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(saved.getDirection()).isEqualTo("LONG");
        assertThat(saved.getStrategyName()).isEqualTo("TrendContinuation");
        assertThat(saved.getEntryPrice().doubleValue()).isEqualTo(42000.0);
        assertThat(saved.getExplanation()).containsKey("bias");
        assertThat(saved.getExplanation()).containsKey("trigger");
        assertThat(saved.getExplanation()).containsEntry("meta_prob", 0.62);
        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getUserId()).isEqualTo(0L);
        assertThat(saved.getTradeId()).isNotBlank();
    }
}
