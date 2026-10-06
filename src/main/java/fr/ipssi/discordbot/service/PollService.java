package fr.ipssi.discordbot.service;

import fr.ipssi.discordbot.model.Poll;
import fr.ipssi.discordbot.repository.PollRepository;
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
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

public final class PollService {

    public static final String BUTTON_PREFIX = "poll:";

    private static final Logger LOGGER = LoggerFactory.getLogger(PollService.class);
    private static final Color OPEN_COLOR = new Color(0x5865F2);
    private static final Color CLOSED_COLOR = new Color(0x747F8D);
    private static final int BAR_LENGTH = 10;

    private final PollRepository pollRepository;

    public PollService(PollRepository pollRepository) {
        this.pollRepository = pollRepository;
    }

    public MessageEmbed render(Poll poll) {
        List<Integer> counts = pollRepository.countVotes(poll.id(), poll.options().size());
        int total = counts.stream().mapToInt(Integer::intValue).sum();

        StringBuilder description = new StringBuilder();
        for (int i = 0; i < poll.options().size(); i++) {
            int count = counts.get(i);
            int percent = total == 0 ? 0 : Math.round(count * 100f / total);
            int filled = Math.round(percent * BAR_LENGTH / 100f);
            description.append("**%d. %s**\n`%s%s` %d%% (%d)\n\n".formatted(
                    i + 1, poll.options().get(i), "█".repeat(filled), "░".repeat(BAR_LENGTH - filled), percent, count));
        }
        if (!poll.closed() && poll.closesAt() != null) {
            description.append("Fin <t:%d:R>".formatted(poll.closesAt().getEpochSecond()));
        }

        String footer = "Sondage #%d · %d vote(s)%s".formatted(poll.id(), total, poll.closed() ? " · Terminé" : "");
        return new EmbedBuilder()
                .setTitle(poll.question())
                .setDescription(description.toString().stripTrailing())
                .setColor(poll.closed() ? CLOSED_COLOR : OPEN_COLOR)
                .setFooter(footer)
                .build();
    }

    public List<ActionRow> buttons(Poll poll) {
        List<Button> buttons = IntStream.range(0, poll.options().size())
                .mapToObj(i -> Button.secondary(BUTTON_PREFIX + poll.id() + ":" + i, (i + 1) + ". " + poll.options().get(i)))
                .toList();
        return List.of(ActionRow.of(buttons));
    }

    public void close(JDA jda, Poll poll) {
        pollRepository.close(poll.id());

        Guild guild = jda.getGuildById(poll.guildId());
        GuildMessageChannel channel = guild == null ? null : guild.getChannelById(GuildMessageChannel.class, poll.channelId());
        if (channel == null) {
            return;
        }
        channel.editMessageEmbedsById(poll.messageId(), render(poll.asClosed()))
                .setComponents(List.of())
                .queue(null, error -> LOGGER.warn("Failed to close the message of poll {}", poll.id(), error));
    }

    public void closeDue(JDA jda) {
        pollRepository.findDue(Instant.now()).forEach(poll -> close(jda, poll));
    }
}
