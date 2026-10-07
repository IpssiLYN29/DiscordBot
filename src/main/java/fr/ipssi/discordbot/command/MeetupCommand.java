package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.Main;
import fr.ipssi.discordbot.model.Meetup;
import fr.ipssi.discordbot.model.MeetupStatus;
import fr.ipssi.discordbot.repository.MeetupRepository;
import fr.ipssi.discordbot.service.MeetupService;
import fr.ipssi.discordbot.util.TimeFormats;
import net.dv8tion.jda.api.EmbedBuilder;
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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class MeetupCommand implements Command {

    private static final int MAX_LISTED = 10;

    private final MeetupRepository meetupRepository;
    private final MeetupService meetupService;

    public MeetupCommand(MeetupRepository meetupRepository, MeetupService meetupService) {
        this.meetupRepository = meetupRepository;
        this.meetupService = meetupService;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("event", "Organize an outing after class")
                .setGuildOnly(true)
                .addSubcommands(
                        new SubcommandData("create", "Create an event")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "title", "What are we doing?", true)
                                                .setMaxLength(80),
                                        new OptionData(OptionType.STRING, "date", "JJ/MM/AAAA HH:mm (heure de Paris)", true),
                                        new OptionData(OptionType.STRING, "location", "Where?")
                                                .setMaxLength(80),
                                        new OptionData(OptionType.STRING, "description", "More details")
                                                .setMaxLength(300)),
                        new SubcommandData("cancel", "Cancel an event")
                                .addOptions(new OptionData(OptionType.INTEGER, "id", "Event number (shown in its footer)", true)
                                        .setMinValue(1)),
                        new SubcommandData("list", "Show the upcoming events"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        switch (event.getSubcommandName()) {
            case "create" -> create(event);
            case "cancel" -> cancel(event);
            case "list" -> list(event);
            default -> event.reply("Sous-commande inconnue.").setEphemeral(true).queue();
        }
    }

    private void create(SlashCommandInteractionEvent event) {
        Instant startsAt;
        try {
            startsAt = TimeFormats.parseDateTime(event.getOption("date", OptionMapping::getAsString));
        } catch (DateTimeParseException e) {
            event.reply("Date invalide. Format attendu : JJ/MM/AAAA HH:mm (ex. 25/12/2026 18:00).")
                    .setEphemeral(true).queue();
            return;
        }
        if (!startsAt.isAfter(Instant.now())) {
            event.reply("La date doit être dans le futur.").setEphemeral(true).queue();
            return;
        }

        GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();
        Meetup draft = Meetup.create(
                event.getGuild().getIdLong(),
                channel.getIdLong(),
                event.getOption("title", OptionMapping::getAsString),
                event.getOption("description", "", OptionMapping::getAsString),
                event.getOption("location", "", OptionMapping::getAsString),
                startsAt,
                event.getUser().getIdLong());
        Meetup meetup = draft.withId(meetupRepository.create(draft));

        channel.sendMessageEmbeds(meetupService.render(meetup)).setComponents(meetupService.buttons(meetup)).queue(
                message -> {
                    meetupRepository.attachMessage(meetup.id(), channel.getIdLong(), message.getIdLong());
                    Main.getLogger().info("New event has been created (#" + meetup.id() + ")");
                    event.reply("Événement #" + meetup.id() + " publié.").setEphemeral(true).queue();
                },
                error -> {
                    meetupRepository.updateStatus(meetup.id(), MeetupStatus.CANCELLED);
                    event.reply("Impossible de publier l'événement dans ce salon.").setEphemeral(true).queue();
                });
    }

    private void cancel(SlashCommandInteractionEvent event) {
        long id = event.getOption("id", OptionMapping::getAsLong);
        Optional<Meetup> found = meetupRepository.find(id)
                .filter(meetup -> meetup.guildId() == event.getGuild().getIdLong());
        if (found.isEmpty()) {
            event.reply("Aucun événement avec le numéro " + id + ".").setEphemeral(true).queue();
            return;
        }

        Meetup meetup = found.get();
        Member member = event.getMember();
        boolean allowed = meetup.createdBy() == event.getUser().getIdLong()
                || (member != null && member.hasPermission(Permission.MESSAGE_MANAGE));
        if (!allowed) {
            event.reply("Seul l'organisateur ou un modérateur peut annuler cet événement.").setEphemeral(true).queue();
            return;
        }
        if (meetup.status() != MeetupStatus.OPEN) {
            event.reply("Cet événement est déjà terminé ou annulé.").setEphemeral(true).queue();
            return;
        }

        meetupService.finish(event.getJDA(), meetup, MeetupStatus.CANCELLED);
        event.reply("Événement #" + id + " annulé.").setEphemeral(true).queue();
    }

    private void list(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        List<Meetup> upcoming = meetupRepository.findUpcoming(guildId, Instant.now());
        if (upcoming.isEmpty()) {
            event.reply("Aucun événement à venir. Crée-en un avec /event create !").setEphemeral(true).queue();
            return;
        }

        String content = upcoming.stream()
                .limit(MAX_LISTED)
                .map(meetup -> "`#%d` **%s** · <t:%d:F> · <@%d> · [voir](https://discord.com/channels/%d/%d/%d)".formatted(
                        meetup.id(), meetup.title(), meetup.startsAt().getEpochSecond(), meetup.createdBy(),
                        guildId, meetup.channelId(), meetup.messageId()))
                .collect(Collectors.joining("\n"));

        EmbedBuilder embed = new EmbedBuilder().setTitle("Événements à venir").setDescription(content);
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
