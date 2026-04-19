package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.IndicatorSnapshot;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IndicatorCalculator {

    private static final int MIN_CANDLES = 50;
    private static final int EMA_SHORT = 21;
    private static final int EMA_LONG = 50;
    private static final int RSI_PERIOD = 14;
    private static final int BB_PERIOD = 20;
    private static final double BB_SIGMA = 2.5;
    private static final int ATR_PERIOD = 14;
    private static final int ADX_PERIOD = 14;
    private static final int VOLUME_AVG_PERIOD = 20;
    private static final int MACD_FAST = 12;
    private static final int MACD_SLOW = 26;
    private static final int MACD_SIGNAL = 9;
    private static final int EMA_SLOPE_LOOKBACK = 5;

    public IndicatorSnapshot calculate(List<Candle> candles, TimeFrame timeFrame) {
        if (candles == null || candles.size() < MIN_CANDLES) {
            return null;
        }

        double[] closes = candles.stream().mapToDouble(Candle::close).toArray();
        double[] highs = candles.stream().mapToDouble(Candle::high).toArray();
        double[] lows = candles.stream().mapToDouble(Candle::low).toArray();
        double[] volumes = candles.stream().mapToDouble(Candle::volume).toArray();

        double ema21 = ema(closes, EMA_SHORT);
        double ema50 = ema(closes, EMA_LONG);
        double ema21Slope = emaSlope(closes, EMA_SHORT, EMA_SLOPE_LOOKBACK);
        double rsi = rsi(closes, RSI_PERIOD);

        double[] bb = bollingerBands(closes, BB_PERIOD, BB_SIGMA);
        double bbUpper = bb[0];
        double bbMiddle = bb[1];
        double bbLower = bb[2];
        double bbWidth = (bbUpper - bbLower) / bbMiddle;
        double bbPercentB = (closes[closes.length - 1] - bbLower) / (bbUpper - bbLower);

        double atr = atr(highs, lows, closes, ATR_PERIOD);
        double vwap = vwap(candles);
        double adx = adx(highs, lows, closes, ADX_PERIOD);
        double volumeRatio = volumeRatio(volumes, VOLUME_AVG_PERIOD);

        double[] macdValues = macd(closes, MACD_FAST, MACD_SLOW, MACD_SIGNAL);

        return new IndicatorSnapshot(
            timeFrame, ema21, ema50, ema21Slope, rsi,
            bbUpper, bbMiddle, bbLower, bbWidth, bbPercentB,
            atr, vwap, adx, volumeRatio,
            macdValues[0], macdValues[1], macdValues[2]
        );
    }

    double ema(double[] data, int period) {
        double multiplier = 2.0 / (period + 1);
        double ema = data[0];
        for (int i = 1; i < data.length; i++) {
            ema = (data[i] - ema) * multiplier + ema;
        }
        return ema;
    }

    double emaSlope(double[] data, int emaPeriod, int slopeLookback) {
        double multiplier = 2.0 / (emaPeriod + 1);
        double[] emaValues = new double[data.length];
        emaValues[0] = data[0];
        for (int i = 1; i < data.length; i++) {
            emaValues[i] = (data[i] - emaValues[i - 1]) * multiplier + emaValues[i - 1];
        }
        int n = emaValues.length;
        if (n < slopeLookback + 1) return 0;
        double sumSlope = 0;
        for (int i = n - slopeLookback; i < n; i++) {
            sumSlope += (emaValues[i] - emaValues[i - 1]);
        }
        return sumSlope / slopeLookback;
    }

    double rsi(double[] closes, int period) {
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period && i < closes.length; i++) {
            double change = closes[i] - closes[i - 1];
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;
        for (int i = period + 1; i < closes.length; i++) {
            double change = closes[i] - closes[i - 1];
            if (change > 0) {
                avgGain = (avgGain * (period - 1) + change) / period;
                avgLoss = (avgLoss * (period - 1)) / period;
            } else {
                avgGain = (avgGain * (period - 1)) / period;
                avgLoss = (avgLoss * (period - 1) + Math.abs(change)) / period;
            }
        }
        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    double[] bollingerBands(double[] data, int period, double sigma) {
        int n = data.length;
        double sum = 0, sqSum = 0;
        int start = Math.max(0, n - period);
        int count = n - start;
        for (int i = start; i < n; i++) {
            sum += data[i];
            sqSum += data[i] * data[i];
        }
        double mean = sum / count;
        double variance = (sqSum / count) - (mean * mean);
        double stdDev = Math.sqrt(Math.max(0, variance));
        return new double[]{mean + sigma * stdDev, mean, mean - sigma * stdDev};
    }

    double atr(double[] highs, double[] lows, double[] closes, int period) {
        int n = highs.length;
        double atr = 0;
        for (int i = 1; i < Math.min(period + 1, n); i++) {
            double tr = Math.max(highs[i] - lows[i],
                Math.max(Math.abs(highs[i] - closes[i - 1]), Math.abs(lows[i] - closes[i - 1])));
            atr += tr;
        }
        atr /= Math.min(period, n - 1);
        for (int i = period + 1; i < n; i++) {
            double tr = Math.max(highs[i] - lows[i],
                Math.max(Math.abs(highs[i] - closes[i - 1]), Math.abs(lows[i] - closes[i - 1])));
            atr = (atr * (period - 1) + tr) / period;
        }
        return atr;
    }

    double vwap(List<Candle> candles) {
        double cumulativeTPV = 0;
        double cumulativeVolume = 0;
        for (Candle c : candles) {
            cumulativeTPV += c.typicalPrice() * c.volume();
            cumulativeVolume += c.volume();
        }
        return cumulativeVolume > 0 ? cumulativeTPV / cumulativeVolume : 0;
    }

    double adx(double[] highs, double[] lows, double[] closes, int period) {
        int n = highs.length;
        if (n < period * 2) return 0;
        double[] plusDM = new double[n];
        double[] minusDM = new double[n];
        double[] tr = new double[n];
        for (int i = 1; i < n; i++) {
            double upMove = highs[i] - highs[i - 1];
            double downMove = lows[i - 1] - lows[i];
            plusDM[i] = (upMove > downMove && upMove > 0) ? upMove : 0;
            minusDM[i] = (downMove > upMove && downMove > 0) ? downMove : 0;
            tr[i] = Math.max(highs[i] - lows[i],
                    Math.max(Math.abs(highs[i] - closes[i - 1]), Math.abs(lows[i] - closes[i - 1])));
        }
        double smoothedPlusDM = 0, smoothedMinusDM = 0, smoothedTR = 0;
        for (int i = 1; i <= period; i++) {
            smoothedPlusDM += plusDM[i];
            smoothedMinusDM += minusDM[i];
            smoothedTR += tr[i];
        }
        double sumDX = 0;
        int dxCount = 0;
        for (int i = period + 1; i < n; i++) {
            smoothedPlusDM = smoothedPlusDM - (smoothedPlusDM / period) + plusDM[i];
            smoothedMinusDM = smoothedMinusDM - (smoothedMinusDM / period) + minusDM[i];
            smoothedTR = smoothedTR - (smoothedTR / period) + tr[i];
            double plusDI = smoothedTR > 0 ? (smoothedPlusDM / smoothedTR) * 100 : 0;
            double minusDI = smoothedTR > 0 ? (smoothedMinusDM / smoothedTR) * 100 : 0;
            double diSum = plusDI + minusDI;
            double dx = diSum > 0 ? Math.abs(plusDI - minusDI) / diSum * 100 : 0;
            sumDX += dx;
            dxCount++;
        }
        return dxCount > 0 ? sumDX / dxCount : 0;
    }

    double volumeRatio(double[] volumes, int period) {
        int n = volumes.length;
        if (n < period + 1) return 1.0;
        double sum = 0;
        for (int i = n - period - 1; i < n - 1; i++) {
            sum += volumes[i];
        }
        double avg = sum / period;
        return avg > 0 ? volumes[n - 1] / avg : 1.0;
    }

    double[] macd(double[] data, int fastPeriod, int slowPeriod, int signalPeriod) {
        double fastMultiplier = 2.0 / (fastPeriod + 1);
        double slowMultiplier = 2.0 / (slowPeriod + 1);
        double sigMultiplier = 2.0 / (signalPeriod + 1);
        double fastEma = data[0];
        double slowEma = data[0];
        double[] macdLine = new double[data.length];
        for (int i = 1; i < data.length; i++) {
            fastEma = (data[i] - fastEma) * fastMultiplier + fastEma;
            slowEma = (data[i] - slowEma) * slowMultiplier + slowEma;
            macdLine[i] = fastEma - slowEma;
        }
        double signalLine = macdLine[0];
        for (int i = 1; i < macdLine.length; i++) {
            signalLine = (macdLine[i] - signalLine) * sigMultiplier + signalLine;
        }
        double lastMacd = macdLine[macdLine.length - 1];
        double histogram = lastMacd - signalLine;
        return new double[]{lastMacd, signalLine, histogram};
    }
}
