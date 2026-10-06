package fr.ipssi.discordbot.model;

import java.time.Instant;
import java.util.List;

public record Poll(
        long id,
        long guildId,
        long channelId,
        long messageId,
        String question,
        List<String> options,
        long createdBy,
        Instant closesAt,
        boolean closed
) {

    public static Poll create(
            long guildId, long channelId, String question, List<String> options, long createdBy, Instant closesAt) {
        return new Poll(0, guildId, channelId, 0, question, options, createdBy, closesAt, false);
    }

    public Poll withId(long newId) {
        return new Poll(newId, guildId, channelId, messageId, question, options, createdBy, closesAt, closed);
    }

    public Poll asClosed() {
        return new Poll(id, guildId, channelId, messageId, question, options, createdBy, closesAt, true);
    }
}
