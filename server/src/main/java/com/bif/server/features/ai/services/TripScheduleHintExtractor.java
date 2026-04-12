package com.bif.server.features.ai.services;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TripScheduleHintExtractor {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d+)\\s*ng\\u00e0y");
    private static final Pattern WEEKS_PATTERN = Pattern.compile("(\\d+)\\s*tu\\u1ea7n");
    private static final Pattern DAYS_PATTERN_EN = Pattern.compile("(\\d+)\\s*day(?:s)?");
    private static final Pattern WEEKS_PATTERN_EN = Pattern.compile("(\\d+)\\s*week(?:s)?");
    private static final Pattern AFTER_DAYS_PATTERN = Pattern.compile("sau\\s+(\\d+)\\s*ng\\u00e0y");
    private static final Pattern AFTER_WEEKS_PATTERN = Pattern.compile("sau\\s+(\\d+)\\s*tu\\u1ea7n");
    private static final Pattern AFTER_DAYS_PATTERN_EN = Pattern.compile("(?:after|in)\\s+(\\d+)\\s*day(?:s)?");
    private static final Pattern AFTER_WEEKS_PATTERN_EN = Pattern.compile("(?:after|in)\\s+(\\d+)\\s*week(?:s)?");

    private final Clock clock;

    public TripScheduleHintExtractor() {
        this(Clock.system(DEFAULT_ZONE));
    }

    TripScheduleHintExtractor(Clock clock) {
        this.clock = clock;
    }

    public TripScheduleHints extract(String query) {
        if (query == null || query.isBlank()) {
            return TripScheduleHints.none();
        }

        String normalized = query.toLowerCase(Locale.ROOT);
        List<String> signals = new ArrayList<>();

        int daySpan = extractDaySpan(normalized, signals);
        int startOffsetDays = extractStartOffsetDays(normalized, signals);
        LocalTime preferredStartTime = resolvePreferredStartTime(normalized, signals);

        boolean hasTimingIntent = !signals.isEmpty();
        if (!hasTimingIntent) {
            return TripScheduleHints.none();
        }

        int normalizedDaySpan = Math.max(1, daySpan);
        LocalDate startDate = LocalDate.now(clock).plusDays(Math.max(startOffsetDays, 0));
        OffsetDateTime suggestedStart = ZonedDateTime.of(startDate, preferredStartTime, DEFAULT_ZONE)
                .toOffsetDateTime()
                .withSecond(0)
                .withNano(0);

        return new TripScheduleHints(
                true,
                normalizedDaySpan,
                Math.max(startOffsetDays, 0),
                suggestedStart,
                List.copyOf(signals)
        );
    }

    private int extractDaySpan(String normalized, List<String> signals) {
        int daySpan = 0;

        Matcher dayMatcher = DAYS_PATTERN.matcher(normalized);
        while (dayMatcher.find()) {
            if (isOffsetExpression(normalized, dayMatcher.start())) {
                continue;
            }
            int value = parseInt(dayMatcher.group(1));
            if (value > daySpan) {
                daySpan = value;
            }
        }

        Matcher weekMatcher = WEEKS_PATTERN.matcher(normalized);
        while (weekMatcher.find()) {
            if (isOffsetExpression(normalized, weekMatcher.start())) {
                continue;
            }
            int value = parseInt(weekMatcher.group(1)) * 7;
            if (value > daySpan) {
                daySpan = value;
            }
        }

        Matcher dayMatcherEn = DAYS_PATTERN_EN.matcher(normalized);
        while (dayMatcherEn.find()) {
            if (isOffsetExpression(normalized, dayMatcherEn.start())) {
                continue;
            }
            int value = parseInt(dayMatcherEn.group(1));
            if (value > daySpan) {
                daySpan = value;
            }
        }

        Matcher weekMatcherEn = WEEKS_PATTERN_EN.matcher(normalized);
        while (weekMatcherEn.find()) {
            if (isOffsetExpression(normalized, weekMatcherEn.start())) {
                continue;
            }
            int value = parseInt(weekMatcherEn.group(1)) * 7;
            if (value > daySpan) {
                daySpan = value;
            }
        }

        if (normalized.contains("m\u1ed9t ng\u00e0y") || normalized.contains("1 ng\u00e0y")) {
            daySpan = Math.max(daySpan, 1);
        }
        if (normalized.contains("hai ng\u00e0y") || normalized.contains("2 ng\u00e0y")) {
            daySpan = Math.max(daySpan, 2);
        }
        if (normalized.contains("ba ng\u00e0y") || normalized.contains("3 ng\u00e0y")) {
            daySpan = Math.max(daySpan, 3);
        }
        if (normalized.contains("one day") || normalized.contains("1 day")) {
            daySpan = Math.max(daySpan, 1);
        }
        if (normalized.contains("two days") || normalized.contains("2 days")) {
            daySpan = Math.max(daySpan, 2);
        }
        if (normalized.contains("three days") || normalized.contains("3 days")) {
            daySpan = Math.max(daySpan, 3);
        }
        if (normalized.contains("weekend")) {
            daySpan = Math.max(daySpan, 2);
        }

        if (daySpan > 0) {
            signals.add("duration=" + daySpan + "d");
        }
        return daySpan;
    }

    private int extractStartOffsetDays(String normalized, List<String> signals) {
        int offset = 0;

        Matcher afterDays = AFTER_DAYS_PATTERN.matcher(normalized);
        while (afterDays.find()) {
            offset = Math.max(offset, parseInt(afterDays.group(1)));
        }

        Matcher afterWeeks = AFTER_WEEKS_PATTERN.matcher(normalized);
        while (afterWeeks.find()) {
            offset = Math.max(offset, parseInt(afterWeeks.group(1)) * 7);
        }

        Matcher afterDaysEn = AFTER_DAYS_PATTERN_EN.matcher(normalized);
        while (afterDaysEn.find()) {
            offset = Math.max(offset, parseInt(afterDaysEn.group(1)));
        }

        Matcher afterWeeksEn = AFTER_WEEKS_PATTERN_EN.matcher(normalized);
        while (afterWeeksEn.find()) {
            offset = Math.max(offset, parseInt(afterWeeksEn.group(1)) * 7);
        }

        if (normalized.contains("sau m\u1ed9t tu\u1ea7n")) {
            offset = Math.max(offset, 7);
        }
        if (normalized.contains("tu\u1ea7n sau")) {
            offset = Math.max(offset, 7);
        }
        if (normalized.contains("ng\u00e0y mai")) {
            offset = Math.max(offset, 1);
        }
        if (normalized.contains("tomorrow")) {
            offset = Math.max(offset, 1);
        }
        if (normalized.contains("next week")) {
            offset = Math.max(offset, 7);
        }
        if (normalized.contains("h\u00f4m nay")) {
            signals.add("start=today");
        }
        if (normalized.contains("today")) {
            signals.add("start=today");
        }

        if (offset > 0) {
            signals.add("startOffset=" + offset + "d");
        }
        return offset;
    }

    private LocalTime resolvePreferredStartTime(String normalized, List<String> signals) {
        if (normalized.contains("bu\u1ed5i t\u1ed1i") || normalized.contains("t\u1ed1i")) {
            signals.add("time=evening");
            return LocalTime.of(18, 30);
        }
        if (normalized.contains("evening") || normalized.contains("night")) {
            signals.add("time=evening");
            return LocalTime.of(18, 30);
        }
        if (normalized.contains("bu\u1ed5i chi\u1ec1u") || normalized.contains("chi\u1ec1u")) {
            signals.add("time=afternoon");
            return LocalTime.of(14, 0);
        }
        if (normalized.contains("afternoon")) {
            signals.add("time=afternoon");
            return LocalTime.of(14, 0);
        }
        if (normalized.contains("bu\u1ed5i tr\u01b0a") || normalized.contains("tr\u01b0a")) {
            signals.add("time=noon");
            return LocalTime.of(11, 30);
        }
        if (normalized.contains("noon") || normalized.contains("midday")) {
            signals.add("time=noon");
            return LocalTime.of(11, 30);
        }
        if (normalized.contains("bu\u1ed5i s\u00e1ng") || normalized.contains("s\u00e1ng")) {
            signals.add("time=morning");
            return LocalTime.of(8, 30);
        }
        if (normalized.contains("morning")) {
            signals.add("time=morning");
            return LocalTime.of(8, 30);
        }
        return LocalTime.of(9, 0);
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean isOffsetExpression(String normalized, int tokenStartIndex) {
        int prefixStart = Math.max(0, tokenStartIndex - 10);
        String prefix = normalized.substring(prefixStart, tokenStartIndex);
        return prefix.contains("sau ")
                || prefix.contains("after ")
                || prefix.contains("in ");
    }

    public record TripScheduleHints(
            boolean shouldArrangeDateTime,
            int daySpan,
            int startOffsetDays,
            OffsetDateTime suggestedStartDateTime,
            List<String> signals) {

        static TripScheduleHints none() {
            return new TripScheduleHints(false, 0, 0, null, List.of());
        }

        String promptDirective() {
            if (!shouldArrangeDateTime || suggestedStartDateTime == null) {
                return null;
            }
            return "Scheduling intent detected from request. "
                    + "Preferred trip length: " + daySpan + " day(s). "
                    + "Preferred start offset: " + startOffsetDays + " day(s). "
                    + "Suggested first stop datetime (ISO-8601): " + suggestedStartDateTime + ". "
                    + "Return coherent plannedDateTime values for every stop in chronological order.";
        }
    }
}
