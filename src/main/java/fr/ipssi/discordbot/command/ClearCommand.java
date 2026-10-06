package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.service.LogService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class ClearCommand implements Command {

    private static final int MAX_MESSAGES = 100;

    private final LogService logService;

    public ClearCommand(LogService logService) {
        this.logService = logService;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("clear", "Delete the latest messages of this channel")
                .setGuildOnly(true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
                .addOptions(new OptionData(OptionType.INTEGER, "amount", "Number of messages to delete", true)
                        .setRequiredRange(1, MAX_MESSAGES));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int amount = event.getOption("amount", OptionMapping::getAsInt);
        MessageChannelUnion channel = event.getChannel();

        event.deferReply(true).queue();
        channel.getHistory().retrievePast(amount).queue(messages -> {
            channel.purgeMessages(messages);
            event.getHook().sendMessage(messages.size() + " message(s) supprimé(s).").queue();
            logService.log(event.getGuild(), "Nettoyage",
                    event.getUser().getAsMention() + " a supprimé " + messages.size()
                            + " message(s) dans " + channel.getAsMention() + ".");
        });
    }
}
