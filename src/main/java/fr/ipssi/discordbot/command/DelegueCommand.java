package fr.ipssi.discordbot.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;

public final class DelegueCommand extends ListenerAdapter implements Command {

    private static final long DELEGUE_ROLE_ID = 1558036770782847007L;
    private static final long PROMO_LYN_ROLE_ID = 1556707717601697863L;
    private static final long PROMO_BDX_ROLE_ID = 1557018331842617376L;

    @Override
    public SlashCommandData definition() {
        return Commands.slash("delegue", "Envoyer un message au délégué")
                .setGuildOnly(true)
                .addOptions(
                        new OptionData(OptionType.STRING, "anonymous", "Le message est-il anonyme ?", true)
                                .addChoice("Oui", "yes")
                                .addChoice("Non", "no")
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member requester = event.getMember();
        if (requester == null || event.getGuild() == null) {
            event.reply("Cette commande ne peut pas être utilisée ici.").setEphemeral(true).queue();
            return;
        }

        String anonymous = event.getOption("anonymous", "yes", OptionMapping::getAsString);

        long campusRoleId = requester.getRoles().stream()
                .map(Role::getIdLong)
                .filter(id -> id == PROMO_LYN_ROLE_ID || id == PROMO_BDX_ROLE_ID)
                .findFirst()
                .orElse(0L);

        if (campusRoleId == 0L) {
            event.reply("Tu dois avoir un rôle de promo pour utiliser cette commande.").setEphemeral(true).queue();
            return;
        }

        Member delegue = event.getGuild().getMembers().stream()
                .filter(m -> m.getRoles().stream().anyMatch(r -> r.getIdLong() == DELEGUE_ROLE_ID))
                .filter(m -> m.getRoles().stream().anyMatch(r -> r.getIdLong() == campusRoleId))
                .findFirst()
                .orElse(null);

        if (delegue == null) {
            event.reply("Aucun délégué n'a été trouvé pour ta promo.").setEphemeral(true).queue();
            return;
        }

        Member member = event.getOption("member", OptionMapping::getAsMember);
        String modalId = "delegue_modal:" + delegue.getId() + ":" + anonymous + ":" + (member == null ? "0" : member.getId());

        TextInput messageInput = TextInput.create("body", "Message", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Décris ton problème ici...")
                .setMaxLength(1000)
                .build();

        Modal modal = Modal.create(modalId, "Message au délégué")
                .addActionRow(messageInput)
                .build();

        event.replyModal(modal).queue();
    }

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
        String anonymous = parts[2];
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

        boolean isAnonymous = "yes".equalsIgnoreCase(anonymous);
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
