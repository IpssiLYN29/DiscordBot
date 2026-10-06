package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.Main;
import fr.ipssi.discordbot.model.Poll;
import fr.ipssi.discordbot.repository.PollRepository;
import fr.ipssi.discordbot.service.PollService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public final class PollCommand implements Command {

    private static final int MAX_OPTIONS = 5;
    private static final int MAX_DURATION_HOURS = 168;

    private final PollRepository pollRepository;
    private final PollService pollService;

    public PollCommand(PollRepository pollRepository, PollService pollService) {
        this.pollRepository = pollRepository;
        this.pollService = pollService;
    }

    @Override
    public SlashCommandData definition() {
        SubcommandData create = new SubcommandData("create", "Create a poll")
                .addOptions(new OptionData(OptionType.STRING, "question", "Question", true).setMaxLength(200));
        IntStream.rangeClosed(1, MAX_OPTIONS).forEach(number -> create.addOptions(
                new OptionData(OptionType.STRING, "option" + number, "Option " + number, number <= 2)
                        .setMaxLength(70)));
        create.addOptions(new OptionData(OptionType.INTEGER, "hours", "Close automatically after this many hours")
                .setRequiredRange(1, MAX_DURATION_HOURS));

        SubcommandData close = new SubcommandData("close", "Close a poll")
                .addOptions(new OptionData(OptionType.INTEGER, "id", "Poll number (shown in its footer)", true)
                        .setMinValue(1));

        return Commands.slash("poll", "Create and manage polls")
                .setGuildOnly(true)
                .addSubcommands(create, close);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        switch (event.getSubcommandName()) {
            case "create" -> create(event);
            case "close" -> close(event);
            default -> event.reply("Sous-commande inconnue.").setEphemeral(true).queue();
        }
    }

    private void create(SlashCommandInteractionEvent event) {
        List<String> options = IntStream.rangeClosed(1, MAX_OPTIONS)
                .mapToObj(number -> event.getOption("option" + number, OptionMapping::getAsString))
                .filter(option -> option != null && !option.isBlank())
                .map(String::trim)
                .toList();
        if (options.size() < 2) {
            event.reply("Il faut au moins deux options.").setEphemeral(true).queue();
            return;
        }

        Long hours = event.getOption("hours", OptionMapping::getAsLong);
        Instant closesAt = hours == null ? null : Instant.now().plus(Duration.ofHours(hours));
        GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();

        Poll draft = Poll.create(
                event.getGuild().getIdLong(),
                channel.getIdLong(),
                event.getOption("question", OptionMapping::getAsString),
                options,
                event.getUser().getIdLong(),
                closesAt);
        Poll poll = draft.withId(pollRepository.create(draft));

        channel.sendMessageEmbeds(pollService.render(poll)).setComponents(pollService.buttons(poll)).queue(
                message -> {
                    pollRepository.attachMessage(poll.id(), channel.getIdLong(), message.getIdLong());
                    Main.getLogger().info("New poll has been created (#" + poll.id() + ")");
                    event.reply("Sondage #" + poll.id() + " publié.").setEphemeral(true).queue();
                },
                error -> {
                    pollRepository.close(poll.id());
                    event.reply("Impossible de publier le sondage dans ce salon.").setEphemeral(true).queue();
                });
    }

    private void close(SlashCommandInteractionEvent event) {
        long id = event.getOption("id", OptionMapping::getAsLong);
        Optional<Poll> found = pollRepository.find(id)
                .filter(poll -> poll.guildId() == event.getGuild().getIdLong());
        if (found.isEmpty()) {
            event.reply("Aucun sondage avec le numéro " + id + ".").setEphemeral(true).queue();
            return;
        }

        Poll poll = found.get();
        Member member = event.getMember();
        boolean allowed = poll.createdBy() == event.getUser().getIdLong()
                || (member != null && member.hasPermission(Permission.MESSAGE_MANAGE));
        if (!allowed) {
            event.reply("Seul l'auteur du sondage ou un modérateur peut le clôturer.").setEphemeral(true).queue();
            return;
        }
        if (poll.closed()) {
            event.reply("Ce sondage est déjà terminé.").setEphemeral(true).queue();
            return;
        }

        pollService.close(event.getJDA(), poll);
        event.reply("Sondage #" + id + " clôturé.").setEphemeral(true).queue();
    }
}
