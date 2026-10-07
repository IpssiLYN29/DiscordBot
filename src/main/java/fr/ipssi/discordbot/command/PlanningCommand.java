package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.ics.IcsParser;
import fr.ipssi.discordbot.model.ScheduleEvent;
import fr.ipssi.discordbot.repository.ScheduleRepository;
import fr.ipssi.discordbot.service.PlanningRecapService;
import fr.ipssi.discordbot.service.ScheduleChangeService;
import fr.ipssi.discordbot.service.ScheduleDiff;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class PlanningCommand implements Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanningCommand.class);
    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;

    private final ScheduleRepository scheduleRepository;
    private final PlanningRecapService recapService;
    private final ScheduleChangeService changeService;

    public PlanningCommand(
            ScheduleRepository scheduleRepository,
            PlanningRecapService recapService,
            ScheduleChangeService changeService) {
        this.scheduleRepository = scheduleRepository;
        this.recapService = recapService;
        this.changeService = changeService;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("planning", "Manage the school schedule")
                .setGuildOnly(true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addSubcommands(new SubcommandData("import", "Import the school calendar (.ics file)")
                        .addOption(OptionType.ATTACHMENT, "file", "Calendar exported from NetYpareo", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Message.Attachment attachment = event.getOption("file", OptionMapping::getAsAttachment);
        if (!attachment.getFileName().toLowerCase().endsWith(".ics") || attachment.getSize() > MAX_FILE_SIZE) {
            event.reply("Fournis un fichier .ics de moins de 5 Mo.").setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        event.deferReply(true).queue();
        attachment.getProxy().download()
                .thenAccept(stream -> importCalendar(event, guildId, stream))
                .exceptionally(error -> {
                    LOGGER.error("Schedule import failed", error);
                    event.getHook().sendMessage("L'import a échoué, vérifie le fichier.").queue();
                    return null;
                });
    }

    private void importCalendar(SlashCommandInteractionEvent event, long guildId, InputStream stream) {
        String content;
        try (stream) {
            content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<ScheduleEvent> events = IcsParser.parse(content, guildId);
        if (events.isEmpty()) {
            event.getHook().sendMessage("Aucun cours trouvé dans ce fichier.").queue();
            return;
        }

        List<ScheduleEvent> before = scheduleRepository.findAll(guildId);
        scheduleRepository.replaceAll(guildId, events);

        ScheduleDiff diff = ScheduleDiff.compute(before, events, Instant.now());
        changeService.announce(event.getGuild(), diff);
        recapService.refresh(event.getGuild());

        Instant first = events.stream().map(ScheduleEvent::startsAt).min(Comparator.naturalOrder()).orElseThrow();
        Instant last = events.stream().map(ScheduleEvent::startsAt).max(Comparator.naturalOrder()).orElseThrow();
        event.getHook().sendMessage("%d cours importés, du <t:%d:D> au <t:%d:D>. %s".formatted(
                events.size(), first.getEpochSecond(), last.getEpochSecond(), describe(diff))).queue();
    }

    private static String describe(ScheduleDiff diff) {
        return diff.isEmpty()
                ? "Aucun changement à signaler."
                : "Changements signalés : %d annulé(s), %d modifié(s), %d ajouté(s)."
                        .formatted(diff.removed().size(), diff.changed().size(), diff.added().size());
    }
}
