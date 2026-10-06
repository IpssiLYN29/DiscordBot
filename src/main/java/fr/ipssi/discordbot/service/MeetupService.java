package fr.ipssi.discordbot.service;

import fr.ipssi.discordbot.model.Meetup;
import fr.ipssi.discordbot.model.MeetupStatus;
import fr.ipssi.discordbot.model.RsvpStatus;
import fr.ipssi.discordbot.repository.MeetupRepository;
import fr.ipssi.discordbot.util.TimeFormats;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MeetupService {

    public static final String BUTTON_PREFIX = "event:";

    private static final Logger LOGGER = LoggerFactory.getLogger(MeetupService.class);
    private static final Color OPEN_COLOR = new Color(0x57F287);
    private static final Color CLOSED_COLOR = new Color(0x747F8D);
    private static final int MAX_FIELD_LENGTH = 1000;
    private static final Duration REMINDER_LEAD_TIME = Duration.ofHours(1);

    private final MeetupRepository meetupRepository;

    public MeetupService(MeetupRepository meetupRepository) {
        this.meetupRepository = meetupRepository;
    }

    public MessageEmbed render(Meetup meetup) {
        boolean open = meetup.status() == MeetupStatus.OPEN;
        long epoch = meetup.startsAt().getEpochSecond();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(statusPrefix(meetup.status()) + meetup.title())
                .setColor(open ? OPEN_COLOR : CLOSED_COLOR)
                .setFooter("Événement #" + meetup.id())
                .addField("Organisateur", "<@" + meetup.createdBy() + ">", true)
                .addField("Quand", "<t:%d:F>\n<t:%d:R>".formatted(epoch, epoch), true);

        if (!meetup.description().isBlank()) {
            embed.setDescription(meetup.description());
        }
        if (!meetup.location().isBlank()) {
            embed.addField("Lieu", meetup.location(), true);
        }
        if (meetup.status() != MeetupStatus.CANCELLED) {
            Map<RsvpStatus, List<Long>> rsvps = meetupRepository.findRsvps(meetup.id());
            embed.addField(title("Je peux", rsvps.get(RsvpStatus.YES)), mentions(rsvps.get(RsvpStatus.YES)), false);
            embed.addField(title("Je ne peux pas", rsvps.get(RsvpStatus.NO)), mentions(rsvps.get(RsvpStatus.NO)), false);
        }
        return embed.build();
    }

    public List<ActionRow> buttons(Meetup meetup) {
        String prefix = BUTTON_PREFIX + meetup.id() + ":";
        return List.of(ActionRow.of(
                Button.success(prefix + RsvpStatus.YES.name(), "Je peux"),
                Button.danger(prefix + RsvpStatus.NO.name(), "Je peux pas")));
    }

    public void finish(JDA jda, Meetup meetup, MeetupStatus status) {
        meetupRepository.updateStatus(meetup.id(), status);

        Guild guild = jda.getGuildById(meetup.guildId());
        GuildMessageChannel channel =
                guild == null ? null : guild.getChannelById(GuildMessageChannel.class, meetup.channelId());
        if (channel == null) {
            return;
        }
        channel.editMessageEmbedsById(meetup.messageId(), render(meetup.withStatus(status)))
                .setComponents(List.of())
                .queue(null, error -> LOGGER.warn("Failed to update the message of event {}", meetup.id(), error));
    }

    public Instant reminderTime(Meetup meetup) {
        return meetup.startsAt().minus(REMINDER_LEAD_TIME);
    }

    public void remindUpcoming(JDA jda) {
        Instant now = Instant.now();
        meetupRepository.findPendingReminders(now, now.plus(REMINDER_LEAD_TIME))
                .forEach(meetup -> sendReminder(jda, meetup));
    }

    private void sendReminder(JDA jda, Meetup meetup) {
        Guild guild = jda.getGuildById(meetup.guildId());
        GuildMessageChannel channel =
                guild == null ? null : guild.getChannelById(GuildMessageChannel.class, meetup.channelId());
        if (channel == null) {
            return;
        }

        Set<Long> attendees = new LinkedHashSet<>();
        attendees.add(meetup.createdBy());
        attendees.addAll(meetupRepository.findRsvps(meetup.id()).get(RsvpStatus.YES));

        String location = meetup.location().isBlank() ? "" : " · " + meetup.location();
        String content = "Rappel : **%s** commence <t:%d:R> (%s)%s\n%s".formatted(
                meetup.title(),
                meetup.startsAt().getEpochSecond(),
                TimeFormats.time(meetup.startsAt()),
                location,
                mentions(List.copyOf(attendees)));

        channel.sendMessage(content)
                .setMessageReference(meetup.messageId())
                .failOnInvalidReply(false)
                .queue(
                        success -> meetupRepository.markReminded(meetup.id()),
                        error -> LOGGER.warn("Failed to send the reminder of event {}", meetup.id(), error));
    }

    public void endStarted(JDA jda) {
        meetupRepository.findStarted(Instant.now()).forEach(meetup -> finish(jda, meetup, MeetupStatus.ENDED));
    }

    private static String statusPrefix(MeetupStatus status) {
        return switch (status) {
            case OPEN -> "";
            case CANCELLED -> "[Annulé] ";
            case ENDED -> "[Terminé] ";
        };
    }

    private static String title(String label, List<Long> userIds) {
        return label + " (" + userIds.size() + ")";
    }

    private static String mentions(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return "—";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < userIds.size(); i++) {
            String mention = "<@" + userIds.get(i) + ">";
            if (result.length() + mention.length() > MAX_FIELD_LENGTH) {
                return result + " +" + (userIds.size() - i);
            }
            result.append(i == 0 ? "" : ", ").append(mention);
        }
        return result.toString();
    }
}
