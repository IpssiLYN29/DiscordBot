package fr.ipssi.discordbot.model;

import java.time.Instant;

public record Meetup(
        long id,
        long guildId,
        long channelId,
        long messageId,
        String title,
        String description,
        String location,
        Instant startsAt,
        long createdBy,
        MeetupStatus status
) {

    public static Meetup create(
            long guildId,
            long channelId,
            String title,
            String description,
            String location,
            Instant startsAt,
            long createdBy) {
        return new Meetup(0, guildId, channelId, 0, title, description, location, startsAt, createdBy, MeetupStatus.OPEN);
    }

    public Meetup withId(long newId) {
        return new Meetup(newId, guildId, channelId, messageId, title, description, location, startsAt, createdBy, status);
    }

    public Meetup withStatus(MeetupStatus newStatus) {
        return new Meetup(id, guildId, channelId, messageId, title, description, location, startsAt, createdBy, newStatus);
    }
}
