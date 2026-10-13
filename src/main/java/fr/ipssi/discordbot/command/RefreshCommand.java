package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.Main;
import fr.ipssi.discordbot.model.HelpQuestion;
import fr.ipssi.discordbot.model.Subject;
import fr.ipssi.discordbot.repository.HelpRepository;
import fr.ipssi.discordbot.repository.SubjectRepository;
import fr.ipssi.discordbot.service.SettingsService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class RefreshCommand implements Command {

    public RefreshCommand() {
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("refresh", "Refresh all bot needed things")
                .setGuildOnly(true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member author = event.getMember();
        if (!author.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("Vous n'avez pas la permission pour faire cette commande.").setEphemeral(true).queue();
            return;
        }


        Main.getCommandManager().publish(Main.getJda());
        event.reply("Les commandes ont bien été refresh !").setEphemeral(true).queue();
    }
}
