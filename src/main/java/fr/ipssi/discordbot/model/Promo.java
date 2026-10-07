package fr.ipssi.discordbot.model;

public record Promo(long guildId, String code, long channelId, long roleId) {
}
