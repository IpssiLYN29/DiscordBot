package fr.ipssi.discordbot.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.awt.Color;

public final class AnnounceCommand implements Command {

    private static final Color ANNOUNCE_COLOR = new Color(0xF0B232);

    @Override
    public SlashCommandData definition() {
        return Commands.slash("announce", "Publish an announcement")
                .setGuildOnly(true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
                .addOption(OptionType.STRING, "title", "Title of the announcement", true)
                .addOption(OptionType.STRING, "message", "Content of the announcement", true)
                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "Target channel (default: current one)")
                        .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS))
                .addOption(OptionType.ROLE, "role", "Role to mention");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String title = event.getOption("title", OptionMapping::getAsString);
        String message = event.getOption("message", OptionMapping::getAsString);
        Role role = event.getOption("role", OptionMapping::getAsRole);
        GuildMessageChannel channel = event.getOption("channel",
                event.getChannel().asGuildMessageChannel(),
                mapping -> mapping.getAsChannel().asGuildMessageChannel());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(message)
                .setColor(ANNOUNCE_COLOR)
                .setFooter("Annonce de " + event.getUser().getName());

        MessageCreateBuilder announcement = new MessageCreateBuilder().setEmbeds(embed.build());
        if (role != null) {
            announcement.setContent(role.getAsMention());
        }

        channel.sendMessage(announcement.build()).queue(
                success -> event.reply("Annonce publiée dans " + channel.getAsMention() + ".").setEphemeral(true).queue(),
                error -> event.reply("Impossible de publier dans ce salon.").setEphemeral(true).queue()
        );
    }
}
