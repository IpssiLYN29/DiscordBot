package fr.ipssi.discordbot.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CommandManager extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);

    private final Map<String, Command> commands = new LinkedHashMap<>();

    public void register(Command command) {
        commands.put(command.name(), command);
    }

    public void publish(JDA jda) {
        jda.updateCommands().queue();
        jda.getGuilds().forEach(this::publish);
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        publish(event.getGuild());
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        Command command = commands.get(event.getName());
        if (command != null) {
            command.execute(event);
        }
    }

    private void publish(Guild guild) {
        List<CommandData> definitions = commands.values().stream().map(Command::definition).map(CommandData.class::cast).toList();
        guild.updateCommands().addCommands(definitions).queue(
                published -> LOGGER.info("Published {} commands on {}", published.size(), guild.getName()),
                error -> LOGGER.error("Failed to publish commands on {}", guild.getName(), error));
    }
}
