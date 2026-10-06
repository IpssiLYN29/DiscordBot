package fr.ipssi.discordbot.service;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;

import java.awt.Color;
import java.time.Instant;

public final class LogService {

    private static final Color LOG_COLOR = new Color(0x5865F2);

    private final SettingsService settings;

    public LogService(SettingsService settings) {
        this.settings = settings;
    }

    public void log(Guild guild, String title, String description) {
        settings.logChannelId(guild.getIdLong())
                .map(guild::getTextChannelById)
                .ifPresent(channel -> {
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle(title)
                            .setDescription(description)
                            .setColor(LOG_COLOR)
                            .setTimestamp(Instant.now());
                    channel.sendMessageEmbeds(embed.build()).queue();
                });
    }
}
