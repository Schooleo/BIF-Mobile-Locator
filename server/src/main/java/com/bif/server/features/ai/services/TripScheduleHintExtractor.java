package com.bif.server.features.ai.services;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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
    private static final Pattern START_DATE_LABEL_PATTERN = Pattern.compile(
            "(?:start\\s*date|ng\\u00e0y\\s*b\\u1eaft\\s*\\u0111\\u1ea7u)\\s*[:\\-]?\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern END_DATE_LABEL_PATTERN = Pattern.compile(
            "(?:end\\s*date|ng\\u00e0y\\s*k\\u1ebft\\s*th\\u00fac)\\s*[:\\-]?\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern INLINE_DATE_RANGE_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2})\\s*(?:to|\\-|\\u0111\\u1ebfn)\\s*(\\d{4}-\\d{2}-\\d{2})");

    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile("(?:từ|tu|from)\\s+([0-9]{1,2})(?:[:h]([0-9]{2}))?\\s*(sáng|sang|chiều|chieu|tối|toi|đêm|dem|am|pm)?\\s+(?:đến|den|to|-)\\s+([0-9]{1,2})(?:[:h]([0-9]{2}))?\\s*(sáng|sang|chiều|chieu|tối|toi|đêm|dem|am|pm)?");
    private static final Pattern SINGLE_TIME_PATTERN = Pattern.compile("\\b([0-9]{1,2})(?:[:h]([0-9]{2}))?\\s*(sáng|sang|chiều|chieu|tối|toi|đêm|dem|am|pm)\\b");

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

        DateRange explicitDateRange = extractExplicitDateRange(normalized, signals);
        int daySpan = extractDaySpan(normalized, signals);
        int startOffsetDays = extractStartOffsetDays(normalized, signals);
        TimeWindow explicitTimeWindow = extractExplicitTimeWindow(normalized, signals);
        LocalTime preferredStartTime = explicitTimeWindow.startTime() != null
                ? explicitTimeWindow.startTime()
                : resolvePreferredStartTime(normalized, signals);
        LocalTime preferredEndTime = explicitTimeWindow.endTime() != null
                ? explicitTimeWindow.endTime()
                : resolvePreferredEndTime(normalized, preferredStartTime, signals);

        boolean hasTimingIntent = !signals.isEmpty();
        if (!hasTimingIntent) {
            return TripScheduleHints.none();
        }

        LocalDate today = LocalDate.now(clock);
        int normalizedDaySpan = Math.max(1, daySpan);
        LocalDate startDate = today.plusDays(Math.max(startOffsetDays, 0));
        int normalizedStartOffsetDays = Math.max(startOffsetDays, 0);
        if (explicitDateRange != null) {
            startDate = explicitDateRange.startDate();
            normalizedDaySpan = Math.max(1, explicitDateRange.daySpan());
            normalizedStartOffsetDays = Math.max(0, (int) ChronoUnit.DAYS.between(today, startDate));
        }
        OffsetDateTime suggestedStart = ZonedDateTime.of(startDate, preferredStartTime, DEFAULT_ZONE)
                .toOffsetDateTime()
                .withSecond(0)
                .withNano(0);

        return new TripScheduleHints(
                true,
                normalizedDaySpan,
                normalizedStartOffsetDays,
                suggestedStart,
                preferredStartTime,
                preferredEndTime,
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


    private TimeWindow extractExplicitTimeWindow(String normalized, List<String> signals) {
        Matcher rangeMatcher = TIME_RANGE_PATTERN.matcher(normalized);
        if (rangeMatcher.find()) {
            LocalTime start = parseClockTime(rangeMatcher.group(1), rangeMatcher.group(2), rangeMatcher.group(3));
            LocalTime end = parseClockTime(rangeMatcher.group(4), rangeMatcher.group(5), rangeMatcher.group(6));
            if (start != null || end != null) {
                signals.add("timeWindow=" + formatTime(start) + "-" + formatTime(end));
                return new TimeWindow(start, end);
            }
        }

        Matcher singleMatcher = SINGLE_TIME_PATTERN.matcher(normalized);
        if (singleMatcher.find()) {
            LocalTime start = parseClockTime(singleMatcher.group(1), singleMatcher.group(2), singleMatcher.group(3));
            if (start != null) {
                signals.add("time=" + formatTime(start));
                return new TimeWindow(start, null);
            }
        }
        return TimeWindow.none();
    }

    private LocalTime resolvePreferredEndTime(String normalized, LocalTime preferredStartTime, List<String> signals) {
        if (normalized.contains("buổi tối") || normalized.contains("tối")
                || normalized.contains("evening") || normalized.contains("night")) {
            return LocalTime.of(22, 0);
        }
        if (normalized.contains("buổi chiều") || normalized.contains("chiều") || normalized.contains("afternoon")) {
            return LocalTime.of(17, 0);
        }
        if (normalized.contains("buổi trưa") || normalized.contains("trưa")
                || normalized.contains("noon") || normalized.contains("midday")) {
            return LocalTime.of(14, 0);
        }
        if (normalized.contains("buổi sáng") || normalized.contains("sáng") || normalized.contains("morning")) {
            return LocalTime.of(11, 30);
        }
        return preferredStartTime == null ? null : preferredStartTime.plusHours(8);
    }

    private LocalTime parseClockTime(String hourValue, String minuteValue, String meridiemValue) {
        int hour = parseInt(hourValue);
        int minute = minuteValue == null ? 0 : parseInt(minuteValue);
        if (hour < 0 || hour > 24 || minute < 0 || minute > 59) {
            return null;
        }
        String meridiem = meridiemValue == null ? "" : meridiemValue;
        if ((meridiem.contains("chiều") || meridiem.contains("chieu")
                || meridiem.contains("tối") || meridiem.contains("toi")
                || meridiem.contains("đêm") || meridiem.contains("dem")
                || meridiem.equals("pm")) && hour < 12) {
            hour += 12;
        }
        if ((meridiem.contains("sáng") || meridiem.contains("sang") || meridiem.equals("am"))
                && hour == 12) {
            hour = 0;
        }
        if (hour == 24) {
            hour = 0;
        }
        return LocalTime.of(hour, minute);
    }

    private String formatTime(LocalTime time) {
        return time == null ? "?" : time.toString();
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

    private DateRange extractExplicitDateRange(String normalized, List<String> signals) {
        LocalDate startDate = extractDateForPattern(normalized, START_DATE_LABEL_PATTERN, 1);
        LocalDate endDate = extractDateForPattern(normalized, END_DATE_LABEL_PATTERN, 1);
        if (startDate == null || endDate == null) {
            Matcher inlineRangeMatcher = INLINE_DATE_RANGE_PATTERN.matcher(normalized);
            if (inlineRangeMatcher.find()) {
                LocalDate inlineStart = parseIsoDate(inlineRangeMatcher.group(1));
                LocalDate inlineEnd = parseIsoDate(inlineRangeMatcher.group(2));
                if (startDate == null) {
                    startDate = inlineStart;
                }
                if (endDate == null) {
                    endDate = inlineEnd;
                }
            }
        }

        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return null;
        }

        int daySpan = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        signals.add("dateRange=" + startDate + ".." + endDate);
        signals.add("duration=" + daySpan + "d");
        return new DateRange(startDate, endDate, daySpan);
    }

    private LocalDate extractDateForPattern(String normalized, Pattern pattern, int dateGroupIndex) {
        Matcher matcher = pattern.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        return parseIsoDate(matcher.group(dateGroupIndex));
    }

    private LocalDate parseIsoDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private record TimeWindow(LocalTime startTime, LocalTime endTime) {
        static TimeWindow none() {
            return new TimeWindow(null, null);
        }
    }

    private record DateRange(LocalDate startDate, LocalDate endDate, int daySpan) {
    }

    public record TripScheduleHints(
            boolean shouldArrangeDateTime,
            int daySpan,
            int startOffsetDays,
            OffsetDateTime suggestedStartDateTime,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            List<String> signals) {

        static TripScheduleHints none() {
            return new TripScheduleHints(false, 0, 0, null, null, null, List.of());
        }

        String promptDirective() {
            if (!shouldArrangeDateTime || suggestedStartDateTime == null) {
                return null;
            }
            return "Scheduling intent detected from request. "
                    + "Preferred trip length: " + daySpan + " day(s). "
                    + "Preferred start offset: " + startOffsetDays + " day(s). "
                    + "Suggested first stop datetime (ISO-8601): " + suggestedStartDateTime + ". "
                    + "Preferred daily time window: " + preferredStartTime + "-" + preferredEndTime + ". "
                    + "Return coherent plannedDateTime, startTime, endTime, and duration values for every stop in chronological order.";
        }
    }
}
