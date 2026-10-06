package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.model.Warn;
import fr.ipssi.discordbot.repository.WarnRepository;
import fr.ipssi.discordbot.service.LogService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.time.Instant;

public final class WarnCommand implements Command {

    private final WarnRepository warnRepository;
    private final LogService logService;

    public WarnCommand(WarnRepository warnRepository, LogService logService) {
        this.warnRepository = warnRepository;
        this.logService = logService;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("warn", "Warn a member")
                .setGuildOnly(true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "member", "Member to warn", true)
                .addOption(OptionType.STRING, "reason", "Reason of the warning", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member target = event.getOption("member", OptionMapping::getAsMember);
        if (!ModerationChecks.canModerate(event, target)) {
            return;
        }

        String reason = event.getOption("reason", OptionMapping::getAsString);
        warnRepository.add(new Warn(
                event.getGuild().getIdLong(),
                target.getIdLong(),
                event.getUser().getIdLong(),
                reason,
                Instant.now()));

        target.getUser().openPrivateChannel()
                .flatMap(channel -> channel.sendMessage("Tu as reçu un avertissement sur le serveur IPSSI Lyon : " + reason))
                .queue(null, error -> { });

        logService.log(event.getGuild(), "Avertissement",
                target.getAsMention() + " a été averti par " + event.getUser().getAsMention() + ".\nRaison : " + reason);
        event.reply(target.getAsMention() + " a été averti.").queue();
    }
}
