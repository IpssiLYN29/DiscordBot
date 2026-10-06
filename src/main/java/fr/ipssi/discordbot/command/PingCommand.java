package fr.ipssi.discordbot.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class PingCommand implements Command {

    @Override
    public SlashCommandData definition() {
        return Commands.slash("ping", "Check the bot latency");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        long gatewayPing = event.getJDA().getGatewayPing();
        event.reply("Pong! " + gatewayPing + " ms").queue();
    }
}
