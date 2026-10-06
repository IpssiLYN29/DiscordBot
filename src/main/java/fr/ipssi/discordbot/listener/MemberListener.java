package fr.ipssi.discordbot.listener;

import fr.ipssi.discordbot.service.LogService;
import fr.ipssi.discordbot.service.SettingsService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public final class MemberListener extends ListenerAdapter {

    private final SettingsService settings;
    private final LogService logService;

    public MemberListener(SettingsService settings, LogService logService) {
        this.settings = settings;
        this.logService = logService;
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        Guild guild = event.getGuild();
        String mention = event.getUser().getAsMention();

        settings.welcomeChannelId(guild.getIdLong())
                .map(guild::getTextChannelById)
                .ifPresent(channel ->
                        channel.sendMessage("Bienvenue " + mention + " sur le serveur de l'IPSSI Lyon !").queue());
        logService.log(guild, "Arrivée", mention + " a rejoint le serveur.");
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        logService.log(event.getGuild(), "Départ", event.getUser().getName() + " a quitté le serveur.");
    }
}
