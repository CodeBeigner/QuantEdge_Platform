package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.client.BinanceHistoricalClient;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Daily gap-filler: for each configured (symbol, timeframe), fetches the last
 * ~48h from Binance REST and upserts into market_data. Fills the gap between
 * the monthly Binance Vision bulk dump and "now" so live features have
 * continuous history.
 *
 * Runs at 00:15 UTC daily by default (cron: "0 15 0 * * *").
 */
@Slf4j
@Component
public class MarketDataSyncScheduler {

    private final BinanceHistoricalClient client;
    private final List<String> symbols;
    private final List<String> timeframes;
    private final JdbcTemplate jdbc;

    public MarketDataSyncScheduler(BinanceHistoricalClient client,
                                    List<String> symbols,
                                    List<String> timeframes) {
        this(client, symbols, timeframes, null);
    }

    @Autowired
    public MarketDataSyncScheduler(
            BinanceHistoricalClient client,
            @Value("${quantedge.sync.symbols:BTCUSD,ETHUSD}") String symbolsCsv,
            @Value("${quantedge.sync.timeframes:15m,1h,4h}") String timeframesCsv,
            JdbcTemplate jdbc) {
        this(client, List.of(symbolsCsv.split(",")), List.of(timeframesCsv.split(",")), jdbc);
    }

    private MarketDataSyncScheduler(BinanceHistoricalClient client,
                                     List<String> symbols,
                                     List<String> timeframes,
                                     JdbcTemplate jdbc) {
        this.client = client;
        this.symbols = symbols;
        this.timeframes = timeframes;
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "${quantedge.sync.cron:0 15 0 * * *}", zone = "UTC")
    public void runDaily() {
        runOnce();
    }

    /** One iteration; visible for manual trigger and tests. */
    public void runOnce() {
        Instant until = Instant.now();
        Instant since = until.minus(Duration.ofHours(48));
        log.info("Market data sync starting: symbols={} timeframes={} since={} until={}",
                 symbols, timeframes, since, until);
        int totalInserted = 0;
        for (String symbol : symbols) {
            for (String tf : timeframes) {
                try {
                    List<Candle> candles = client.fetchCandles(symbol, tf, since, until);
                    int inserted = jdbc != null
                        ? client.persistToMarketData(symbol, tf, candles, jdbc)
                        : 0;
                    totalInserted += inserted;
                    log.info("{} {}: fetched {} candles, inserted {}",
                             symbol, tf, candles.size(), inserted);
                } catch (Exception e) {
                    log.warn("{} {} sync failed: {}", symbol, tf, e.getMessage());
                }
            }
        }
        log.info("Market data sync done; inserted {} rows total", totalInserted);
    }
}
