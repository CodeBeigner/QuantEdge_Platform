package com.QuantPlatformApplication.QuantPlatformApplication.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Market hours calendar with holiday awareness.
 * Agents auto-pause outside market hours.
 */
@Slf4j
@Service
public class MarketHoursService {

    private static final ZoneId NYSE_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(16, 0);
    private static final LocalTime PRE_MARKET_OPEN = LocalTime.of(4, 0);
    private static final LocalTime AFTER_HOURS_CLOSE = LocalTime.of(20, 0);

    private static final Set<MonthDay> US_HOLIDAYS = Set.of(
            MonthDay.of(1, 1),   // New Year's Day
            MonthDay.of(7, 4),   // Independence Day
            MonthDay.of(12, 25)  // Christmas Day
    );

    private static final Set<DayOfWeek> WEEKEND = Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    public boolean isMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(NYSE_ZONE);
        return isMarketOpenAt(now);
    }

    public boolean isMarketOpenAt(ZonedDateTime dateTime) {
        if (WEEKEND.contains(dateTime.getDayOfWeek())) return false;
        if (isHoliday(dateTime.toLocalDate())) return false;

        LocalTime time = dateTime.toLocalTime();
        return !time.isBefore(MARKET_OPEN) && time.isBefore(MARKET_CLOSE);
    }

    public boolean isPreMarket() {
        ZonedDateTime now = ZonedDateTime.now(NYSE_ZONE);
        if (WEEKEND.contains(now.getDayOfWeek()) || isHoliday(now.toLocalDate())) return false;
        LocalTime time = now.toLocalTime();
        return !time.isBefore(PRE_MARKET_OPEN) && time.isBefore(MARKET_OPEN);
    }

    public boolean isAfterHours() {
        ZonedDateTime now = ZonedDateTime.now(NYSE_ZONE);
        if (WEEKEND.contains(now.getDayOfWeek()) || isHoliday(now.toLocalDate())) return false;
        LocalTime time = now.toLocalTime();
        return !time.isBefore(MARKET_CLOSE) && time.isBefore(AFTER_HOURS_CLOSE);
    }

    public boolean isHoliday(LocalDate date) {
        return US_HOLIDAYS.contains(MonthDay.from(date));
    }

    public boolean isTradingDay() {
        LocalDate today = LocalDate.now(NYSE_ZONE);
        return !WEEKEND.contains(today.getDayOfWeek()) && !isHoliday(today);
    }

    public Map<String, Object> getMarketStatus() {
        ZonedDateTime now = ZonedDateTime.now(NYSE_ZONE);
        String session;
        if (isMarketOpenAt(now)) session = "REGULAR";
        else if (isPreMarket()) session = "PRE_MARKET";
        else if (isAfterHours()) session = "AFTER_HOURS";
        else session = "CLOSED";

        return Map.of(
                "isOpen", isMarketOpen(),
                "session", session,
                "isTradingDay", isTradingDay(),
                "nyseTime", now.format(DateTimeFormatter.ofPattern("HH:mm:ss z")),
                "nextOpen", getNextOpenTime().toString(),
                "marketOpen", MARKET_OPEN.toString(),
                "marketClose", MARKET_CLOSE.toString()
        );
    }

    private ZonedDateTime getNextOpenTime() {
        ZonedDateTime now = ZonedDateTime.now(NYSE_ZONE);
        ZonedDateTime candidate = now.with(MARKET_OPEN);

        if (now.toLocalTime().isAfter(MARKET_OPEN) || !isTradingDay()) {
            candidate = candidate.plusDays(1);
        }

        while (WEEKEND.contains(candidate.getDayOfWeek()) || isHoliday(candidate.toLocalDate())) {
            candidate = candidate.plusDays(1);
        }

        return candidate;
    }
}
