package fr.ipssi.discordbot.service;

import fr.ipssi.discordbot.model.ScheduleEvent;
import fr.ipssi.discordbot.repository.ScheduleRepository;
import fr.ipssi.discordbot.util.TimeFormats;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public final class ScheduleReminderService implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduleReminderService.class);
    private static final Duration REMINDER_LEAD_TIME = Duration.ofMinutes(30);
    private static final Color REMINDER_COLOR = new Color(0x5865F2);

    private final JDA jda;
    private final ScheduleRepository scheduleRepository;
    private final SettingsService settings;

    public ScheduleReminderService(JDA jda, ScheduleRepository scheduleRepository, SettingsService settings) {
        this.jda = jda;
        this.scheduleRepository = scheduleRepository;
        this.settings = settings;
    }

    @Override
    public void run() {
        Instant now = Instant.now();
        scheduleRepository.findPendingReminders(now, now.plus(REMINDER_LEAD_TIME)).forEach(event ->
                post(event, event.startsAt(), () -> scheduleRepository.markReminded(event.guildId(), event.uid())));
    }

    public void announceNext(Guild guild) {
        Instant now = Instant.now();
        scheduleRepository.findNext(guild.getIdLong(), now).ifPresent(event ->
                post(event, now.plus(REMINDER_LEAD_TIME), () -> { }));
    }

    private void post(ScheduleEvent event, Instant displayedStart, Runnable onSent) {
        Guild guild = jda.getGuildById(event.guildId());
        if (guild == null) {
            return;
        }
        TextChannel channel = settings.planningChannelId(guild.getIdLong())
                .map(guild::getTextChannelById)
                .orElse(null);
        if (channel == null) {
            return;
        }

        String mention = settings.planningRoleId(guild.getIdLong())
                .map(roleId -> "<@&" + roleId + ">")
                .orElse("");
        MessageCreateBuilder message = new MessageCreateBuilder()
                .setContent(mention)
                .setEmbeds(buildEmbed(event, displayedStart).build());

        channel.sendMessage(message.build()).queue(
                success -> onSent.run(),
                error -> LOGGER.warn("Failed to send reminder for event {}", event.uid(), error));
    }

    private EmbedBuilder buildEmbed(ScheduleEvent event, Instant displayedStart) {
        String timeRange = "%s – %s (%s)".formatted(
                TimeFormats.time(event.startsAt()),
                TimeFormats.time(event.endsAt()),
                TimeFormats.duration(Duration.between(event.startsAt(), event.endsAt())));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(event.title())
                .setDescription("Commence <t:%d:R>\n%s".formatted(displayedStart.getEpochSecond(), timeRange))
                .setColor(REMINDER_COLOR);

        if (!event.teacher().isBlank()) {
            embed.addField("Intervenant", event.teacher(), true);
        }
        if (!event.location().isBlank()) {
            embed.addField("Salle", event.location(), true);
        }

        String laterToday = laterToday(event);
        if (!laterToday.isEmpty()) {
            embed.addField("Ensuite aujourd'hui", laterToday, false);
        }
        return embed;
    }

    private String laterToday(ScheduleEvent event) {
        Instant endOfDay = event.startsAt().atZone(TimeFormats.ZONE)
                .toLocalDate().plusDays(1).atStartOfDay(TimeFormats.ZONE).toInstant();
        List<ScheduleEvent> later = scheduleRepository.findStartingBetween(event.guildId(), event.startsAt(), endOfDay);

        return later.stream()
                .filter(next -> !next.uid().equals(event.uid()))
                .map(next -> "%s – %s  %s".formatted(
                        TimeFormats.time(next.startsAt()), TimeFormats.time(next.endsAt()), next.title()))
                .collect(Collectors.joining("\n"));
    }
}
