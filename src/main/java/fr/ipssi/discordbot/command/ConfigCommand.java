package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.model.SettingKey;
import fr.ipssi.discordbot.service.SettingsService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.Optional;
import java.util.stream.Collectors;

public final class ConfigCommand implements Command {

    private static final String NOT_SET = "non défini";

    private final SettingsService settings;

    public ConfigCommand(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("config", "Configure the bot for this server")
                .setGuildOnly(true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addSubcommands(
                        new SubcommandData("welcome-channel", "Set the welcome channel")
                                .addOptions(channelOption()),
                        new SubcommandData("log-channel", "Set the logs channel")
                                .addOptions(channelOption()),
                        new SubcommandData("deadline-channel", "Set the channel for deadline reminders")
                                .addOptions(channelOption()),
                        new SubcommandData("planning-channel", "Set the channel for class reminders")
                                .addOptions(channelOption()),
                        new SubcommandData("planning-role", "Set the optional role pinged before classes")
                                .addOption(OptionType.ROLE, "role", "Role to ping", true),
                        new SubcommandData("member-role", "Set the role given when the rules are accepted")
                                .addOption(OptionType.ROLE, "role", "Role to give", true),
                        new SubcommandData("add-selectable-role", "Add a role to the selection menu")
                                .addOption(OptionType.ROLE, "role", "Role members can pick", true),
                        new SubcommandData("remove-selectable-role", "Remove a role from the selection menu")
                                .addOption(OptionType.ROLE, "role", "Role to remove", true),
                        new SubcommandData("show", "Show the current configuration"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        String subcommand = event.getSubcommandName();

        switch (subcommand) {
            case "welcome-channel" -> saveChannel(event, guildId, SettingKey.WELCOME_CHANNEL);
            case "log-channel" -> saveChannel(event, guildId, SettingKey.LOG_CHANNEL);
            case "deadline-channel" -> saveChannel(event, guildId, SettingKey.DEADLINE_CHANNEL);
            case "planning-channel" -> saveChannel(event, guildId, SettingKey.PLANNING_CHANNEL);
            case "planning-role" -> {
                settings.setId(guildId, SettingKey.PLANNING_ROLE, event.getOption("role").getAsRole().getIdLong());
                reply(event, "Rôle du planning mis à jour. Il apparaît dans le menu de /roles-panel.");
            }
            case "member-role" -> {
                settings.setId(guildId, SettingKey.MEMBER_ROLE, event.getOption("role").getAsRole().getIdLong());
                reply(event, "Rôle membre mis à jour.");
            }
            case "add-selectable-role" -> {
                settings.addSelectableRole(guildId, event.getOption("role").getAsRole().getIdLong());
                reply(event, "Rôle ajouté au menu de sélection.");
            }
            case "remove-selectable-role" -> {
                settings.removeSelectableRole(guildId, event.getOption("role").getAsRole().getIdLong());
                reply(event, "Rôle retiré du menu de sélection.");
            }
            case "show" -> reply(event, describe(guildId));
            default -> reply(event, "Sous-commande inconnue.");
        }
    }

    private void saveChannel(SlashCommandInteractionEvent event, long guildId, SettingKey key) {
        settings.setId(guildId, key, event.getOption("channel").getAsChannel().getIdLong());
        reply(event, "Salon mis à jour.");
    }

    private String describe(long guildId) {
        String selectableRoles = settings.selectableRoleIds(guildId).stream()
                .map(id -> "<@&" + id + ">")
                .collect(Collectors.joining(", "));

        return """
                Salon de bienvenue : %s
                Salon de logs : %s
                Salon des deadlines : %s
                Salon du planning : %s
                Rôle du planning : %s
                Rôle membre : %s
                Rôles sélectionnables : %s"""
                .formatted(
                        mention(settings.welcomeChannelId(guildId), "#"),
                        mention(settings.logChannelId(guildId), "#"),
                        mention(settings.deadlineChannelId(guildId), "#"),
                        mention(settings.planningChannelId(guildId), "#"),
                        mention(settings.planningRoleId(guildId), "@&"),
                        mention(settings.memberRoleId(guildId), "@&"),
                        selectableRoles.isEmpty() ? NOT_SET : selectableRoles);
    }

    private static String mention(Optional<Long> id, String prefix) {
        return id.map(value -> "<" + prefix + value + ">").orElse(NOT_SET);
    }

    private static OptionData channelOption() {
        return new OptionData(OptionType.CHANNEL, "channel", "Text channel", true)
                .setChannelTypes(ChannelType.TEXT);
    }

    private static void reply(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }
}
