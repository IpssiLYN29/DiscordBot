package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.listener.RulesListener;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class RulesPanelCommand implements Command {

    private static final String RULES_RESOURCE = "/rules.txt";
    private static final Color RULES_COLOR = new Color(0xED4245);

    private final String rules = loadRules();

    @Override
    public SlashCommandData definition() {
        return Commands.slash("rules-panel", "Post the rules with the acceptance button")
                .setGuildOnly(true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Règlement du serveur")
                .setDescription(rules)
                .setColor(RULES_COLOR);

        event.replyEmbeds(embed.build())
                .addActionRow(Button.success(RulesListener.ACCEPT_BUTTON_ID, "J'accepte le règlement"))
                .queue();
    }

    private static String loadRules() {
        try (InputStream stream = RulesPanelCommand.class.getResourceAsStream(RULES_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing resource: " + RULES_RESOURCE);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
