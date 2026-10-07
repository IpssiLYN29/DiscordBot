package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.model.Promo;
import fr.ipssi.discordbot.model.SettingKey;
import fr.ipssi.discordbot.model.Subject;
import fr.ipssi.discordbot.repository.PromoRepository;
import fr.ipssi.discordbot.repository.SubjectRepository;
import fr.ipssi.discordbot.service.SettingsService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ConfigCommand implements Command {

    private static final String NOT_SET = "non défini";

    private final SettingsService settings;
    private final PromoRepository promoRepository;
    private final SubjectRepository subjectRepository;

    public ConfigCommand(
            SettingsService settings, PromoRepository promoRepository, SubjectRepository subjectRepository) {
        this.settings = settings;
        this.promoRepository = promoRepository;
        this.subjectRepository = subjectRepository;
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
                        new SubcommandData("help-channel", "Set the channel where /question creates threads")
                                .addOptions(channelOption()),
                        new SubcommandData("planning-channel", "Set the planning channel when there are no promos")
                                .addOptions(channelOption()),
                        new SubcommandData("planning-role", "Set the role pinged before classes when there are no promos")
                                .addOption(OptionType.ROLE, "role", "Role to ping", true),
                        new SubcommandData("promo-set", "Add or update a promo with its own planning channel and role")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "code", "Promo code as in the calendar, e.g. LYN", true)
                                                .setMaxLength(10),
                                        channelOption(),
                                        new OptionData(OptionType.ROLE, "role", "Opt-in role pinged before its classes", true)),
                        new SubcommandData("promo-remove", "Remove a promo")
                                .addOptions(new OptionData(OptionType.STRING, "code", "Promo code", true)
                                        .setMaxLength(10)),
                        new SubcommandData("subject-add", "Add or update a subject for /question")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "name", "Subject name", true)
                                                .setMaxLength(40),
                                        new OptionData(OptionType.ROLE, "role", "Role pinged for this subject", true)),
                        new SubcommandData("subject-remove", "Remove a subject")
                                .addOptions(new OptionData(OptionType.STRING, "name", "Subject name", true)
                                        .setMaxLength(40)),
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

        switch (event.getSubcommandName()) {
            case "welcome-channel" -> saveChannel(event, guildId, SettingKey.WELCOME_CHANNEL);
            case "log-channel" -> saveChannel(event, guildId, SettingKey.LOG_CHANNEL);
            case "deadline-channel" -> saveChannel(event, guildId, SettingKey.DEADLINE_CHANNEL);
            case "help-channel" -> saveChannel(event, guildId, SettingKey.HELP_CHANNEL);
            case "planning-channel" -> saveChannel(event, guildId, SettingKey.PLANNING_CHANNEL);
            case "planning-role" -> {
                settings.setId(guildId, SettingKey.PLANNING_ROLE, event.getOption("role").getAsRole().getIdLong());
                reply(event, "Rôle du planning mis à jour. Il apparaît dans le menu de /roles-panel.");
            }
            case "promo-set" -> setPromo(event, guildId);
            case "promo-remove" -> removePromo(event, guildId);
            case "subject-add" -> addSubject(event, guildId);
            case "subject-remove" -> removeSubject(event, guildId);
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

    private void setPromo(SlashCommandInteractionEvent event, long guildId) {
        String code = normalize(event.getOption("code", OptionMapping::getAsString));
        promoRepository.save(new Promo(
                guildId,
                code,
                event.getOption("channel").getAsChannel().getIdLong(),
                event.getOption("role").getAsRole().getIdLong()));
        reply(event, "Promo " + code + " enregistrée. Relance le bot ou fais /planning import pour recréer les récaps.");
    }

    private void removePromo(SlashCommandInteractionEvent event, long guildId) {
        String code = normalize(event.getOption("code", OptionMapping::getAsString));
        reply(event, promoRepository.remove(guildId, code) ? "Promo " + code + " supprimée." : "Promo inconnue.");
    }

    private void addSubject(SlashCommandInteractionEvent event, long guildId) {
        String name = event.getOption("name", OptionMapping::getAsString).trim();
        subjectRepository.save(new Subject(guildId, name, event.getOption("role").getAsRole().getIdLong()));
        reply(event, "Matière « " + name + " » enregistrée. Son rôle apparaît dans le menu de /roles-panel.");
    }

    private void removeSubject(SlashCommandInteractionEvent event, long guildId) {
        String name = event.getOption("name", OptionMapping::getAsString).trim();
        reply(event, subjectRepository.remove(guildId, name) ? "Matière supprimée." : "Matière inconnue.");
    }

    private String describe(long guildId) {
        String selectableRoles = settings.selectableRoleIds(guildId).stream()
                .map(id -> "<@&" + id + ">")
                .collect(Collectors.joining(", "));
        List<Promo> promos = promoRepository.findAll(guildId);
        String promoLines = promos.isEmpty()
                ? NOT_SET
                : promos.stream()
                        .map(promo -> "\n- **%s** : <#%d>, <@&%d>".formatted(promo.code(), promo.channelId(), promo.roleId()))
                        .collect(Collectors.joining());
        String subjects = subjectRepository.findAll(guildId).stream()
                .map(subject -> subject.name() + " (<@&" + subject.roleId() + ">)")
                .collect(Collectors.joining(", "));

        return """
                Salon de bienvenue : %s
                Salon de logs : %s
                Salon des deadlines : %s
                Salon d'aide : %s
                Promos : %s
                Salon du planning (sans promos) : %s
                Rôle du planning (sans promos) : %s
                Matières : %s
                Rôle membre : %s
                Rôles sélectionnables : %s"""
                .formatted(
                        mention(settings.welcomeChannelId(guildId), "#"),
                        mention(settings.logChannelId(guildId), "#"),
                        mention(settings.deadlineChannelId(guildId), "#"),
                        mention(settings.helpChannelId(guildId), "#"),
                        promoLines,
                        mention(settings.planningChannelId(guildId), "#"),
                        mention(settings.planningRoleId(guildId), "@&"),
                        subjects.isEmpty() ? NOT_SET : subjects,
                        mention(settings.memberRoleId(guildId), "@&"),
                        selectableRoles.isEmpty() ? NOT_SET : selectableRoles);
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
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
