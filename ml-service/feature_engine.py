"""
Feature Engineering Pipeline — Technical Indicators

Computes RSI, MACD, Bollinger Bands, SMA crossover, ATR, OBV
from raw OHLCV data for use as ML features.
"""
import numpy as np
import pandas as pd


def compute_features(df: pd.DataFrame) -> pd.DataFrame:
    """Compute all technical indicators from OHLCV DataFrame.
    
    Expected columns: open, high, low, close, volume
    Returns DataFrame with original + feature columns.
    """
    df = df.copy()
    close = df['close'].astype(float)
    high = df['high'].astype(float)
    low = df['low'].astype(float)
    volume = df['volume'].astype(float)

    # ── RSI (14-period) ────────────────────────────────────
    delta = close.diff()
    gain = delta.where(delta > 0, 0.0)
    loss = -delta.where(delta < 0, 0.0)
    avg_gain = gain.rolling(14).mean()
    avg_loss = loss.rolling(14).mean()
    rs = avg_gain / avg_loss.replace(0, np.nan)
    df['rsi'] = 100 - (100 / (1 + rs))

    # ── MACD (12, 26, 9) ──────────────────────────────────
    ema12 = close.ewm(span=12, adjust=False).mean()
    ema26 = close.ewm(span=26, adjust=False).mean()
    df['macd'] = ema12 - ema26
    df['macd_signal'] = df['macd'].ewm(span=9, adjust=False).mean()
    df['macd_hist'] = df['macd'] - df['macd_signal']

    # ── Bollinger Bands (20, 2σ) ───────────────────────────
    sma20 = close.rolling(20).mean()
    std20 = close.rolling(20).std()
    df['bb_upper'] = sma20 + 2 * std20
    df['bb_lower'] = sma20 - 2 * std20
    df['bb_width'] = (df['bb_upper'] - df['bb_lower']) / sma20
    df['bb_pctb'] = (close - df['bb_lower']) / (df['bb_upper'] - df['bb_lower'])

    # ── SMA Crossover (10/50) ──────────────────────────────
    sma10 = close.rolling(10).mean()
    sma50 = close.rolling(50).mean()
    df['sma_cross'] = (sma10 - sma50) / sma50  # Normalized

    # ── ATR (14-period) ────────────────────────────────────
    tr = pd.concat([
        high - low,
        (high - close.shift(1)).abs(),
        (low - close.shift(1)).abs()
    ], axis=1).max(axis=1)
    df['atr'] = tr.rolling(14).mean()
    df['atr_pct'] = df['atr'] / close  # Normalized

    # ── OBV (On-Balance Volume) ────────────────────────────
    obv = (np.sign(delta.fillna(0)) * volume).cumsum()
    df['obv'] = obv
    df['obv_sma'] = obv.rolling(20).mean()

    # ── VWAP ────────────────────────────────────────────────
    df['vwap'] = (close * volume).cumsum() / volume.cumsum()

    # ── Relative Volume ────────────────────────────────────
    df['rel_volume'] = volume / volume.rolling(20).mean()

    # ── Price-based features ───────────────────────────────
    df['returns_1d'] = close.pct_change(1)
    df['returns_5d'] = close.pct_change(5)
    df['returns_20d'] = close.pct_change(20)
    df['volatility_20d'] = close.pct_change().rolling(20).std()

    # ── Target: next-day return direction ──────────────────
    df['target'] = (close.shift(-1) > close).astype(int)

    return df


FEATURE_COLS = [
    'rsi', 'macd', 'macd_signal', 'macd_hist',
    'bb_width', 'bb_pctb', 'sma_cross',
    'atr_pct', 'obv', 'vwap', 'rel_volume',
    'returns_1d', 'returns_5d',
    'returns_20d', 'volatility_20d',
]
