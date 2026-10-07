package fr.ipssi.discordbot.command;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public interface Command {

    SlashCommandData definition();

    void execute(SlashCommandInteractionEvent event);

    default void autocomplete(CommandAutoCompleteInteractionEvent event) {
    }

    default String name() {
        return definition().getName();
    }
}
