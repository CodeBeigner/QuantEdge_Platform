"""FastAPI service for stress-testing trading algorithms."""
from __future__ import annotations

import logging

import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from services.stress_test.schema import (
    StressTestRequest,
    StressTestResponse,
    MonteCarloResult,
    MaxDrawdownDistribution,
)
from services.stress_test.hmm_layer import fit_hmm, get_transition_matrix, regime_performance
from services.stress_test.monte_carlo import run_simulations, compute_sharpe, check_overfitting

_log = logging.getLogger(__name__)

app = FastAPI(title="QuantEdge Stress Test Service", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health():
    return {"status": "UP", "service": "stress-test"}


@app.post("/stress-test", response_model=StressTestResponse)
async def stress_test(req: StressTestRequest):
    entries = req.trade_log

    if len(entries) < 30:
        raise HTTPException(status_code=422, detail=f"Insufficient data: need at least 30 entries, got {len(entries)}")

    returns = np.array([e.return_pct / 100.0 for e in entries])

    model, state_probas, regimes = fit_hmm(returns, n_states=req.n_states)
    transmat = get_transition_matrix(model)
    _log.info("HMM transition matrix: %s", transmat)

    mc_result = run_simulations(
        model=model,
        returns=returns,
        regimes=regimes,
        n_simulations=req.n_simulations,
    )

    _log.info(
        "Monte Carlo: median=%.4f%%, VaR95=%.4f%%, ruin=%.2f%%",
        mc_result["median_return"] * 100,
        mc_result["var_95"] * 100,
        mc_result["ruin_probability"] * 100,
    )

    live_sharpe = compute_sharpe(returns)
    overfitting = check_overfitting(live_sharpe, req.backtest_sharpe)

    regime_perf = regime_performance(returns, regimes)

    regime_labels_ints = []
    name_to_int = {"bear": 0, "sideways": 1, "bull": 2}
    for r in regimes:
        regime_labels_ints.append(name_to_int.get(r, 1))

    return StressTestResponse(
        regime_labels=regime_labels_ints,
        monte_carlo=MonteCarloResult(
            median_return=mc_result["median_return"],
            var_95=mc_result["var_95"],
            cvar_95=mc_result["cvar_95"],
            max_drawdown_distribution=MaxDrawdownDistribution(**mc_result["max_drawdown_distribution"]),
            ruin_probability=mc_result["ruin_probability"],
        ),
        overfitting_warning=overfitting,
        regime_performance=regime_perf,
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5004)
