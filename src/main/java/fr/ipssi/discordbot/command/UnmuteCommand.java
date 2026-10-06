package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.service.LogService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class UnmuteCommand implements Command {

    private final LogService logService;

    public UnmuteCommand(LogService logService) {
        this.logService = logService;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("unmute", "Remove the mute of a member")
                .setGuildOnly(true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "member", "Member to unmute", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member target = event.getOption("member", OptionMapping::getAsMember);
        if (!ModerationChecks.canModerate(event, target)) {
            return;
        }

        target.removeTimeout().queue(
                success -> {
                    logService.log(event.getGuild(), "Unmute",
                            target.getAsMention() + " a été unmute par " + event.getUser().getAsMention() + ".");
                    event.reply(target.getAsMention() + " n'est plus mute.").queue();
                },
                error -> event.reply("Impossible de unmute ce membre.").setEphemeral(true).queue()
        );
    }
}
