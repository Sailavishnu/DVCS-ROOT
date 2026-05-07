package com.dvcs.client.workspacepage.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class DateTimeUtils {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());

    private DateTimeUtils() {
    }

    public static String formatInstant(Instant instant) {
        if (instant == null) {
            return "No commits yet";
        }
        return DATE_TIME_FORMATTER.format(instant);
    }
}
