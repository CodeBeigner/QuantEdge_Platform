// ================================================================
// Data Generators — simulate market data until backend is connected
// ================================================================

function generateMarketData(days = 252) {
    const data = [];
    let price = 100;
    const prices = [];

    for (let i = 0; i < days; i++) {
        const ret = (Math.random() - 0.48) * 3;
        price = Math.max(50, price * (1 + ret / 100));
        prices.push(price);

        const sma50 = prices.length >= 50
            ? prices.slice(-50).reduce((a, b) => a + b, 0) / 50
            : price;
        const sma200 = prices.length >= 200
            ? prices.slice(-200).reduce((a, b) => a + b, 0) / 200
            : price;

        const vol = 10 + Math.random() * 20 + (Math.sin(i / 30) * 8);
        const bid = price - (0.01 + Math.random() * 0.05);
        const ask = price + (0.01 + Math.random() * 0.05);
        const volume = Math.floor(500000 + Math.random() * 2000000);

        data.push({
            day: i + 1,
            price: +price.toFixed(2),
            sma50: +sma50.toFixed(2),
            sma200: +sma200.toFixed(2),
            volatility: +vol.toFixed(1),
            rollingVol: +vol.toFixed(1),
            bid: +bid.toFixed(2),
            ask: +ask.toFixed(2),
            spread: +(ask - bid).toFixed(4),
            volume,
            correlation: +(Math.sin(i / 40) * 0.6 + (Math.random() - 0.5) * 0.3).toFixed(3),
        });
    }
    return data;
}
