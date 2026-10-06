package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.model.Warn;
import fr.ipssi.discordbot.repository.WarnRepository;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;
import java.util.stream.Collectors;

public final class WarnsCommand implements Command {

    private static final int MAX_DISPLAYED = 10;

    private final WarnRepository warnRepository;

    public WarnsCommand(WarnRepository warnRepository) {
        this.warnRepository = warnRepository;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("warns", "Show the warning history of a member")
                .setGuildOnly(true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "member", "Member to look up", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User target = event.getOption("member", OptionMapping::getAsUser);
        List<Warn> warns = warnRepository.findByMember(event.getGuild().getIdLong(), target.getIdLong());

        if (warns.isEmpty()) {
            event.reply(target.getAsMention() + " n'a aucun avertissement.").setEphemeral(true).queue();
            return;
        }

        String history = warns.stream()
                .limit(MAX_DISPLAYED)
                .map(warn -> "<t:%d:d> par <@%d> : %s".formatted(
                        warn.createdAt().getEpochSecond(), warn.moderatorId(), warn.reason()))
                .collect(Collectors.joining("\n"));

        event.reply(target.getAsMention() + " : " + warns.size() + " avertissement(s)\n" + history)
                .setEphemeral(true)
                .queue();
    }
}
