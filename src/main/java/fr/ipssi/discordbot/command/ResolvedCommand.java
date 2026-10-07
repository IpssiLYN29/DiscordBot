package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.model.HelpQuestion;
import fr.ipssi.discordbot.repository.HelpRepository;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Optional;

public final class ResolvedCommand implements Command {

    private static final String RESOLVED_PREFIX = "[Résolu] ";
    private static final int MAX_THREAD_NAME_LENGTH = 100;

    private final HelpRepository helpRepository;

    public ResolvedCommand(HelpRepository helpRepository) {
        this.helpRepository = helpRepository;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("resolu", "Mark your question as solved and close its thread")
                .setGuildOnly(true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.getChannelType().isThread()) {
            event.reply("Cette commande s'utilise dans le fil d'une question.").setEphemeral(true).queue();
            return;
        }

        ThreadChannel thread = event.getChannel().asThreadChannel();
        Optional<HelpQuestion> question = helpRepository.find(thread.getIdLong());
        if (question.isEmpty()) {
            event.reply("Ce fil n'a pas été créé avec /question.").setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        boolean allowed = question.get().askerId() == event.getUser().getIdLong()
                || (member != null && member.hasPermission(Permission.MESSAGE_MANAGE));
        if (!allowed) {
            event.reply("Seul l'auteur de la question ou un modérateur peut la marquer comme résolue.")
                    .setEphemeral(true).queue();
            return;
        }
        if (question.get().resolved()) {
            event.reply("Cette question est déjà résolue.").setEphemeral(true).queue();
            return;
        }

        helpRepository.markResolved(thread.getIdLong());
        String name = RESOLVED_PREFIX + thread.getName();
        if (name.length() > MAX_THREAD_NAME_LENGTH) {
            name = name.substring(0, MAX_THREAD_NAME_LENGTH);
        }
        String resolvedName = name;

        event.reply("Question marquée comme résolue, merci à tous ! Le fil est archivé.")
                .queue(hook -> thread.getManager().setName(resolvedName).setArchived(true).queue());
    }
}
