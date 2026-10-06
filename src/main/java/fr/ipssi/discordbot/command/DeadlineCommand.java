package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.model.Deadline;
import fr.ipssi.discordbot.repository.DeadlineRepository;
import fr.ipssi.discordbot.util.TimeFormats;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.time.Instant;
import java.time.format.DateTimeParseException;

public final class DeadlineCommand implements Command {

    private final DeadlineRepository deadlineRepository;

    public DeadlineCommand(DeadlineRepository deadlineRepository) {
        this.deadlineRepository = deadlineRepository;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("deadline", "Manage deadlines")
                .setGuildOnly(true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
                .addSubcommands(
                        new SubcommandData("add", "Add a deadline")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "subject", "Subject or project", true)
                                                .setMaxLength(60),
                                        new OptionData(OptionType.STRING, "date", "JJ/MM/AAAA HH:mm (heure de Paris)", true),
                                        new OptionData(OptionType.STRING, "description", "Details")
                                                .setMaxLength(100)),
                        new SubcommandData("remove", "Remove a deadline")
                                .addOptions(new OptionData(OptionType.INTEGER, "id", "Deadline number", true)
                                        .setMinValue(1)));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        switch (event.getSubcommandName()) {
            case "add" -> add(event);
            case "remove" -> remove(event);
            default -> event.reply("Sous-commande inconnue.").setEphemeral(true).queue();
        }
    }

    private void add(SlashCommandInteractionEvent event) {
        Instant dueAt;
        try {
            String rawDate = event.getOption("date", OptionMapping::getAsString);
            dueAt = TimeFormats.parseDateTime(rawDate);
        } catch (DateTimeParseException e) {
            event.reply("Date invalide. Format attendu : JJ/MM/AAAA HH:mm (ex. 25/12/2026 18:00).")
                    .setEphemeral(true).queue();
            return;
        }

        if (!dueAt.isAfter(Instant.now())) {
            event.reply("La date doit être dans le futur.").setEphemeral(true).queue();
            return;
        }

        Deadline deadline = Deadline.create(
                event.getGuild().getIdLong(),
                event.getOption("subject", OptionMapping::getAsString),
                event.getOption("description", "", OptionMapping::getAsString),
                dueAt,
                event.getUser().getIdLong());
        long id = deadlineRepository.add(deadline);

        event.reply("Deadline #%d ajoutée : **%s**, <t:%d:F>.".formatted(id, deadline.subject(), dueAt.getEpochSecond()))
                .queue();
    }

    private void remove(SlashCommandInteractionEvent event) {
        long id = event.getOption("id", OptionMapping::getAsLong);
        boolean removed = deadlineRepository.remove(event.getGuild().getIdLong(), id);

        event.reply(removed ? "Deadline #" + id + " supprimée." : "Aucune deadline avec le numéro " + id + ".")
                .setEphemeral(!removed)
                .queue();
    }
}
