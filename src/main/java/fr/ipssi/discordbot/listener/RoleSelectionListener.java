package fr.ipssi.discordbot.listener;

import fr.ipssi.discordbot.service.RoleMenuService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RoleSelectionListener extends ListenerAdapter {

    public static final String SELECT_MENU_ID = "roles:select";

    private final RoleMenuService roleMenu;

    public RoleSelectionListener(RoleMenuService roleMenu) {
        this.roleMenu = roleMenu;
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!SELECT_MENU_ID.equals(event.getComponentId())) {
            return;
        }

        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            return;
        }

        Set<String> selectedIds = Set.copyOf(event.getValues());
        List<Role> selectableRoles = roleMenu.menuRoleIds(guild.getIdLong()).stream()
                .map(guild::getRoleById)
                .filter(Objects::nonNull)
                .toList();

        List<Role> toAdd = selectableRoles.stream().filter(role -> selectedIds.contains(role.getId())).toList();
        List<Role> toRemove = selectableRoles.stream().filter(role -> !selectedIds.contains(role.getId())).toList();

        guild.modifyMemberRoles(member, toAdd, toRemove).queue(
                success -> event.reply("Tes rôles ont été mis à jour.").setEphemeral(true).queue(),
                error -> event.reply("Impossible de modifier tes rôles, préviens un admin.").setEphemeral(true).queue()
        );
    }
}
