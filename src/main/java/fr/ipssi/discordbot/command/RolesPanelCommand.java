package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.listener.RoleSelectionListener;
import fr.ipssi.discordbot.service.SettingsService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.util.List;
import java.util.Objects;

public final class RolesPanelCommand implements Command {

    private final SettingsService settings;

    public RolesPanelCommand(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("roles-panel", "Post the role selection menu")
                .setGuildOnly(true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        List<SelectOption> options = settings.menuRoleIds(guild.getIdLong()).stream()
                .map(guild::getRoleById)
                .filter(Objects::nonNull)
                .map(role -> SelectOption.of(role.getName(), role.getId()))
                .toList();

        if (options.isEmpty()) {
            event.reply("Aucun rôle sélectionnable n'est configuré (voir /config).").setEphemeral(true).queue();
            return;
        }

        StringSelectMenu menu = StringSelectMenu.create(RoleSelectionListener.SELECT_MENU_ID)
                .setPlaceholder("Choisis ta promo, ta spécialité, ton année...")
                .setRequiredRange(0, options.size())
                .addOptions(options)
                .build();

        event.reply("Sélectionne tes rôles :").addActionRow(menu).queue();
    }
}
