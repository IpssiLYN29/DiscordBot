package fr.ipssi.discordbot.command;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

final class ModerationChecks {

    private ModerationChecks() {
    }

    static boolean canModerate(SlashCommandInteractionEvent event, Member target) {
        Member moderator = event.getMember();
        boolean allowed = moderator != null
                && target != null
                && !moderator.equals(target)
                && moderator.canInteract(target)
                && event.getGuild().getSelfMember().canInteract(target);

        if (!allowed) {
            event.reply("Action impossible sur ce membre.").setEphemeral(true).queue();
        }
        return allowed;
    }
}
