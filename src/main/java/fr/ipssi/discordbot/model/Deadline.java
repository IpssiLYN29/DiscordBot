package fr.ipssi.discordbot.model;

import java.time.Instant;

public record Deadline(
        long id,
        long guildId,
        String subject,
        String description,
        Instant dueAt,
        long createdBy,
        int lastReminderDays
) {

    public static final int NO_REMINDER_SENT = Integer.MAX_VALUE;

    public static Deadline create(long guildId, String subject, String description, Instant dueAt, long createdBy) {
        return new Deadline(0, guildId, subject, description, dueAt, createdBy, NO_REMINDER_SENT);
    }
}
