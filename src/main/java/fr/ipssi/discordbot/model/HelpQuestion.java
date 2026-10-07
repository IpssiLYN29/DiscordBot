package fr.ipssi.discordbot.model;

public record HelpQuestion(long threadId, long guildId, long askerId, boolean resolved) {
}
