# UI Rewrite for New ML Endpoints — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the React ML page work against the new `/predict-meta` and `/predict-flow` endpoints (and their companion training endpoints), replacing the broken calls to the deprecated `/predict-ensemble` pathway. User-facing: the ML tab shows a meaningful prediction again.

**Architecture:** Thin pass-throughs on the Java side (4 new routes in `MLController`) that proxy to the Python endpoints. React page gains two side-by-side prediction panels ("Meta Filter" and "Order Flow"), plus a "Train" action per panel. Legacy page components (ensemble/LSTM badges, 3-col direction_prob bar chart) are deleted, not hidden.

**Tech Stack:** React 19 + TypeScript 5.9, TanStack Query (`useMutation`/`useQuery`), Tailwind 4, lucide-react icons. Spring Boot 3.5 controller + `RestTemplate` for the Python proxy. No new deps.

**Parent spec:** `docs/superpowers/specs/2026-05-10-ml-rebuild-and-paper-trading-design.md` §4.4 (UI surface area is implied by "Learn While Earning" product vision in `project_quantedge_product_vision.md` — every trade and prediction must be inspectable, not hidden).

---

## Scope Boundary

**In scope:**
- 4 new Java proxy routes on `MLController`: `POST /ml/train-meta/{symbol}`, `POST /ml/predict-meta/{symbol}`, `POST /ml/train-flow/{symbol}`, `POST /ml/predict-flow/{symbol}`.
- `MLClientService` gains matching methods.
- Frontend `api.ts`: remove `mlPredict`, `mlTrain`, `mlPredictEnsemble`, `mlTrainLstm`. Add `mlPredictMeta`, `mlTrainMeta`, `mlPredictFlow`, `mlTrainFlow`.
- Frontend `types/index.ts`: remove `MLPrediction`. Add `MetaPrediction`, `FlowPrediction`, `MetaTrainResult`, `FlowTrainResult`.
- Frontend `MLPage.tsx`: fully rewritten.
- One manual smoke test against live backend + ml-service.

**Out of scope:**
- Admin endpoint UI (Plan 4).
- Live signal stream / websocket integration.
- Re-skinning the rest of the app.
- Feature-schema version checks (flagged in Plan 2 final review; deferred to Plan 4).

---

## File Layout

### Created
- _None_ (all modifications to existing files).

### Modified
- `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/MLClientService.java` — add 4 methods
- `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/controller/MLController.java` — add 4 routes
- `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/MLClientServiceTest.java` — new test file (path tests that proxy builds correct URL)
- `frontend/src/services/api.ts` — swap ML section
- `frontend/src/types/index.ts` — swap `MLPrediction` for `MetaPrediction` + `FlowPrediction` + train result types
- `frontend/src/pages/MLPage.tsx` — full rewrite

---

## Task 1: Java proxy methods in MLClientService

**Files:**
- Modify: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/MLClientService.java`
- Create: `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/MLClientServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/MLClientServiceTest.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.repository.MLSignalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
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
```

Run (Java 21 in PATH):
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication
./mvnw test -Dtest=MLClientServiceTest 2>&1 | tail -15
```
Expected: COMPILATION FAILURE — methods don't exist.

- [ ] **Step 2: Add the 4 methods to `MLClientService.java`**

Edit `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/MLClientService.java`. Add these methods right before the closing brace of the class (after `health()`):

```java
    /**
     * Score a primary signal via the meta-labeler.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> predictMeta(
            String symbol, String primarySignal,
            double entryPrice, double tpPct, double slPct) {
        try {
            Map<String, Object> body = Map.of(
                    "primary_signal", primarySignal,
                    "entry_price", entryPrice,
                    "tp_pct", tpPct,
                    "sl_pct", slPct);
            ResponseEntity<Map> res = restTemplate.postForEntity(
                    ML_SERVICE_URL + "/predict-meta/" + symbol, body, Map.class);
            return res.getBody();
        } catch (Exception e) {
            log.warn("predict-meta failed for {}: {}", symbol, e.getMessage());
            return Map.of("error", "ML service unavailable", "message", e.getMessage());
        }
    }

    /**
     * Train (or retrain) the meta-labeler for a symbol.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> trainMeta(String symbol) {
        try {
            ResponseEntity<Map> res = restTemplate.postForEntity(
                    ML_SERVICE_URL + "/train-meta/" + symbol, null, Map.class);
            return res.getBody();
        } catch (Exception e) {
            log.warn("train-meta failed for {}: {}", symbol, e.getMessage());
            return Map.of("error", "ML service unavailable", "message", e.getMessage());
        }
    }

    /**
     * Score order-flow direction for a symbol.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> predictFlow(String symbol, int lookbackBars) {
        try {
            Map<String, Object> body = Map.of("lookback_bars", lookbackBars);
            ResponseEntity<Map> res = restTemplate.postForEntity(
                    ML_SERVICE_URL + "/predict-flow/" + symbol, body, Map.class);
            return res.getBody();
        } catch (Exception e) {
            log.warn("predict-flow failed for {}: {}", symbol, e.getMessage());
            return Map.of("error", "ML service unavailable", "message", e.getMessage());
        }
    }

    /**
     * Train (or retrain) the order-flow model for a symbol.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> trainFlow(String symbol) {
        try {
            ResponseEntity<Map> res = restTemplate.postForEntity(
                    ML_SERVICE_URL + "/train-flow/" + symbol, null, Map.class);
            return res.getBody();
        } catch (Exception e) {
            log.warn("train-flow failed for {}: {}", symbol, e.getMessage());
            return Map.of("error", "ML service unavailable", "message", e.getMessage());
        }
    }
```

- [ ] **Step 3: Rerun tests**

```
./mvnw test -Dtest=MLClientServiceTest 2>&1 | tail -15
```
Expected: 4 passed.

Also `./mvnw -q compile 2>&1 | tail -5` → BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/MLClientService.java \
        QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/MLClientServiceTest.java
git commit -m "feat(ml): MLClientService proxies to /predict-meta and /predict-flow

Four new methods (trainMeta, predictMeta, trainFlow, predictFlow)
wrap the corresponding Python endpoints with the same fail-soft
pattern as the existing train/predict: network error returns
{error, message} instead of throwing, so the HTTP layer stays 200.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Java controller routes

**Files:**
- Modify: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/controller/MLController.java`

- [ ] **Step 1: Add the 4 routes**

Edit `MLController.java`. After the existing `health()` method (just before the closing brace), add:

```java
    public record PredictMetaRequest(
            String primarySignal,
            Double entryPrice,
            Double tpPct,
            Double slPct
    ) {}

    public record PredictFlowRequest(Integer lookbackBars) {}

    @PostMapping("/train-meta/{symbol}")
    public ResponseEntity<?> trainMeta(@PathVariable String symbol) {
        return ResponseEntity.ok(mlClient.trainMeta(symbol));
    }

    @PostMapping("/predict-meta/{symbol}")
    public ResponseEntity<?> predictMeta(
            @PathVariable String symbol,
            @RequestBody PredictMetaRequest req) {
        double entryPrice = req.entryPrice() != null ? req.entryPrice() : 0.0;
        double tpPct = req.tpPct() != null ? req.tpPct() : 0.02;
        double slPct = req.slPct() != null ? req.slPct() : 0.01;
        String primary = req.primarySignal() != null ? req.primarySignal() : "LONG";
        return ResponseEntity.ok(mlClient.predictMeta(symbol, primary, entryPrice, tpPct, slPct));
    }

    @PostMapping("/train-flow/{symbol}")
    public ResponseEntity<?> trainFlow(@PathVariable String symbol) {
        return ResponseEntity.ok(mlClient.trainFlow(symbol));
    }

    @PostMapping("/predict-flow/{symbol}")
    public ResponseEntity<?> predictFlow(
            @PathVariable String symbol,
            @RequestBody(required = false) PredictFlowRequest req) {
        int lookback = (req != null && req.lookbackBars() != null) ? req.lookbackBars() : 200;
        return ResponseEntity.ok(mlClient.predictFlow(symbol, lookback));
    }
```

- [ ] **Step 2: Verify compile**

```
./mvnw -q compile 2>&1 | tail -5
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/controller/MLController.java
git commit -m "feat(api): /ml/train-meta /ml/predict-meta /ml/train-flow /ml/predict-flow

Four proxy routes expose the new Python endpoints through the Java
backend so the frontend can call them via the standard /api/v1/ml/*
prefix. Jackson records carry the request payloads; missing fields
fall back to sensible defaults (tp=2%, sl=1%, lookback=200 bars).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Frontend API client + types

**Files:**
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/types/index.ts`

- [ ] **Step 1: Replace the `MLPrediction` type in `types/index.ts`**

Find the block (currently around line 151):

```typescript
export interface MLPrediction {
  signal: string;
  confidence: number;
  direction_prob: { up: number; down: number };
  features?: Record<string, number>;
  model_accuracy?: number;
  ensemble?: boolean;
  model_used?: string;
}
```

Replace with:

```typescript
export interface MetaPrediction {
  symbol: string;
  meta_prob: number;
  direction: -1 | 0 | 1;
  primary_signal: 'LONG' | 'SHORT';
  error?: string;
  message?: string;
}

export interface MetaTrainResult {
  symbol?: string;
  n_train?: number;
  n_dropped_timeout?: number;
  train_accuracy?: number;
  feature_cols?: string[];
  saved_to?: string;
  error?: string;
  message?: string;
}

export interface FlowPrediction {
  symbol: string;
  flow_score: number;
  direction: -1 | 0 | 1;
  probs: { short: number; flat: number; long: number };
  error?: string;
  message?: string;
}

export interface FlowTrainResult {
  symbol?: string;
  n_train?: number;
  train_accuracy?: number;
  feature_cols?: string[];
  forward_bars?: number;
  saved_to?: string;
  error?: string;
  message?: string;
}
```

- [ ] **Step 2: Replace the ML block in `services/api.ts`**

Find the current block (around lines 212-220):

```typescript
  // ML
  mlPredict: (symbol: string) => post<MLPrediction>(`/ml/predict/${symbol}`),
  mlTrain: (symbol: string) => post<Record<string, unknown>>(`/ml/train/${symbol}`),
  mlFeatures: (symbol: string) => get<Record<string, number>[]>(`/ml/features/${symbol}`),
  mlOptimize: (symbols: string[]) => post<Record<string, unknown>>('/ml/optimize', { symbols }),
  mlSignals: () => get<Record<string, unknown>>('/ml/signals'),
  mlHealth: () => get<Record<string, unknown>>('/ml/health'),
  mlPredictEnsemble: (symbol: string) => post<MLPrediction>(`/ml/predict-ensemble/${symbol}`),
  mlTrainLstm: (symbol: string) => post<Record<string, unknown>>(`/ml/train-lstm/${symbol}`),
```

Replace with:

```typescript
  // ML — triple-barrier meta-labeler + order-flow model
  mlTrainMeta: (symbol: string) =>
    post<MetaTrainResult>(`/ml/train-meta/${symbol}`, {}),
  mlPredictMeta: (
    symbol: string,
    primary: 'LONG' | 'SHORT' = 'LONG',
    entryPrice = 0,
    tpPct = 0.02,
    slPct = 0.01,
  ) =>
    post<MetaPrediction>(`/ml/predict-meta/${symbol}`, {
      primarySignal: primary,
      entryPrice,
      tpPct,
      slPct,
    }),
  mlTrainFlow: (symbol: string) =>
    post<FlowTrainResult>(`/ml/train-flow/${symbol}`, {}),
  mlPredictFlow: (symbol: string, lookbackBars = 200) =>
    post<FlowPrediction>(`/ml/predict-flow/${symbol}`, { lookbackBars }),
  mlFeatures: (symbol: string) => get<Record<string, number>[]>(`/ml/features/${symbol}`),
  mlOptimize: (symbols: string[]) => post<Record<string, unknown>>('/ml/optimize', { symbols }),
  mlSignals: () => get<Record<string, unknown>>('/ml/signals'),
  mlHealth: () => get<Record<string, unknown>>('/ml/health'),
```

Then also update the import at the top of `api.ts`: add `MetaPrediction, MetaTrainResult, FlowPrediction, FlowTrainResult` to the type import list (replacing `MLPrediction` which no longer exists).

- [ ] **Step 3: TypeScript check**

```
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/frontend
npx tsc --noEmit 2>&1 | tail -20
```
Expected: errors ONLY in `MLPage.tsx` (it still imports `MLPrediction`). No errors in `api.ts` or `types/index.ts`.

- [ ] **Step 4: Commit (partial — page rewrite comes next)**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add frontend/src/services/api.ts frontend/src/types/index.ts
git commit -m "refactor(ui): swap MLPrediction type for MetaPrediction + FlowPrediction

MLPage is temporarily broken on purpose. The next commit rewrites it
against the new types. Four API client methods replace the
deprecated /predict, /train, /predict-ensemble, /train-lstm wrappers.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Rewrite `MLPage.tsx`

**Files:**
- Modify (full rewrite): `frontend/src/pages/MLPage.tsx`

- [ ] **Step 1: Overwrite the file**

Replace the entire contents of `frontend/src/pages/MLPage.tsx` with:

```tsx
import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Activity, Loader2, TrendingUp, TrendingDown, Pause, AlertCircle } from 'lucide-react';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';
import type {
  MetaPrediction, MetaTrainResult,
  FlowPrediction, FlowTrainResult,
} from '@/types';

const DEFAULT_SYMBOLS = ['BTCUSDT', 'ETHUSDT'];

function DirectionBadge({ direction }: { direction: -1 | 0 | 1 }) {
  if (direction === 1) {
    return (
      <span
        className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm font-bold"
        style={{ background: 'rgba(0,255,136,0.15)', color: 'var(--tertiary)' }}
      >
        <TrendingUp size={14} /> LONG
      </span>
    );
  }
  if (direction === -1) {
    return (
      <span
        className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm font-bold"
        style={{ background: 'rgba(239,68,68,0.15)', color: 'var(--error)' }}
      >
        <TrendingDown size={14} /> SHORT
      </span>
    );
  }
  return (
    <span
      className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm font-bold"
      style={{ background: 'rgba(251,191,36,0.15)', color: '#fbbf24' }}
    >
      <Pause size={14} /> FLAT
    </span>
  );
}

function ProbBar({ label, value }: { label: string; value: number }) {
  const pct = (value * 100).toFixed(1);
  const color =
    value >= 0.7 ? 'from-[#00ff88] to-[#3b82f6]' :
    value >= 0.4 ? 'from-[#fbbf24] to-[#f97316]' :
                   'from-[#ef4444] to-[#f97316]';
  return (
    <div>
      <div className="flex justify-between text-xs mb-1" style={{ color: 'var(--on-surface-variant)' }}>
        <span>{label}</span>
        <span>{pct}%</span>
      </div>
      <div className="h-2 rounded-full overflow-hidden" style={{ background: 'var(--surface)' }}>
        <div
          className={`h-full rounded-full bg-gradient-to-r ${color} transition-all`}
          style={{ width: `${Math.min(100, Math.max(0, value * 100))}%` }}
        />
      </div>
    </div>
  );
}

function ErrorCallout({ error, message }: { error: string; message?: string }) {
  return (
    <div
      className="flex items-start gap-2 p-3 rounded text-sm"
      style={{ background: 'rgba(239,68,68,0.1)', color: 'var(--error)' }}
    >
      <AlertCircle size={16} className="shrink-0 mt-0.5" />
      <div>
        <div className="font-semibold">{error}</div>
        {message && <div className="text-xs opacity-80">{message}</div>}
      </div>
    </div>
  );
}

function MetaPanel({ symbol }: { symbol: string }) {
  const [primary, setPrimary] = useState<'LONG' | 'SHORT'>('LONG');
  const [entryPrice, setEntryPrice] = useState<string>('');

  const train = useMutation<MetaTrainResult>({
    mutationFn: () => api.mlTrainMeta(symbol),
  });
  const predict = useMutation<MetaPrediction>({
    mutationFn: () => api.mlPredictMeta(symbol, primary, Number(entryPrice) || 0),
  });

  const pred = predict.data;
  const trained = train.data;

  return (
    <div
      className="p-5 space-y-4 rounded-lg"
      style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}
    >
      <div className="flex items-baseline justify-between">
        <h3 className="font-semibold" style={{ color: 'var(--on-surface)' }}>
          Meta Filter — {symbol}
        </h3>
        <span className="text-xs" style={{ color: 'var(--outline)' }}>XGBoost · Triple-Barrier</span>
      </div>

      <div className="flex gap-2">
        <select
          value={primary}
          onChange={(e) => setPrimary(e.target.value as 'LONG' | 'SHORT')}
          className="px-3 py-2 rounded text-sm"
          style={{ background: 'var(--surface)', color: 'var(--on-surface)', border: '1px solid var(--outline-variant)' }}
        >
          <option value="LONG">LONG signal</option>
          <option value="SHORT">SHORT signal</option>
        </select>
        <input
          type="number"
          value={entryPrice}
          onChange={(e) => setEntryPrice(e.target.value)}
          placeholder="Entry price (optional)"
          className="flex-1 px-3 py-2 rounded text-sm"
          style={{ background: 'var(--surface)', color: 'var(--on-surface)', border: '1px solid var(--outline-variant)' }}
        />
      </div>

      <div className="flex gap-2">
        <button
          onClick={() => predict.mutate()}
          disabled={predict.isPending}
          className="flex-1 px-4 py-2 rounded text-sm font-medium transition-colors"
          style={{ background: 'var(--primary)', color: 'var(--on-primary)' }}
        >
          {predict.isPending ? <Loader2 size={14} className="inline animate-spin mr-1" /> : null}
          Predict
        </button>
        <button
          onClick={() => train.mutate()}
          disabled={train.isPending}
          className="px-4 py-2 rounded text-sm"
          style={{ background: 'var(--surface)', color: 'var(--on-surface-variant)', border: '1px solid var(--outline-variant)' }}
        >
          {train.isPending ? <Loader2 size={14} className="inline animate-spin mr-1" /> : null}
          Train
        </button>
      </div>

      {pred?.error && <ErrorCallout error={pred.error} message={pred.message} />}

      {pred && !pred.error && (
        <div className="space-y-3">
          <div className="flex items-center gap-3">
            <DirectionBadge direction={pred.direction as -1 | 0 | 1} />
            <span className="text-xs" style={{ color: 'var(--outline)' }}>
              on {pred.primary_signal} signal
            </span>
          </div>
          <ProbBar label="Meta probability" value={pred.meta_prob} />
        </div>
      )}

      {trained?.error && <ErrorCallout error={trained.error} message={trained.message} />}

      {trained && !trained.error && (
        <div
          className="text-xs space-y-1 p-2 rounded"
          style={{ background: 'var(--surface)', color: 'var(--on-surface-variant)' }}
        >
          <div>Trained on {trained.n_train} samples</div>
          {trained.n_dropped_timeout !== undefined && (
            <div>Dropped {trained.n_dropped_timeout} timeout rows</div>
          )}
          {trained.train_accuracy !== undefined && (
            <div>Train accuracy: {(trained.train_accuracy * 100).toFixed(1)}%</div>
          )}
        </div>
      )}
    </div>
  );
}

function FlowPanel({ symbol }: { symbol: string }) {
  const train = useMutation<FlowTrainResult>({
    mutationFn: () => api.mlTrainFlow(symbol),
  });
  const predict = useMutation<FlowPrediction>({
    mutationFn: () => api.mlPredictFlow(symbol, 200),
  });

  const pred = predict.data;
  const trained = train.data;

  return (
    <div
      className="p-5 space-y-4 rounded-lg"
      style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}
    >
      <div className="flex items-baseline justify-between">
        <h3 className="font-semibold" style={{ color: 'var(--on-surface)' }}>
          Order Flow — {symbol}
        </h3>
        <span className="text-xs" style={{ color: 'var(--outline)' }}>LightGBM · Fallback features</span>
      </div>

      <div className="flex gap-2">
        <button
          onClick={() => predict.mutate()}
          disabled={predict.isPending}
          className="flex-1 px-4 py-2 rounded text-sm font-medium transition-colors"
          style={{ background: 'var(--primary)', color: 'var(--on-primary)' }}
        >
          {predict.isPending ? <Loader2 size={14} className="inline animate-spin mr-1" /> : null}
          Predict
        </button>
        <button
          onClick={() => train.mutate()}
          disabled={train.isPending}
          className="px-4 py-2 rounded text-sm"
          style={{ background: 'var(--surface)', color: 'var(--on-surface-variant)', border: '1px solid var(--outline-variant)' }}
        >
          {train.isPending ? <Loader2 size={14} className="inline animate-spin mr-1" /> : null}
          Train
        </button>
      </div>

      {pred?.error && <ErrorCallout error={pred.error} message={pred.message} />}

      {pred && !pred.error && (
        <div className="space-y-3">
          <div className="flex items-center gap-3">
            <DirectionBadge direction={pred.direction as -1 | 0 | 1} />
            <span className="text-xs" style={{ color: 'var(--outline)' }}>
              {pred.direction === 0 ? 'below confidence threshold' : `score ${(pred.flow_score * 100).toFixed(1)}%`}
            </span>
          </div>
          <div className="space-y-2">
            <ProbBar label="Long" value={pred.probs.long} />
            <ProbBar label="Flat" value={pred.probs.flat} />
            <ProbBar label="Short" value={pred.probs.short} />
          </div>
        </div>
      )}

      {trained?.error && <ErrorCallout error={trained.error} message={trained.message} />}

      {trained && !trained.error && (
        <div
          className="text-xs space-y-1 p-2 rounded"
          style={{ background: 'var(--surface)', color: 'var(--on-surface-variant)' }}
        >
          <div>Trained on {trained.n_train} samples (fwd = {trained.forward_bars} bars)</div>
          {trained.train_accuracy !== undefined && (
            <div>Train accuracy: {(trained.train_accuracy * 100).toFixed(1)}%</div>
          )}
        </div>
      )}
    </div>
  );
}

export default function MLPage() {
  const [symbol, setSymbol] = useState<string>(DEFAULT_SYMBOLS[0]);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        icon={<Activity size={20} />}
        title="ML Intelligence"
        subtitle="Triple-barrier meta-labeler + order-flow model"
      />

      <div className="flex items-center gap-3">
        <label className="text-sm" style={{ color: 'var(--on-surface-variant)' }}>Symbol</label>
        <select
          value={symbol}
          onChange={(e) => setSymbol(e.target.value)}
          className="px-3 py-2 rounded text-sm"
          style={{ background: 'var(--surface)', color: 'var(--on-surface)', border: '1px solid var(--outline-variant)' }}
        >
          {DEFAULT_SYMBOLS.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <MetaPanel key={`meta-${symbol}`} symbol={symbol} />
        <FlowPanel key={`flow-${symbol}`} symbol={symbol} />
      </div>
    </div>
  );
}
```

- [ ] **Step 2: TypeScript check**

```
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/frontend
npx tsc --noEmit 2>&1 | tail -15
```
Expected: 0 errors.

- [ ] **Step 3: Build check**

```
npm run build 2>&1 | tail -15
```
Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add frontend/src/pages/MLPage.tsx
git commit -m "feat(ui): rewrite MLPage for meta-labeler + order-flow

Two side-by-side panels (Meta Filter, Order Flow) replace the
ensemble/LSTM page. Each panel has Train + Predict buttons and
surfaces: direction badge, probability bars, train metrics,
graceful error callout on service-down. Symbol selector defaults
to BTCUSDT / ETHUSDT matching the seeded data.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: End-to-end smoke test

**Files:** none (manual validation).

- [ ] **Step 1: Ensure Docker + Postgres + ml-service + backend are running**

In separate terminals (or background):
- Docker compose is up (TimescaleDB on 5432, Redis on 6379).
- ml-service is running: `cd ml-service && DATABASE_URL=postgresql://postgres:Walktorem%4012@localhost:5432/postgres python3 main.py` (listens on 5001).
- Spring Boot backend is running on 8080.
- Frontend dev server on 3000: `cd frontend && npm run dev`.

If any are not running, start them and wait for health checks.

- [ ] **Step 2: Quick smoke in the browser**

1. Open `http://localhost:3000/ml` (or whatever the route is in `App.tsx`).
2. Select BTCUSDT.
3. Click "Train" on the Meta panel. Wait for success card showing `n_train`.
4. Click "Predict" on the Meta panel. Confirm a direction badge + probability bar appear.
5. Click "Train" on the Flow panel. Confirm success.
6. Click "Predict" on the Flow panel. Confirm a direction + 3 probability bars.

- [ ] **Step 3: Error-path smoke**

With the ml-service stopped (`Ctrl-C` the python process), click "Predict". Expect a red error callout, NOT a crash.

- [ ] **Step 4: Commit a chore stamp (optional) and report**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git commit --allow-empty -m "chore: UI rewrite verified end-to-end

Browser smoke: train → predict → error-path all passed for both
Meta and Flow panels on BTCUSDT seeded data.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- Meta-labeler surface: Task 4 Meta panel ✓
- Order-flow surface: Task 4 Flow panel ✓
- Deprecated endpoint removal: Task 3 drops the old `mlPredict`, `mlTrain`, `mlPredictEnsemble`, `mlTrainLstm` from the TS client ✓
- Java proxy: Tasks 1 + 2 ✓
- Error-path UX: the `ErrorCallout` component + backend's fail-soft wrapper ✓

**Placeholder scan:** no `TBD`, `TODO`, `handle error appropriately`, or other hand-waves. Every step has code.

**Type consistency checks:**
- `MetaPrediction.direction` is `-1 | 0 | 1` in types and rendered by `DirectionBadge` which accepts the same union. ✓
- `FlowPrediction.probs` has `short/flat/long` keys — the Flow panel renders all three. ✓
- `api.mlPredictMeta` signature matches `MLController.predictMeta` payload (camelCase on the wire via Spring's default `PropertyNamingStrategies.SNAKE_CASE` disabled — Spring Boot defaults to camelCase for records, so `primarySignal`, `entryPrice`, `tpPct`, `slPct` on the wire ALIGN with what the TS client sends).

**Known soft spots flagged inline:**
- The `MLPage` doesn't persist prediction history (single last-run surface). This is deliberate YAGNI for Plan 2.5; live signal history is a Plan 4 concern.
- The deprecated `/ml/predict` and `/ml/train` routes on the Java side are NOT deleted in this plan — they're still callable for debugging. Removing them is cosmetic and orthogonal.

---

**Out-of-band deferred items:**
- Weekly retrain cron → Plan 4
- Live signal stream / websocket prediction updates → Plan 4
- Feature-schema version check in `/predict-meta` → Plan 4
