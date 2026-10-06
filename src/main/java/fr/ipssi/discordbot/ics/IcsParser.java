package fr.ipssi.discordbot.ics;

import fr.ipssi.discordbot.model.ScheduleEvent;
import fr.ipssi.discordbot.util.TimeFormats;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class IcsParser {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final String TEACHER_SEPARATOR = " - ";

    private record Property(String parameters, String value) {
    }

    private IcsParser() {
    }

    public static List<ScheduleEvent> parse(String content, long guildId) {
        List<ScheduleEvent> events = new ArrayList<>();
        Map<String, Property> current = null;

        for (String line : unfold(content)) {
            if (line.equals("BEGIN:VEVENT")) {
                current = new HashMap<>();
            } else if (line.equals("END:VEVENT")) {
                if (current != null) {
                    toEvent(current, guildId).ifPresent(events::add);
                }
                current = null;
            } else if (current != null) {
                parseLine(line, current);
            }
        }
        return events;
    }

    private static Map<String, Property> parseLine(String line, Map<String, Property> current) {
        int separator = line.indexOf(':');
        if (separator > 0) {
            String[] nameAndParameters = line.substring(0, separator).split(";", 2);
            String parameters = nameAndParameters.length > 1 ? nameAndParameters[1] : "";
            current.put(nameAndParameters[0].toUpperCase(),
                    new Property(parameters, line.substring(separator + 1)));
        }
        return current;
    }

    private static List<String> unfold(String content) {
        List<String> lines = new ArrayList<>();
        for (String raw : content.split("\\r?\\n")) {
            boolean continuation = !raw.isEmpty() && (raw.charAt(0) == ' ' || raw.charAt(0) == '\t');
            if (continuation && !lines.isEmpty()) {
                int last = lines.size() - 1;
                lines.set(last, lines.get(last) + raw.substring(1));
            } else {
                lines.add(raw);
            }
        }
        return lines;
    }

    private static Optional<ScheduleEvent> toEvent(Map<String, Property> properties, long guildId) {
        Property uid = properties.get("UID");
        Property summary = properties.get("SUMMARY");
        Optional<Instant> start = Optional.ofNullable(properties.get("DTSTART")).flatMap(IcsParser::toInstant);
        if (uid == null || summary == null || start.isEmpty()) {
            return Optional.empty();
        }

        Instant end = Optional.ofNullable(properties.get("DTEND")).flatMap(IcsParser::toInstant).orElse(start.get());
        String text = unescape(summary.value());
        int separator = text.lastIndexOf(TEACHER_SEPARATOR);
        String title = separator > 0 ? text.substring(0, separator).trim() : text;
        String teacher = separator > 0 ? text.substring(separator + TEACHER_SEPARATOR.length()).trim() : "";
        String location = Optional.ofNullable(properties.get("LOCATION")).map(p -> unescape(p.value())).orElse("");

        return Optional.of(new ScheduleEvent(guildId, uid.value(), title, teacher, location, start.get(), end));
    }

    private static Optional<Instant> toInstant(Property property) {
        String value = property.value();
        if (!value.contains("T")) {
            return Optional.empty();
        }
        try {
            if (value.endsWith("Z")) {
                LocalDateTime utc = LocalDateTime.parse(value.substring(0, value.length() - 1), DATE_TIME);
                return Optional.of(utc.toInstant(ZoneOffset.UTC));
            }
            return Optional.of(LocalDateTime.parse(value, DATE_TIME).atZone(zoneOf(property)).toInstant());
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    private static ZoneId zoneOf(Property property) {
        for (String parameter : property.parameters().split(";")) {
            if (parameter.startsWith("TZID=")) {
                return ZoneId.of(parameter.substring("TZID=".length()));
            }
        }
        return TimeFormats.ZONE;
    }

    private static String unescape(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                result.append(next == 'n' || next == 'N' ? '\n' : next);
            } else {
                result.append(current);
            }
        }
        return result.toString().trim();
    }
}
