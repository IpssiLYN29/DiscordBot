package fr.ipssi.discordbot.model;

import java.time.Instant;
import java.util.List;

public record Resource(
        long id,
        long guildId,
        String url,
        String title,
        String description,
        List<String> tags,
        long createdBy,
        Instant createdAt
) {

    public static Resource create(
            long guildId, String url, String title, String description, List<String> tags, long createdBy) {
        return new Resource(0, guildId, url, title, description, tags, createdBy, Instant.now());
    }
}
