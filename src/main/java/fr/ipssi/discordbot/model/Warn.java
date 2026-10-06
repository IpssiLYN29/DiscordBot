package fr.ipssi.discordbot.model;

import java.time.Instant;

public record Warn(long guildId, long userId, long moderatorId, String reason, Instant createdAt) {
}
