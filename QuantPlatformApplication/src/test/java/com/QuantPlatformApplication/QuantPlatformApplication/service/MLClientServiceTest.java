package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.repository.MLSignalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MLClientServiceTest {

    @Test
    void predictMeta_hitsCorrectUrlWithPayload() {
        RestTemplate rest = mock(RestTemplate.class);
        MLSignalRepository repo = mock(MLSignalRepository.class);
        MLClientService svc = new MLClientService(repo, rest);

        when(rest.postForEntity(contains("/predict-meta/BTCUSDT"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("meta_prob", 0.62, "direction", 1)));

        Map<String, Object> out = svc.predictMeta("BTCUSDT", "LONG", 42000.0, 0.02, 0.01);

        assertThat(out).containsEntry("meta_prob", 0.62);
        verify(rest).postForEntity(contains("/predict-meta/BTCUSDT"), any(), eq(Map.class));
    }

    @Test
    void predictMeta_returnsErrorOnServiceDown() {
        RestTemplate rest = mock(RestTemplate.class);
        MLSignalRepository repo = mock(MLSignalRepository.class);
        MLClientService svc = new MLClientService(repo, rest);

        when(rest.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("connection refused"));

        Map<String, Object> out = svc.predictMeta("BTCUSDT", "LONG", 100.0, 0.02, 0.01);

        assertThat(out).containsKey("error");
    }

    @Test
    void trainMeta_hitsCorrectUrl() {
        RestTemplate rest = mock(RestTemplate.class);
        MLSignalRepository repo = mock(MLSignalRepository.class);
        MLClientService svc = new MLClientService(repo, rest);

        when(rest.postForEntity(contains("/train-meta/BTCUSDT"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("n_train", 120, "train_accuracy", 0.66)));

        Map<String, Object> out = svc.trainMeta("BTCUSDT");

        assertThat(out).containsEntry("n_train", 120);
    }

    @Test
    void predictFlow_and_trainFlow_hitCorrectUrls() {
        RestTemplate rest = mock(RestTemplate.class);
        MLSignalRepository repo = mock(MLSignalRepository.class);
        MLClientService svc = new MLClientService(repo, rest);

        when(rest.postForEntity(contains("/predict-flow/ETHUSDT"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("flow_score", 0.58, "direction", -1)));
        when(rest.postForEntity(contains("/train-flow/ETHUSDT"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("n_train", 450)));

        Map<String, Object> predictOut = svc.predictFlow("ETHUSDT", 200);
        Map<String, Object> trainOut = svc.trainFlow("ETHUSDT");

        assertThat(predictOut).containsEntry("flow_score", 0.58);
        assertThat(trainOut).containsEntry("n_train", 450);
    }
}
