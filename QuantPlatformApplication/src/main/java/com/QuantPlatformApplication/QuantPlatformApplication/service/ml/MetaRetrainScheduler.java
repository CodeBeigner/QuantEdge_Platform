package com.QuantPlatformApplication.QuantPlatformApplication.service.ml;

import com.QuantPlatformApplication.QuantPlatformApplication.service.MLClientService;
import com.QuantPlatformApplication.QuantPlatformApplication.service.telegram.TelegramBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Weekly Sunday 02:00 UTC retraining of the triple-barrier meta-labeler
 * for each configured symbol. The actual non-degradation gate (reject model
 * if train_accuracy drops >5 pp or n_train < 50) is enforced Python-side
 * inside /train-meta — this scheduler just surfaces the outcome via Telegram
 * so the operator sees the weekly status.
 *
 * Config:
 *   quantedge.meta.retrain.symbols  (default BTCUSDT,ETHUSDT)
 *   quantedge.meta.retrain.cron     (default "0 0 2 * * SUN")
 */
@Slf4j
@Component
public class MetaRetrainScheduler {

    private final MLClientService ml;
    private final TelegramBotService telegram;
    private final List<String> symbols;

    @Autowired
    public MetaRetrainScheduler(
            MLClientService ml,
            TelegramBotService telegram,
            @Value("${quantedge.meta.retrain.symbols:BTCUSDT,ETHUSDT}") String symbolsCsv) {
        this(ml, telegram, Arrays.asList(symbolsCsv.split(",")));
    }

    // Visible for tests.
    MetaRetrainScheduler(MLClientService ml, TelegramBotService telegram, List<String> symbols) {
        this.ml = ml;
        this.telegram = telegram;
        this.symbols = symbols;
    }

    @Scheduled(cron = "${quantedge.meta.retrain.cron:0 0 2 * * SUN}", zone = "UTC")
    public void onSchedule() {
        runOnce();
    }

    public void runOnce() {
        List<String> lines = new ArrayList<>();
        lines.add("*Weekly Meta-Labeler Retrain*");
        for (String symbol : symbols) {
            String s = symbol.trim();
            if (s.isEmpty()) continue;
            try {
                Map<String, Object> result = ml.trainMeta(s);
                Object acc = result.get("train_accuracy");
                Object n   = result.get("n_train");
                Object err = result.get("error");
                if (err != null) {
                    lines.add(String.format("%s: FAILED — %s", s, err));
                } else {
                    lines.add(String.format("%s: n_train=%s, acc=%.3f",
                        s, n, acc instanceof Number ? ((Number) acc).doubleValue() : Double.NaN));
                }
            } catch (Exception e) {
                log.warn("Retrain threw for {}: {}", s, e.getMessage());
                lines.add(String.format("%s: EXCEPTION — %s", s, e.getMessage()));
            }
        }
        telegram.sendMessage(String.join("\n", lines));
    }
}
