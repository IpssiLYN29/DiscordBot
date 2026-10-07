package fr.ipssi.discordbot.service;

import fr.ipssi.discordbot.model.ScheduleEvent;
import fr.ipssi.discordbot.repository.ScheduleRepository;
import fr.ipssi.discordbot.util.TimeFormats;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class ScheduleReminderService implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduleReminderService.class);
    private static final Duration REMINDER_LEAD_TIME = Duration.ofMinutes(30);
    private static final Color REMINDER_COLOR = new Color(0x5865F2);

    private final JDA jda;
    private final ScheduleRepository scheduleRepository;
    private final PlanningTargetService targets;

    public ScheduleReminderService(JDA jda, ScheduleRepository scheduleRepository, PlanningTargetService targets) {
        this.jda = jda;
        this.scheduleRepository = scheduleRepository;
        this.targets = targets;
    }

    @Override
    public void run() {
        Instant now = Instant.now();
        scheduleRepository.findPendingReminders(now, now.plus(REMINDER_LEAD_TIME)).forEach(this::remind);
    }

    public void announceNext(Guild guild) {
        Instant now = Instant.now();
        List<ScheduleEvent> upcoming = scheduleRepository.findAll(guild.getIdLong()).stream()
                .filter(event -> event.startsAt().isAfter(now))
                .toList();

        for (PlanningTarget target : targets.targets(guild.getIdLong())) {
            upcoming.stream()
                    .filter(target::matches)
                    .min(Comparator.comparing(ScheduleEvent::startsAt))
                    .ifPresent(event -> send(guild, target, event, now.plus(REMINDER_LEAD_TIME)));
        }
    }

    private void remind(ScheduleEvent event) {
        Guild guild = jda.getGuildById(event.guildId());
        if (guild == null) {
            return;
        }

        List<CompletableFuture<Boolean>> results = targets.targets(guild.getIdLong()).stream()
                .filter(target -> target.matches(event))
                .map(target -> send(guild, target, event, event.startsAt()))
                .flatMap(Optional::stream)
                .map(sent -> sent.handle((message, error) -> error == null))
                .toList();
        if (results.isEmpty()) {
            return;
        }

        CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).thenRun(() -> {
            if (results.stream().anyMatch(CompletableFuture::join)) {
                scheduleRepository.markReminded(event.guildId(), event.uid());
            } else {
                LOGGER.warn("Failed to send the reminder of event {}", event.uid());
            }
        });
    }

    private Optional<CompletableFuture<Message>> send(
            Guild guild, PlanningTarget target, ScheduleEvent event, Instant displayedStart) {
        TextChannel channel = guild.getTextChannelById(target.channelId());
        if (channel == null) {
            return Optional.empty();
        }

        MessageCreateBuilder message = new MessageCreateBuilder()
                .setContent(target.roleMention())
                .setEmbeds(buildEmbed(event, target, displayedStart).build());
        return Optional.of(channel.sendMessage(message.build()).submit());
    }

    private EmbedBuilder buildEmbed(ScheduleEvent event, PlanningTarget target, Instant displayedStart) {
        String timeRange = "%s – %s (%s)".formatted(
                TimeFormats.time(event.startsAt()),
                TimeFormats.time(event.endsAt()),
                TimeFormats.duration(Duration.between(event.startsAt(), event.endsAt())));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(event.title() + target.titleSuffix())
                .setDescription("Commence <t:%d:R>\n%s".formatted(displayedStart.getEpochSecond(), timeRange))
                .setColor(REMINDER_COLOR);

        if (!event.teacher().isBlank()) {
            embed.addField("Intervenant", event.teacher(), true);
        }
        if (!event.location().isBlank()) {
            embed.addField("Salle", event.location(), true);
        }

        String laterToday = laterToday(event, target);
        if (!laterToday.isEmpty()) {
            embed.addField("Ensuite aujourd'hui", laterToday, false);
        }
        return embed;
    }

    private String laterToday(ScheduleEvent event, PlanningTarget target) {
        Instant endOfDay = event.startsAt().atZone(TimeFormats.ZONE)
                .toLocalDate().plusDays(1).atStartOfDay(TimeFormats.ZONE).toInstant();
        List<ScheduleEvent> later = scheduleRepository.findStartingBetween(event.guildId(), event.startsAt(), endOfDay);

        return later.stream()
                .filter(next -> !next.uid().equals(event.uid()))
                .filter(target::matches)
                .map(next -> "%s – %s  %s".formatted(
                        TimeFormats.time(next.startsAt()), TimeFormats.time(next.endsAt()), next.title()))
                .collect(Collectors.joining("\n"));
    }
}
