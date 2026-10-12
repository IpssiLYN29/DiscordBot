package fr.ipssi.discordbot.listener;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;

public final class DelegueListener extends ListenerAdapter {

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (!event.getModalId().startsWith("delegue_modal:")) {
            return;
        }

        String[] parts = event.getModalId().split(":");
        if (parts.length < 4) {
            event.reply("Le formulaire est invalide.").setEphemeral(true).queue();
            return;
        }

        String delegateId = parts[1];
        boolean isAnonymous = Boolean.parseBoolean(parts[2]);
        String message = event.getValue("body") == null ? "" : event.getValue("body").getAsString();

        if (event.getGuild() == null) {
            event.reply("Impossible d'envoyer ce message depuis ici.").setEphemeral(true).queue();
            return;
        }

        Member delegate = event.getGuild().getMemberById(delegateId);
        if (delegate == null) {
            event.reply("Le délégué n'est plus disponible.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(isAnonymous ? Color.GRAY : new Color(88, 101, 242))
                .setTitle(isAnonymous ? "Message anonyme" : "Message au délégué")
                .setDescription(message)
                .setTimestamp(Instant.now());

        if (!isAnonymous) {
            embed.setAuthor(
                    event.getMember() == null ? event.getUser().getName() : event.getMember().getEffectiveName(),
                    null,
                    event.getUser().getEffectiveAvatarUrl()
            );
        }

        delegate.getUser().openPrivateChannel().queue(
                channel -> channel.sendMessageEmbeds(embed.build()).queue(
                        success -> event.reply("Ton message a bien été envoyé au délégué.").setEphemeral(true).queue(),
                        error -> event.reply("Le délégué n'a pas pu recevoir ton message pour le moment.").setEphemeral(true).queue()
                ),
                error -> event.reply("Impossible d'ouvrir le MP du délégué.").setEphemeral(true).queue()
        );
    }
}
