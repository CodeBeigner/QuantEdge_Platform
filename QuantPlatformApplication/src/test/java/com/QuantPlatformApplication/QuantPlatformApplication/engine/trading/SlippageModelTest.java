package com.QuantPlatformApplication.QuantPlatformApplication.engine.trading;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlippageModelTest {

    @Test
    void buyApplyIncreasesPriceByBps() {
        double filled = SlippageModel.applyToBuy(100.0, 10.0); // 10 bps
        assertThat(filled).isEqualTo(100.0 * (1 + 0.001));
    }

    @Test
    void sellApplyDecreasesPriceByBps() {
        double filled = SlippageModel.applyToSell(100.0, 10.0);
        assertThat(filled).isEqualTo(100.0 * (1 - 0.001));
    }

    @Test
    void zeroBpsIsIdentity() {
        assertThat(SlippageModel.applyToBuy(100.0, 0.0)).isEqualTo(100.0);
        assertThat(SlippageModel.applyToSell(100.0, 0.0)).isEqualTo(100.0);
    }

    @Test
    void deterministicForOrderIdReturnsStableValue() {
        double a = SlippageModel.deterministicForOrderId("abc-123", 3.0, 7.0);
        double b = SlippageModel.deterministicForOrderId("abc-123", 3.0, 7.0);
        assertThat(a).isEqualTo(b);
        assertThat(a).isBetween(3.0, 7.0);
    }

    @Test
    void deterministicForOrderIdVariesByOrderId() {
        double a = SlippageModel.deterministicForOrderId("abc-123", 3.0, 7.0);
        double b = SlippageModel.deterministicForOrderId("xyz-999", 3.0, 7.0);
        assertThat(a).isNotEqualTo(b);
    }
}
