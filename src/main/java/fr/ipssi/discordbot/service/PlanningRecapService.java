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

    private record Recap(long channelId, long messageId, LocalDate weekStart) {
    }

    private final ScheduleRepository scheduleRepository;
    private final SettingsService settings;
    private final Map<Long, Recap> recaps = new ConcurrentHashMap<>();

    public PlanningRecapService(ScheduleRepository scheduleRepository, SettingsService settings) {
        this.scheduleRepository = scheduleRepository;
        this.settings = settings;
    }

    public void resetAll(JDA jda) {
        jda.getGuilds().forEach(this::reset);
    }

    public void reset(Guild guild) {
        TextChannel channel = planningChannel(guild);
        if (channel == null) {
            return;
        }
        recaps.remove(guild.getIdLong());

        channel.getIterableHistory().takeWhileAsync(message -> true)
                .thenCompose(messages -> CompletableFuture.allOf(
                        channel.purgeMessages(messages).toArray(CompletableFuture[]::new)))
                .thenRun(() -> post(guild, channel))
                .exceptionally(error -> {
                    LOGGER.error("Failed to reset the planning channel of guild {}", guild.getId(), error);
                    return null;
                });
    }

    public void refresh(Guild guild) {
        Recap recap = recaps.get(guild.getIdLong());
        if (recap == null) {
            reset(guild);
            return;
        }
        TextChannel channel = guild.getTextChannelById(recap.channelId());
        if (channel == null) {
            recaps.remove(guild.getIdLong());
            return;
        }

        Week week = currentWeek();
        channel.editMessageEmbedsById(recap.messageId(), buildEmbed(guild, week)).queue(
                success -> recaps.put(guild.getIdLong(), new Recap(recap.channelId(), recap.messageId(), week.start())),
                error -> {
                    LOGGER.warn("Failed to update the planning recap of guild {}", guild.getId(), error);
                    recaps.remove(guild.getIdLong());
                });
    }

    public void refreshOutdated(JDA jda) {
        LocalDate currentWeekStart = currentWeek().start();
        recaps.forEach((guildId, recap) -> {
            Guild guild = jda.getGuildById(guildId);
            if (guild != null && !recap.weekStart().equals(currentWeekStart)) {
                refresh(guild);
            }
        });
    }

    private void post(Guild guild, TextChannel channel) {
        Week week = currentWeek();
        channel.sendMessageEmbeds(buildEmbed(guild, week)).queue(message -> {
            recaps.put(guild.getIdLong(), new Recap(channel.getIdLong(), message.getIdLong(), week.start()));
            message.pin().queue(
                    success -> removePinNotice(channel, message.getIdLong()),
                    error -> LOGGER.warn("Failed to pin the planning recap of guild {}", guild.getId(), error));
        });
    }

    private static void removePinNotice(TextChannel channel, long messageId) {
        channel.getHistoryAfter(messageId, 1).queue(history -> history.getRetrievedHistory().stream()
                .filter(message -> message.getType() == MessageType.CHANNEL_PINNED_ADD)
                .forEach(message -> message.delete().queue()));
    }

    private TextChannel planningChannel(Guild guild) {
        return settings.planningChannelId(guild.getIdLong())
                .map(guild::getTextChannelById)
                .orElse(null);
    }

    private static Week currentWeek() {
        LocalDate today = LocalDate.now(TimeFormats.ZONE);
        boolean weekend = today.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue();
        LocalDate monday = weekend
                ? today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                : today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new Week(monday, weekend);
    }

    private MessageEmbed buildEmbed(Guild guild, Week week) {
        Instant from = week.start().atStartOfDay(TimeFormats.ZONE).toInstant();
        Instant until = week.start().plusDays(7).atStartOfDay(TimeFormats.ZONE).toInstant();
        List<ScheduleEvent> events = scheduleRepository.findStartingBetween(guild.getIdLong(), from, until);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Planning du %s au %s".formatted(
                        TimeFormats.dayMonth(week.start()), TimeFormats.dayMonth(week.end())))
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
