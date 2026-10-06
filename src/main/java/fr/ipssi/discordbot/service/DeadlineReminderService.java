package fr.ipssi.discordbot.service;

import fr.ipssi.discordbot.model.Deadline;
import fr.ipssi.discordbot.repository.DeadlineRepository;
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
import java.util.OptionalInt;

public final class DeadlineReminderService implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeadlineReminderService.class);
    private static final List<Integer> REMINDER_DAYS = List.of(7, 2, 1);
    private static final Color REMINDER_COLOR = new Color(0xED4245);

    private final JDA jda;
    private final DeadlineRepository deadlineRepository;
    private final SettingsService settings;

    public DeadlineReminderService(JDA jda, DeadlineRepository deadlineRepository, SettingsService settings) {
        this.jda = jda;
        this.deadlineRepository = deadlineRepository;
        this.settings = settings;
    }

    @Override
    public void run() {
        Instant now = Instant.now();
        for (Deadline deadline : deadlineRepository.findAllUpcoming(now)) {
            nextReminderDays(deadline, now).ifPresent(days -> sendReminder(deadline, days));
        }
    }

    private static OptionalInt nextReminderDays(Deadline deadline, Instant now) {
        return REMINDER_DAYS.stream()
                .mapToInt(Integer::intValue)
                .filter(days -> days < deadline.lastReminderDays())
                .filter(days -> !now.isBefore(deadline.dueAt().minus(Duration.ofDays(days))))
                .min();
    }

    private void sendReminder(Deadline deadline, int days) {
        Guild guild = jda.getGuildById(deadline.guildId());
        if (guild == null) {
            return;
        }
        TextChannel channel = settings.deadlineChannelId(guild.getIdLong())
                .map(guild::getTextChannelById)
                .orElse(null);
        if (channel == null) {
            return;
        }

        String mention = settings.memberRoleId(guild.getIdLong())
                .map(roleId -> "<@&" + roleId + ">")
                .orElse("");
        long epoch = deadline.dueAt().getEpochSecond();
        String description = "**%s** à rendre <t:%d:R>\n<t:%d:F>".formatted(deadline.subject(), epoch, epoch);
        if (!deadline.description().isBlank()) {
            description += "\n" + deadline.description();
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Rappel J-" + days)
                .setDescription(description)
                .setColor(REMINDER_COLOR);
        MessageCreateBuilder message = new MessageCreateBuilder().setContent(mention).setEmbeds(embed.build());

        channel.sendMessage(message.build()).queue(
                success -> deadlineRepository.updateLastReminder(deadline.id(), days),
                error -> LOGGER.warn("Failed to send reminder for deadline {}", deadline.id(), error));
    }
}
