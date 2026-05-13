package com.QuantPlatformApplication.QuantPlatformApplication.service.ml;

import com.QuantPlatformApplication.QuantPlatformApplication.client.MLMetaClient;
import com.QuantPlatformApplication.QuantPlatformApplication.client.MLMetaPredictionResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.service.telegram.TelegramBotService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetaFilterGateTest {

    @Test
    void allow_whenProbAboveThreshold() {
        MLMetaClient client = mock(MLMetaClient.class);
        TelegramBotService telegram = mock(TelegramBotService.class);
        when(client.predictMeta(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(new MLMetaPredictionResponse("BTCUSDT", 0.72, 1, "LONG"));

        MetaFilterGate gate = new MetaFilterGate(client, telegram, 0.55);
        MetaFilterGate.Decision d = gate.check("BTCUSDT", "LONG", 42000.0, 0.02, 0.01);

        assertThat(d.allow()).isTrue();
        assertThat(d.metaProb()).isEqualTo(0.72);
        verify(telegram, never()).sendMessage(any());
    }

    @Test
    void veto_whenProbBelowThreshold() {
        MLMetaClient client = mock(MLMetaClient.class);
        TelegramBotService telegram = mock(TelegramBotService.class);
        when(client.predictMeta(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(new MLMetaPredictionResponse("BTCUSDT", 0.30, 1, "LONG"));

        MetaFilterGate gate = new MetaFilterGate(client, telegram, 0.55);
        MetaFilterGate.Decision d = gate.check("BTCUSDT", "LONG", 42000.0, 0.02, 0.01);

        assertThat(d.allow()).isFalse();
        assertThat(d.metaProb()).isEqualTo(0.30);
        assertThat(d.reason()).contains("below threshold");
        verify(telegram, never()).sendMessage(any()); // veto is expected, not alarmed
    }

    @Test
    void failOpen_whenClientThrows_sendsLoudAlert() {
        MLMetaClient client = mock(MLMetaClient.class);
        TelegramBotService telegram = mock(TelegramBotService.class);
        when(client.predictMeta(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble()))
            .thenThrow(new RuntimeException("connection refused"));

        MetaFilterGate gate = new MetaFilterGate(client, telegram, 0.55);
        MetaFilterGate.Decision d = gate.check("BTCUSDT", "LONG", 42000.0, 0.02, 0.01);

        assertThat(d.allow()).isTrue();
        assertThat(d.failedOpen()).isTrue();
        verify(telegram, times(1)).sendMessage(any(String.class));
    }
}
