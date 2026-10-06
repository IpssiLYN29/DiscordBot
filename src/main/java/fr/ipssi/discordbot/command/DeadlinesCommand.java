package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.model.Deadline;
import fr.ipssi.discordbot.repository.DeadlineRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public final class DeadlinesCommand implements Command {

    private static final int MAX_DISPLAYED = 15;

    private final DeadlineRepository deadlineRepository;

    public DeadlinesCommand(DeadlineRepository deadlineRepository) {
        this.deadlineRepository = deadlineRepository;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("deadlines", "Show the upcoming deadlines")
                .setGuildOnly(true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<Deadline> deadlines = deadlineRepository.findUpcoming(event.getGuild().getIdLong(), Instant.now());
        if (deadlines.isEmpty()) {
            event.reply("Aucune deadline à venir.").setEphemeral(true).queue();
            return;
        }

        String content = deadlines.stream()
                .limit(MAX_DISPLAYED)
                .map(DeadlinesCommand::format)
                .collect(Collectors.joining("\n\n"));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Deadlines à venir")
                .setDescription(content);
        event.replyEmbeds(embed.build()).queue();
    }

    private static String format(Deadline deadline) {
        long epoch = deadline.dueAt().getEpochSecond();
        String line = "`#%d` **%s**\n<t:%d:F> (<t:%d:R>)".formatted(deadline.id(), deadline.subject(), epoch, epoch);
        return deadline.description().isBlank() ? line : line + "\n" + deadline.description();
    }
}
