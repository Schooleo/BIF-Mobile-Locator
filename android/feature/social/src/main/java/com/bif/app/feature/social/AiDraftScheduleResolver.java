package com.bif.app.feature.social;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class AiDraftScheduleResolver {

    private AiDraftScheduleResolver() {
    }

    static ScheduleCursor newCursor(long tripStartAt, long tripEndAt) {
        if (tripStartAt <= 0L) {
            return null;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate startDate = Instant.ofEpochMilli(tripStartAt).atZone(zone).toLocalDate();
        LocalDate endDate = tripEndAt >= tripStartAt
                ? Instant.ofEpochMilli(tripEndAt).atZone(zone).toLocalDate()
                : startDate;
        return new ScheduleCursor(zone, startDate, endDate);
    }

    static ScheduledTime resolveStopTimes(String plannedDateTime,
                                          String startTime,
                                          String endTime,
                                          int durationMinutes,
                                          ScheduleCursor cursor) {
        int safeDurationMinutes = Math.max(0, durationMinutes);
        long plannedAt = parseDateTimeMillis(plannedDateTime);
        if (plannedAt > 0L) {
            long departureAt = resolveDepartureFromPlanned(
                    plannedAt,
                    endTime,
                    safeDurationMinutes,
                    cursor != null ? cursor.zone : ZoneId.systemDefault()
            );
            if (cursor != null) {
                cursor.updateFrom(plannedAt, departureAt);
            }
            return new ScheduledTime(plannedAt, departureAt);
        }

        LocalTime parsedStart = parseLocalTime(startTime);
        LocalTime parsedEnd = parseLocalTime(endTime);
        if (parsedStart == null && parsedEnd != null) {
            parsedStart = parsedEnd.minusMinutes(safeDurationMinutes);
        }
        if (parsedStart != null && cursor == null) {
            ZoneId zone = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zone);
            ZonedDateTime arrival = ZonedDateTime.of(today, parsedStart, zone);
            ZonedDateTime departure = parsedEnd != null
                    ? ZonedDateTime.of(parsedEnd.isBefore(parsedStart) ? today.plusDays(1) : today, parsedEnd, zone)
                    : arrival.plusMinutes(safeDurationMinutes);
            if (departure.toInstant().isBefore(arrival.toInstant())) {
                departure = arrival.plusMinutes(safeDurationMinutes);
            }
            return new ScheduledTime(arrival.toInstant().toEpochMilli(), departure.toInstant().toEpochMilli());
        }
        if (parsedStart == null || cursor == null) {
            return new ScheduledTime(0L, 0L);
        }

        LocalDate scheduledDate = cursor.currentDate;
        ZonedDateTime candidateArrival = ZonedDateTime.of(scheduledDate, parsedStart, cursor.zone);
        if (cursor.previousInstant != null && candidateArrival.isBefore(cursor.previousInstant)) {
            LocalDate nextDate = scheduledDate.plusDays(1);
            if (nextDate.isAfter(cursor.endDate)
                    && cursor.previousInstant.toLocalDate().equals(cursor.endDate)) {
                ZonedDateTime arrival = cursor.previousInstant.plusMinutes(safeDurationMinutes);
                ZonedDateTime departure = arrival.plusMinutes(safeDurationMinutes);
                cursor.updateFrom(arrival.toInstant().toEpochMilli(), departure.toInstant().toEpochMilli());
                return new ScheduledTime(arrival.toInstant().toEpochMilli(), departure.toInstant().toEpochMilli());
            }
            scheduledDate = nextDate.isAfter(cursor.endDate) ? cursor.endDate : nextDate;
        }

        ZonedDateTime arrival = ZonedDateTime.of(scheduledDate, parsedStart, cursor.zone);
        ZonedDateTime departure;
        if (parsedEnd != null) {
            LocalDate departureDate = parsedEnd.isBefore(parsedStart)
                    ? scheduledDate.plusDays(1)
                    : scheduledDate;
            if (departureDate.isAfter(cursor.endDate)) {
                departureDate = cursor.endDate;
            }
            departure = ZonedDateTime.of(departureDate, parsedEnd, cursor.zone);
            if (departure.toInstant().toEpochMilli() < arrival.toInstant().toEpochMilli()) {
                departure = arrival.plusMinutes(safeDurationMinutes);
            }
        } else {
            departure = arrival.plusMinutes(safeDurationMinutes);
        }

        long arrivalAt = arrival.toInstant().toEpochMilli();
        long departureAt = departure.toInstant().toEpochMilli();
        cursor.updateFrom(arrivalAt, departureAt);
        return new ScheduledTime(arrivalAt, departureAt);
    }

    static long parseDateTimeMillis(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return 0L;
        }
        try {
            return Instant.parse(normalized).toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(normalized).toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return ZonedDateTime.parse(normalized).toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private static long resolveDepartureFromPlanned(long plannedAt,
                                                    String endTime,
                                                    int durationMinutes,
                                                    ZoneId zone) {
        LocalTime parsedEnd = parseLocalTime(endTime);
        if (parsedEnd == null) {
            return plannedAt + (durationMinutes * 60_000L);
        }

        ZonedDateTime arrival = Instant.ofEpochMilli(plannedAt).atZone(zone);
        LocalDate departureDate = parsedEnd.isBefore(arrival.toLocalTime())
                ? arrival.toLocalDate().plusDays(1)
                : arrival.toLocalDate();
        return ZonedDateTime.of(departureDate, parsedEnd, zone).toInstant().toEpochMilli();
    }

    private static LocalTime parseLocalTime(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalTime.parse(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static final class ScheduledTime {
        final long arrivalAt;
        final long departureAt;

        ScheduledTime(long arrivalAt, long departureAt) {
            this.arrivalAt = arrivalAt;
            this.departureAt = departureAt;
        }
    }

    static final class ScheduleCursor {
        private final ZoneId zone;
        private final LocalDate endDate;
        private LocalDate currentDate;
        private ZonedDateTime previousInstant;

        ScheduleCursor(ZoneId zone, LocalDate currentDate, LocalDate endDate) {
            this.zone = zone;
            this.currentDate = currentDate;
            this.endDate = endDate;
        }

        private void updateFrom(long arrivalAt, long departureAt) {
            ZonedDateTime departure = Instant.ofEpochMilli(Math.max(arrivalAt, departureAt)).atZone(zone);
            currentDate = departure.toLocalDate().isAfter(endDate) ? endDate : departure.toLocalDate();
            previousInstant = departure;
        }
    }
}
