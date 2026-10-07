package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.model.HelpQuestion;
import fr.ipssi.discordbot.model.Subject;
import fr.ipssi.discordbot.repository.HelpRepository;
import fr.ipssi.discordbot.repository.SubjectRepository;
import fr.ipssi.discordbot.service.SettingsService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class QuestionCommand implements Command {

    private static final int MAX_THREAD_NAME_LENGTH = 100;
    private static final int MAX_CHOICES = 25;

    private final SettingsService settings;
    private final SubjectRepository subjectRepository;
    private final HelpRepository helpRepository;

    public QuestionCommand(
            SettingsService settings, SubjectRepository subjectRepository, HelpRepository helpRepository) {
        this.settings = settings;
        this.subjectRepository = subjectRepository;
        this.helpRepository = helpRepository;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("question", "Ask for help in a dedicated thread")
                .setGuildOnly(true)
                .addOptions(
                        new OptionData(OptionType.STRING, "subject", "Subject of your question", true)
                                .setAutoComplete(true),
                        new OptionData(OptionType.STRING, "title", "Your question in a few words", true)
                                .setMaxLength(60),
                        new OptionData(OptionType.STRING, "details", "Context, what you tried, errors...")
                                .setMaxLength(1000));
    }

    @Override
    public void autocomplete(CommandAutoCompleteInteractionEvent event) {
        if (!event.getFocusedOption().getName().equals("subject")) {
            return;
        }

        String typed = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        List<String> choices = subjectRepository.findAll(event.getGuild().getIdLong()).stream()
                .map(Subject::name)
                .filter(name -> name.toLowerCase(Locale.ROOT).contains(typed))
                .limit(MAX_CHOICES)
                .toList();
        event.replyChoiceStrings(choices).queue();
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        TextChannel helpChannel = settings.helpChannelId(guild.getIdLong())
                .map(guild::getTextChannelById)
                .orElse(null);
        if (helpChannel == null) {
            event.reply("Aucun salon d'aide n'est configuré.").setEphemeral(true).queue();
            return;
        }

        String subjectName = event.getOption("subject", OptionMapping::getAsString);
        Optional<Subject> subject = subjectRepository.find(guild.getIdLong(), subjectName.trim());
        if (subject.isEmpty()) {
            event.reply("Matière inconnue. Matières disponibles : " + subjectList(guild.getIdLong()))
                    .setEphemeral(true).queue();
            return;
        }

        String title = event.getOption("title", OptionMapping::getAsString).trim();
        String details = event.getOption("details", "", OptionMapping::getAsString).trim();
        String threadName = "[%s] %s".formatted(subject.get().name(), title);
        if (threadName.length() > MAX_THREAD_NAME_LENGTH) {
            threadName = threadName.substring(0, MAX_THREAD_NAME_LENGTH);
        }

        long askerId = event.getUser().getIdLong();
        String opening = "<@&%d> %s a besoin d'aide en **%s** :\n**%s**%s".formatted(
                subject.get().roleId(),
                event.getUser().getAsMention(),
                subject.get().name(),
                title,
                details.isEmpty() ? "" : "\n" + details);

        helpChannel.createThreadChannel(threadName).queue(
                thread -> {
                    helpRepository.save(new HelpQuestion(thread.getIdLong(), guild.getIdLong(), askerId, false));
                    thread.addThreadMemberById(askerId).queue();
                    thread.sendMessage(opening).queue();
                    event.reply("Ton fil est prêt : " + thread.getAsMention()
                            + "\nQuand c'est réglé, tape /resolu dedans.").setEphemeral(true).queue();
                },
                error -> event.reply("Impossible de créer le fil dans le salon d'aide.").setEphemeral(true).queue());
    }

    private String subjectList(long guildId) {
        String names = subjectRepository.findAll(guildId).stream()
                .map(Subject::name)
                .collect(Collectors.joining(", "));
        return names.isEmpty() ? "aucune configurée" : names;
    }
}
