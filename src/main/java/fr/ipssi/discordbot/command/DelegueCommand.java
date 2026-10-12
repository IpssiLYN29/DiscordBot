package fr.ipssi.discordbot.command;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import fr.ipssi.discordbot.service.SettingsService;

public final class DelegueCommand implements Command {

    private final SettingsService settings;

    public DelegueCommand(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("delegue", "Envoyer un message au délégué")
                .setGuildOnly(true)
                .addOptions(
                        new OptionData(OptionType.BOOLEAN, "anonymous", "Le message est-il anonyme ?", true)
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member requester = event.getMember();
        if (requester == null || event.getGuild() == null) {
            event.reply("Cette commande ne peut pas être utilisée ici.").setEphemeral(true).queue();
            return;
        }

        Boolean isAnonymous = event.getOption("anonymous", OptionMapping::getAsBoolean);
        if (isAnonymous == null) {
            event.reply("Indique si ton message est anonyme.").setEphemeral(true).queue();
            return;
        }

        long campusRoleId = requester.getRoles().stream()
                .map(Role::getIdLong)
                .filter(id -> id == settings.promoLynRoleId(event.getGuild().getIdLong())
                        || id == settings.promoBdxRoleId(event.getGuild().getIdLong()))
                .findFirst()
                .orElse(0L);

        if (campusRoleId == 0L) {
            event.reply("Tu dois avoir un rôle de promo pour utiliser cette commande.").setEphemeral(true).queue();
            return;
        }

        Member delegue = event.getGuild().getMembers().stream()
                .filter(m -> m.getRoles().stream().anyMatch(r -> r.getIdLong() == settings.delegueRoleId(event.getGuild().getIdLong())))
                .filter(m -> m.getRoles().stream().anyMatch(r -> r.getIdLong() == campusRoleId))
                .findFirst()
                .orElse(null);

        if (delegue == null) {
            event.reply("Aucun délégué n'a été trouvé pour ta promo.").setEphemeral(true).queue();
            return;
        }

        Member member = event.getOption("member", OptionMapping::getAsMember);
        String modalId = "delegue_modal:" + delegue.getId() + ":" + isAnonymous + ":" + (member == null ? "0" : member.getId());

        TextInput messageInput = TextInput.create("body", "Message", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Décris ton problème ici...")
                .setMaxLength(1000)
                .build();

        Modal modal = Modal.create(modalId, "Message au délégué")
                .addActionRow(messageInput)
                .build();

        event.replyModal(modal).queue();
    }
}
