// ================================================================
// API CLIENT — All communication with the Spring Boot backend
// ================================================================

const API_BASE = 'http://localhost:8080/api/v1';

const ApiClient = {
    // ── Internal helpers ────────────────────────────────────────

    _getToken() {
        return localStorage.getItem('quantedge_token');
    },

    _headers(withAuth = true) {
        const h = { 'Content-Type': 'application/json' };
        if (withAuth) {
            const token = this._getToken();
            if (token) h['Authorization'] = 'Bearer ' + token;
        }
        return h;
    },

    async _fetch(url, options = {}, retries = 3) {
        let lastErr;
        for (let attempt = 0; attempt < retries; attempt++) {
            try {
                const res = await fetch(url, options);
                if (res.status === 401) {
                    localStorage.removeItem('quantedge_token');
                    localStorage.removeItem('quantedge_user');
                    window.dispatchEvent(new Event('auth:expired'));
                    throw new Error('Session expired');
                }
                return res;
            } catch (err) {
                lastErr = err;
                if (err.message === 'Session expired') throw err;
                if (attempt < retries - 1) {
                    await new Promise(r => setTimeout(r, Math.pow(2, attempt) * 1000));
                    console.warn(`Retry ${attempt + 1}/${retries - 1} for ${url}`);
                }
            }
        }
        console.error('API Error after retries:', lastErr);
        throw lastErr;
    },

    // ── Auth ────────────────────────────────────────────────────

    async register(name, email, password) {
        const res = await this._fetch(API_BASE + '/auth/register', {
            method: 'POST',
            headers: this._headers(false),
            body: JSON.stringify({ name, email, password }),
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Registration failed');
        localStorage.setItem('quantedge_token', data.token);
        localStorage.setItem('quantedge_user', JSON.stringify(data));
        return data;
    },

    async login(email, password) {
        const res = await this._fetch(API_BASE + '/auth/login', {
            method: 'POST',
            headers: this._headers(false),
            body: JSON.stringify({ email, password }),
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Login failed');
        localStorage.setItem('quantedge_token', data.token);
        localStorage.setItem('quantedge_user', JSON.stringify(data));
        return data;
    },

    logout() {
        localStorage.removeItem('quantedge_token');
        localStorage.removeItem('quantedge_user');
    },

    getUser() {
        try {
            return JSON.parse(localStorage.getItem('quantedge_user'));
        } catch { return null; }
    },

    isLoggedIn() {
        return !!this._getToken();
    },

    // ── Market Data ─────────────────────────────────────────────

    async getSymbols() {
        const res = await this._fetch(API_BASE + '/market-data/symbols', {
            headers: this._headers(),
        });
        return res.json();
    },

    async getPrices(symbol, days = 252) {
        const res = await this._fetch(
            API_BASE + '/market-data/prices/' + symbol + '?days=' + days,
            { headers: this._headers() }
        );
        return res.json();
    },

    async getPricesByRange(symbol, start, end) {
        const res = await this._fetch(
            API_BASE + '/market-data/prices/' + symbol + '?start=' + start + '&end=' + end,
            { headers: this._headers() }
        );
        return res.json();
    },

    async getSummary(symbol) {
        const res = await this._fetch(
            API_BASE + '/market-data/summary/' + symbol,
            { headers: this._headers() }
        );
        return res.json();
    },

    // ── Strategies ──────────────────────────────────────────────

    async getStrategies() {
        const res = await this._fetch(API_BASE + '/strategies', {
            headers: this._headers(),
        });
        return res.json();
    },

    async getStrategy(id) {
        const res = await this._fetch(API_BASE + '/strategies/' + id, {
            headers: this._headers(),
        });
        return res.json();
    },

    async createStrategy(data) {
        const res = await this._fetch(API_BASE + '/strategies', {
            method: 'POST',
            headers: this._headers(),
            body: JSON.stringify(data),
        });
        const result = await res.json();
        if (!res.ok) throw new Error(result.error || 'Failed to create strategy');
        return result;
    },

    async executeStrategy(id) {
        const res = await this._fetch(API_BASE + '/strategies/' + id + '/execute', {
            method: 'POST',
            headers: this._headers(),
        });
        const result = await res.json();
        if (!res.ok) throw new Error(result.error || 'Execution failed');
        return result;
    },

    async deleteStrategy(id) {
        const res = await this._fetch(API_BASE + '/strategies/' + id, {
            method: 'DELETE',
            headers: this._headers(),
        });
        if (!res.ok) throw new Error('Delete failed');
    },

    // ── Backtests ───────────────────────────────────────────────

    async runBacktest(strategyId, startDate, endDate, initialCapital) {
        const res = await this._fetch(API_BASE + '/backtests', {
            method: 'POST',
            headers: this._headers(),
            body: JSON.stringify({ strategyId, startDate, endDate, initialCapital }),
        });
        const result = await res.json();
        if (!res.ok) throw new Error(result.error || 'Backtest failed');
        return result;
    },

    async getBacktests(strategyId) {
        const res = await this._fetch(API_BASE + '/backtests/' + strategyId, {
            headers: this._headers(),
        });
        return res.json();
    },

    // ── Agents ──────────────────────────────────────────────────

    async getAgents() {
        const res = await this._fetch(API_BASE + '/agents', {
            headers: this._headers(),
        });
        return res.json();
    },

    async createAgent(data) {
        const res = await this._fetch(API_BASE + '/agents', {
            method: 'POST',
            headers: this._headers(),
            body: JSON.stringify(data),
        });
        const result = await res.json();
        if (!res.ok) throw new Error(result.error || 'Failed to create agent');
        return result;
    },

    async startAgent(id) {
        const res = await this._fetch(API_BASE + '/agents/' + id + '/start', {
            method: 'POST',
            headers: this._headers(),
        });
        return res.json();
    },

    async stopAgent(id) {
        const res = await this._fetch(API_BASE + '/agents/' + id + '/stop', {
            method: 'POST',
            headers: this._headers(),
        });
        return res.json();
    },

    // ── Health ──────────────────────────────────────────────────

    async getHealth() {
        try {
            const res = await fetch('http://localhost:8080/actuator/health');
            return res.json();
        } catch { return { status: 'DOWN' }; }
    },

    // ── Orders (OMS) ────────────────────────────────────────────

    async placeOrder(data) {
        const res = await this._fetch(API_BASE + '/orders', {
            method: 'POST', headers: this._headers(), body: JSON.stringify(data),
        });
        return res.json();
    },

    async getOrders() {
        const res = await this._fetch(API_BASE + '/orders', { headers: this._headers() });
        return res.json();
    },

    async cancelOrder(id) {
        const res = await this._fetch(API_BASE + '/orders/' + id + '/cancel', {
            method: 'POST', headers: this._headers(),
        });
        return res.json();
    },

    async getPositions() {
        const res = await this._fetch(API_BASE + '/orders/positions', { headers: this._headers() });
        return res.json();
    },

    async getPortfolio() {
        const res = await this._fetch(API_BASE + '/orders/portfolio', { headers: this._headers() });
        return res.json();
    },

    // ── Risk ────────────────────────────────────────────────────

    async getVaR(symbol, days = 252) {
        const res = await this._fetch(API_BASE + '/risk/var/' + symbol + '?days=' + days, {
            headers: this._headers(),
        });
        return res.json();
    },

    async checkPositionLimits() {
        const res = await this._fetch(API_BASE + '/risk/positions', { headers: this._headers() });
        return res.json();
    },

    async getPortfolioRisk() {
        const res = await this._fetch(API_BASE + '/risk/portfolio', { headers: this._headers() });
        return res.json();
    },

    // ── Alerts ──────────────────────────────────────────────────

    async getAlerts() {
        const res = await this._fetch(API_BASE + '/alerts', { headers: this._headers() });
        return res.json();
    },

    async getUnacknowledgedAlerts() {
        const res = await this._fetch(API_BASE + '/alerts/unacknowledged', { headers: this._headers() });
        return res.json();
    },

    async acknowledgeAlert(id) {
        const res = await this._fetch(API_BASE + '/alerts/' + id + '/acknowledge', {
            method: 'POST', headers: this._headers(),
        });
        return res.json();
    },

    // ── ML Service ──────────────────────────────────────────────

    async mlPredict(symbol) {
        const res = await this._fetch(API_BASE + '/ml/predict/' + symbol, {
            method: 'POST', headers: this._headers(),
        });
        return res.json();
    },

    async mlTrain(symbol) {
        const res = await this._fetch(API_BASE + '/ml/train/' + symbol, {
            method: 'POST', headers: this._headers(),
        });
        return res.json();
    },

    async mlFeatures(symbol) {
        const res = await this._fetch(API_BASE + '/ml/features/' + symbol, { headers: this._headers() });
        return res.json();
    },

    async mlOptimize(symbols) {
        const res = await this._fetch(API_BASE + '/ml/optimize', {
            method: 'POST', headers: this._headers(),
            body: JSON.stringify({ symbols }),
        });
        return res.json();
    },

    async mlSignals() {
        const res = await this._fetch(API_BASE + '/ml/signals', { headers: this._headers() });
        return res.json();
    },

    async mlHealth() {
        const res = await this._fetch(API_BASE + '/ml/health', { headers: this._headers() });
        return res.json();
    },
};
