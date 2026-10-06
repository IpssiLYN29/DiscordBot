package fr.ipssi.discordbot.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.format.TextStyle;
import java.util.Locale;

public final class TimeFormats {

    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter INPUT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT);

    private TimeFormats() {
    }

    public static Instant parseDateTime(String raw) {
        return LocalDateTime.parse(raw.trim(), INPUT).atZone(ZONE).toInstant();
    }

    public static String time(Instant instant) {
        return TIME.format(instant.atZone(ZONE));
    }

    public static String dayMonth(LocalDate date) {
        return date.getDayOfMonth() + " " + date.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH);
    }

    public static String dayLabel(LocalDate date) {
        String weekday = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.FRENCH);
        return Character.toUpperCase(weekday.charAt(0)) + weekday.substring(1) + " " + dayMonth(date);
    }

    public static String duration(Duration duration) {
        long hours = duration.toHours();
        int minutes = duration.toMinutesPart();
        if (hours == 0) {
            return minutes + " min";
        }
        return minutes == 0 ? hours + "h" : "%dh%02d".formatted(hours, minutes);
    }
}
