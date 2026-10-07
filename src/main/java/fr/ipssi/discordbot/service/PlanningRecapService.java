package fr.ipssi.discordbot.service;

import fr.ipssi.discordbot.model.ScheduleEvent;
import fr.ipssi.discordbot.repository.ScheduleRepository;
import fr.ipssi.discordbot.util.TimeFormats;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class PlanningRecapService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanningRecapService.class);
    private static final Color RECAP_COLOR = new Color(0x57F287);

    private record Week(LocalDate start, boolean upcoming) {

        LocalDate end() {
            return start.plusDays(6);
        }
    }

    private record Recap(long guildId, long messageId, LocalDate weekStart) {
    }

    private final ScheduleRepository scheduleRepository;
    private final PlanningTargetService targets;
    private final Map<Long, Recap> recapsByChannel = new ConcurrentHashMap<>();

    public PlanningRecapService(ScheduleRepository scheduleRepository, PlanningTargetService targets) {
        this.scheduleRepository = scheduleRepository;
        this.targets = targets;
    }

    public void resetAll(JDA jda) {
        jda.getGuilds().forEach(this::reset);
    }

    public void reset(Guild guild) {
        targets.targets(guild.getIdLong()).forEach(target -> reset(guild, target));
    }

    public void refresh(Guild guild) {
        targets.targets(guild.getIdLong()).forEach(target -> refresh(guild, target));
    }

    public void refreshOutdated(JDA jda) {
        LocalDate currentWeekStart = currentWeek().start();
        recapsByChannel.forEach((channelId, recap) -> {
            Guild guild = jda.getGuildById(recap.guildId());
            if (guild == null || recap.weekStart().equals(currentWeekStart)) {
                return;
            }
            targets.targets(guild.getIdLong()).stream()
                    .filter(target -> target.channelId() == channelId)
                    .forEach(target -> refresh(guild, target));
        });
    }

    private void reset(Guild guild, PlanningTarget target) {
        TextChannel channel = guild.getTextChannelById(target.channelId());
        if (channel == null) {
            return;
        }
        recapsByChannel.remove(channel.getIdLong());

        channel.getIterableHistory().takeWhileAsync(message -> true)
                .thenCompose(messages -> CompletableFuture.allOf(
                        channel.purgeMessages(messages).toArray(CompletableFuture[]::new)))
                .thenRun(() -> post(guild, channel, target))
                .exceptionally(error -> {
                    LOGGER.error("Failed to reset the planning channel {}", channel.getId(), error);
                    return null;
                });
    }

    private void refresh(Guild guild, PlanningTarget target) {
        Recap recap = recapsByChannel.get(target.channelId());
        if (recap == null) {
            reset(guild, target);
            return;
        }
        TextChannel channel = guild.getTextChannelById(target.channelId());
        if (channel == null) {
            recapsByChannel.remove(target.channelId());
            return;
        }

        Week week = currentWeek();
        channel.editMessageEmbedsById(recap.messageId(), buildEmbed(guild, week, target)).queue(
                success -> recapsByChannel.put(
                        channel.getIdLong(), new Recap(guild.getIdLong(), recap.messageId(), week.start())),
                error -> {
                    LOGGER.warn("Failed to update the planning recap of channel {}", channel.getId(), error);
                    recapsByChannel.remove(channel.getIdLong());
                });
    }

    private void post(Guild guild, TextChannel channel, PlanningTarget target) {
        Week week = currentWeek();
        channel.sendMessageEmbeds(buildEmbed(guild, week, target)).queue(message -> {
            recapsByChannel.put(channel.getIdLong(), new Recap(guild.getIdLong(), message.getIdLong(), week.start()));
            message.pin().queue(
                    success -> removePinNotice(channel, message.getIdLong()),
                    error -> LOGGER.warn("Failed to pin the planning recap of channel {}", channel.getId(), error));
        });
    }

    private static void removePinNotice(TextChannel channel, long messageId) {
        channel.getHistoryAfter(messageId, 1).queue(history -> history.getRetrievedHistory().stream()
                .filter(message -> message.getType() == MessageType.CHANNEL_PINNED_ADD)
                .forEach(message -> message.delete().queue()));
    }

    private static Week currentWeek() {
        LocalDate today = LocalDate.now(TimeFormats.ZONE);
        boolean weekend = today.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue();
        LocalDate monday = weekend
                ? today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                : today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new Week(monday, weekend);
    }

    private MessageEmbed buildEmbed(Guild guild, Week week, PlanningTarget target) {
        Instant from = week.start().atStartOfDay(TimeFormats.ZONE).toInstant();
        Instant until = week.start().plusDays(7).atStartOfDay(TimeFormats.ZONE).toInstant();
        List<ScheduleEvent> events = scheduleRepository.findStartingBetween(guild.getIdLong(), from, until).stream()
                .filter(target::matches)
                .toList();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Planning du %s au %s%s".formatted(
                        TimeFormats.dayMonth(week.start()), TimeFormats.dayMonth(week.end()), target.titleSuffix()))
                .setColor(RECAP_COLOR)
                .setTimestamp(Instant.now());

        if (events.isEmpty()) {
            return embed.setDescription("Aucun cours cette semaine.").build();
        }

        embed.setDescription(week.upcoming() ? "Semaine prochaine" : "Semaine en cours");
        Map<LocalDate, List<ScheduleEvent>> byDay = events.stream().collect(Collectors.groupingBy(
                event -> event.startsAt().atZone(TimeFormats.ZONE).toLocalDate(), TreeMap::new, Collectors.toList()));
        byDay.forEach((day, dayEvents) -> embed.addField(TimeFormats.dayLabel(day), formatDay(dayEvents), false));
        return embed.build();
    }

    private static String formatDay(List<ScheduleEvent> events) {
        String value = events.stream().map(PlanningRecapService::formatEvent).collect(Collectors.joining("\n"));
        if (value.length() <= MessageEmbed.VALUE_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, MessageEmbed.VALUE_MAX_LENGTH - 1) + "…";
    }

    private static String formatEvent(ScheduleEvent event) {
        StringBuilder line = new StringBuilder("`%s – %s` %s".formatted(
                TimeFormats.time(event.startsAt()), TimeFormats.time(event.endsAt()), event.title()));
        if (!event.teacher().isBlank()) {
            line.append(" · ").append(event.teacher());
        }
        if (!event.location().isBlank()) {
            line.append(" · ").append(event.location());
        }
        return line.toString();
    }
}
