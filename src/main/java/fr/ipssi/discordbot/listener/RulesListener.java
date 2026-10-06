package fr.ipssi.discordbot.listener;

import fr.ipssi.discordbot.service.SettingsService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public final class RulesListener extends ListenerAdapter {

    public static final String ACCEPT_BUTTON_ID = "rules:accept";

    private final SettingsService settings;

    public RulesListener(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!ACCEPT_BUTTON_ID.equals(event.getComponentId())) {
            return;
        }

        Guild guild = event.getGuild();
        Member member = event.getMember();
        Role role = guild == null ? null : settings.memberRoleId(guild.getIdLong())
                .map(guild::getRoleById)
                .orElse(null);
        if (member == null || role == null) {
            event.reply("Impossible de valider le règlement pour le moment.").setEphemeral(true).queue();
            return;
        }

        guild.addRoleToMember(member, role).queue(
                success -> event.reply("Règlement accepté, bienvenue !").setEphemeral(true).queue(),
                error -> event.reply("Impossible de t'attribuer le rôle, préviens un admin.").setEphemeral(true).queue()
        );
    }
}
