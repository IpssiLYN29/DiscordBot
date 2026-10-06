package fr.ipssi.discordbot.model;

import java.time.Instant;

public record ScheduleEvent(
        long guildId,
        String uid,
        String title,
        String teacher,
        String location,
        Instant startsAt,
        Instant endsAt
) {
}
