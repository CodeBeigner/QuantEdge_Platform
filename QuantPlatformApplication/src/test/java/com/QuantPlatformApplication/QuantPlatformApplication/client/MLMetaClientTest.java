package com.QuantPlatformApplication.QuantPlatformApplication.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MLMetaClientTest {

    private MockWebServer server;
    private MLMetaClient client;

    @BeforeEach
    void setup() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new MLMetaClient(server.url("/").toString().replaceAll("/$", ""), new ObjectMapper());
    }

    @AfterEach
    void teardown() throws Exception {
        server.shutdown();
    }

    @Test
    void predictMeta_parsesHappyResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"symbol\":\"BTCUSDT\",\"meta_prob\":0.62,\"direction\":1,\"primary_signal\":\"LONG\"}"));

        MLMetaPredictionResponse resp = client.predictMeta(
                "BTCUSDT", "LONG", 42000.0, 0.02, 0.01);

        assertThat(resp.symbol()).isEqualTo("BTCUSDT");
        assertThat(resp.metaProb()).isEqualTo(0.62);
        assertThat(resp.direction()).isEqualTo(1);
    }

    @Test
    void predictMeta_throwsOn500() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThatThrownBy(() -> client.predictMeta("BTCUSDT", "LONG", 42000.0, 0.02, 0.01))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("500");
    }
}
