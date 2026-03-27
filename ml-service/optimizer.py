"""
Portfolio Optimizer — Markowitz Mean-Variance Optimization

Implements efficient frontier calculation and optimal
portfolio allocation across multiple assets.
"""
import numpy as np
from scipy.optimize import minimize
from sklearn.covariance import LedoitWolf
from typing import List


def optimize_portfolio(
    returns: np.ndarray,
    symbols: List[str],
    risk_free_rate: float = 0.02,
    target_return: float = None,
) -> dict:
    """Compute optimal portfolio weights using mean-variance optimization.
    
    Args:
        returns: 2D array of daily returns, shape (days, n_assets)
        symbols: List of symbol names
        risk_free_rate: Annual risk-free rate
        target_return: Target annual return (None = maximize Sharpe)
    
    Returns:
        Optimal weights, expected return, volatility, Sharpe ratio
    """
    n_assets = returns.shape[1]
    mean_returns = np.mean(returns, axis=0) * 252  # Annualize
    cov_matrix = np.cov(returns.T) * 252

    def portfolio_stats(weights):
        ret = np.dot(weights, mean_returns)
        vol = np.sqrt(np.dot(weights.T, np.dot(cov_matrix, weights)))
        sharpe = (ret - risk_free_rate) / vol if vol > 0 else 0
        return ret, vol, sharpe

    def neg_sharpe(weights):
        _, _, sharpe = portfolio_stats(weights)
        return -sharpe

    # Constraints: weights sum to 1, each between 0 and 1
    constraints = [{'type': 'eq', 'fun': lambda w: np.sum(w) - 1}]
    if target_return is not None:
        constraints.append({
            'type': 'eq',
            'fun': lambda w: np.dot(w, mean_returns) - target_return
        })

    bounds = tuple((0, 1) for _ in range(n_assets))
    init_weights = np.array([1.0 / n_assets] * n_assets)

    result = minimize(
        neg_sharpe,
        init_weights,
        method='SLSQP',
        bounds=bounds,
        constraints=constraints,
    )

    optimal_weights = result.x
    ret, vol, sharpe = portfolio_stats(optimal_weights)

    allocation = {
        sym: round(float(w), 4)
        for sym, w in zip(symbols, optimal_weights)
    }

    return {
        "allocation": allocation,
        "expected_return": round(float(ret), 4),
        "volatility": round(float(vol), 4),
        "sharpe_ratio": round(float(sharpe), 4),
        "risk_free_rate": risk_free_rate,
    }


def compute_efficient_frontier(
    returns: np.ndarray,
    symbols: List[str],
    n_points: int = 20,
) -> list:
    """Compute efficient frontier as a list of (return, volatility) points."""
    mean_returns = np.mean(returns, axis=0) * 252
    min_ret = float(np.min(mean_returns))
    max_ret = float(np.max(mean_returns))
    target_returns = np.linspace(min_ret, max_ret, n_points)

    frontier = []
    for tr in target_returns:
        try:
            result = optimize_portfolio(returns, symbols, target_return=tr)
            frontier.append({
                "return": result["expected_return"],
                "volatility": result["volatility"],
            })
        except Exception:
            pass

    return frontier


def optimize_portfolio_robust(
    returns: np.ndarray,
    symbols: List[str],
    risk_free_rate: float = 0.02,
    target_return: float = None,
) -> dict:
    """Portfolio optimization using Ledoit-Wolf shrinkage covariance.

    More robust than sample covariance, especially when the number
    of assets is large relative to the sample size.
    """
    n_assets = returns.shape[1]
    mean_returns = np.mean(returns, axis=0) * 252

    lw = LedoitWolf().fit(returns)
    cov_matrix = lw.covariance_ * 252

    def portfolio_stats(weights):
        ret = np.dot(weights, mean_returns)
        vol = np.sqrt(np.dot(weights.T, np.dot(cov_matrix, weights)))
        sharpe = (ret - risk_free_rate) / vol if vol > 0 else 0
        return ret, vol, sharpe

    def neg_sharpe(weights):
        _, _, sharpe = portfolio_stats(weights)
        return -sharpe

    constraints = [{'type': 'eq', 'fun': lambda w: np.sum(w) - 1}]
    if target_return is not None:
        constraints.append({
            'type': 'eq',
            'fun': lambda w: np.dot(w, mean_returns) - target_return
        })

    bounds = tuple((0, 1) for _ in range(n_assets))
    init_weights = np.array([1.0 / n_assets] * n_assets)

    result = minimize(
        neg_sharpe, init_weights,
        method='SLSQP', bounds=bounds, constraints=constraints,
    )

    optimal_weights = result.x
    ret, vol, sharpe = portfolio_stats(optimal_weights)

    allocation = {
        sym: round(float(w), 4)
        for sym, w in zip(symbols, optimal_weights)
    }

    return {
        "allocation": allocation,
        "expected_return": round(float(ret), 4),
        "volatility": round(float(vol), 4),
        "sharpe_ratio": round(float(sharpe), 4),
        "risk_free_rate": risk_free_rate,
        "shrinkage_coefficient": round(float(lw.shrinkage_), 4),
        "method": "ledoit_wolf",
    }


def risk_parity_allocation(
    returns: np.ndarray,
    symbols: List[str],
) -> dict:
    """Risk parity allocation — each asset contributes equal risk.

    Minimizes the sum of squared differences between each asset's
    marginal risk contribution and the target (1/n of total risk).
    """
    n_assets = returns.shape[1]
    cov_matrix = np.cov(returns.T) * 252

    def risk_contribution(weights):
        port_vol = np.sqrt(np.dot(weights.T, np.dot(cov_matrix, weights)))
        marginal = np.dot(cov_matrix, weights) / port_vol
        return weights * marginal

    def objective(weights):
        rc = risk_contribution(weights)
        target = np.sum(rc) / n_assets
        return np.sum((rc - target) ** 2)

    constraints = [{'type': 'eq', 'fun': lambda w: np.sum(w) - 1}]
    bounds = tuple((0.01, 1) for _ in range(n_assets))
    init_weights = np.array([1.0 / n_assets] * n_assets)

    result = minimize(
        objective, init_weights,
        method='SLSQP', bounds=bounds, constraints=constraints,
    )

    optimal_weights = result.x
    mean_returns = np.mean(returns, axis=0) * 252
    port_ret = np.dot(optimal_weights, mean_returns)
    port_vol = np.sqrt(np.dot(optimal_weights.T, np.dot(cov_matrix, optimal_weights)))

    rc = risk_contribution(optimal_weights)
    risk_contributions = {
        sym: round(float(r), 4)
        for sym, r in zip(symbols, rc)
    }

    allocation = {
        sym: round(float(w), 4)
        for sym, w in zip(symbols, optimal_weights)
    }

    return {
        "allocation": allocation,
        "expected_return": round(float(port_ret), 4),
        "volatility": round(float(port_vol), 4),
        "risk_contributions": risk_contributions,
        "method": "risk_parity",
    }
