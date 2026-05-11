package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Backtest configuration. Defaults reflect realistic Delta-Exchange-style
 * crypto perpetuals:
 *   - 3 bps maker / 7 bps taker fee (midpoint of common rates)
 *   - 5 bps per-side slippage (used by SlippageModel)
 *   - 0.01% funding per 8-hour interval
 *
 * Market impact is intentionally NOT modeled — at retail scale
 * ($500 × 10-25x leverage = notional ~$5-12k), impact on BTCUSDT
 * (ADV > $10B) is rounding error vs. slippage. Revisit when capital
 * exceeds $100k.
 */
@Getter
@Builder
public class BacktestConfig {
    @Builder.Default private final double initialCapital = 500;
    @Builder.Default private final double slippageBps = 5.0;        // 5 bps per side
    @Builder.Default private final double makerFeePct = 0.0003;     // 3 bps
    @Builder.Default private final double takerFeePct = 0.0007;     // 7 bps
    @Builder.Default private final double fundingRatePer8h = 0.0001; // 0.01%
    @Builder.Default private final boolean useMakerOrders = true;
    @Builder.Default private final RiskParameters riskParameters = RiskParameters.builder().build();

    // Plan 3 additions — meta-labeler veto hook (default off).
    @Builder.Default private final boolean useMetaFilter = false;
    @Builder.Default private final double metaThreshold = 0.55;
    @Builder.Default private final String metaSymbol = "";          // if empty, uses the backtest symbol

    // Plan 3 additions — data source policy.
    @Builder.Default private final boolean allowRestFallback = false; // Postgres-first; no silent fallback
}
