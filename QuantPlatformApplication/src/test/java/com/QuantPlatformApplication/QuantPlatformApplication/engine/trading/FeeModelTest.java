package com.QuantPlatformApplication.QuantPlatformApplication.engine.trading;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class FeeModelTest {

    @Test
    void entryFeeIsMakerPctWhenUseMakerTrue() {
        double fee = FeeModel.entryFee(10_000.0, 0.0003, 0.0007, true);
        assertThat(fee).isEqualTo(10_000.0 * 0.0003);
    }

    @Test
    void entryFeeIsTakerPctWhenUseMakerFalse() {
        double fee = FeeModel.entryFee(10_000.0, 0.0003, 0.0007, false);
        assertThat(fee).isEqualTo(10_000.0 * 0.0007);
    }

    @Test
    void fundingCost_oneFullIntervalCharged() {
        // 1 exactly-8h-interval hold = one funding charge
        double cost = FeeModel.fundingCost(10_000.0, 0.0001, 1);
        assertThat(cost).isEqualTo(10_000.0 * 0.0001);
    }

    @Test
    void fundingCost_threeIntervalsCharged() {
        double cost = FeeModel.fundingCost(10_000.0, 0.0001, 3);
        assertThat(cost).isEqualTo(10_000.0 * 0.0001 * 3, offset(1e-9));
    }

    @Test
    void fundingCost_zeroIntervalsIsZero() {
        double cost = FeeModel.fundingCost(10_000.0, 0.0001, 0);
        assertThat(cost).isEqualTo(0.0);
    }

    @Test
    void roundTripCostCombinesEntryExit() {
        double rt = FeeModel.roundTripCost(10_000.0, 0.0003, 0.0007, true);
        assertThat(rt).isEqualTo(10_000.0 * 0.0003 * 2);
    }
}
