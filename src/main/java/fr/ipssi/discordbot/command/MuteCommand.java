package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.service.LogService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.time.Duration;

public final class MuteCommand implements Command {

    private static final int MAX_MINUTES = 40_320;

    private final LogService logService;

    public MuteCommand(LogService logService) {
        this.logService = logService;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("mute", "Temporarily mute a member")
                .setGuildOnly(true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "member", "Member to mute", true)
                .addOptions(new OptionData(OptionType.INTEGER, "minutes", "Duration in minutes", true)
                        .setRequiredRange(1, MAX_MINUTES))
                .addOption(OptionType.STRING, "reason", "Reason of the mute", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member target = event.getOption("member", OptionMapping::getAsMember);
        if (!ModerationChecks.canModerate(event, target)) {
            return;
        }

        int minutes = event.getOption("minutes", OptionMapping::getAsInt);
        String reason = event.getOption("reason", OptionMapping::getAsString);

        target.timeoutFor(Duration.ofMinutes(minutes)).reason(reason).queue(
                success -> {
                    logService.log(event.getGuild(), "Mute",
                            target.getAsMention() + " a été mute " + minutes + " min par "
                                    + event.getUser().getAsMention() + ".\nRaison : " + reason);
                    event.reply(target.getAsMention() + " a été mute pendant " + minutes + " minute(s).").queue();
                },
                error -> event.reply("Impossible de mute ce membre.").setEphemeral(true).queue()
        );
    }
}
