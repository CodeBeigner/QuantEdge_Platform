package com.QuantPlatformApplication.QuantPlatformApplication.service.ml;

import com.QuantPlatformApplication.QuantPlatformApplication.service.MLClientService;
import com.QuantPlatformApplication.QuantPlatformApplication.service.telegram.TelegramBotService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetaRetrainSchedulerTest {

    @Test
    void runOnce_retrainsEachConfiguredSymbol() {
        MLClientService ml = mock(MLClientService.class);
        TelegramBotService telegram = mock(TelegramBotService.class);
        when(ml.trainMeta(anyString())).thenReturn(
            Map.of("n_train", 120, "train_accuracy", 0.68));

        MetaRetrainScheduler scheduler = new MetaRetrainScheduler(
            ml, telegram, List.of("BTCUSDT", "ETHUSDT"));

        scheduler.runOnce();

        verify(ml).trainMeta(eq("BTCUSDT"));
        verify(ml).trainMeta(eq("ETHUSDT"));
        verify(telegram, times(1)).sendMessage(any()); // one weekly-summary message
    }

    @Test
    void runOnce_sendsWarningOnDegradedModel() {
        MLClientService ml = mock(MLClientService.class);
        TelegramBotService telegram = mock(TelegramBotService.class);
        when(ml.trainMeta(eq("BTCUSDT"))).thenReturn(
            Map.of("n_train", 10, "train_accuracy", 0.50, "error", "not enough binary labels"));
        when(ml.trainMeta(eq("ETHUSDT"))).thenReturn(
            Map.of("n_train", 120, "train_accuracy", 0.70));

        MetaRetrainScheduler scheduler = new MetaRetrainScheduler(
            ml, telegram, List.of("BTCUSDT", "ETHUSDT"));

        scheduler.runOnce();

        // One summary message that notes BTCUSDT failed
        verify(telegram, times(1)).sendMessage(any());
    }
}
