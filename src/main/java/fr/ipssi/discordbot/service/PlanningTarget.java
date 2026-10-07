package fr.ipssi.discordbot.service;

import fr.ipssi.discordbot.model.ScheduleEvent;

public record PlanningTarget(String promoCode, long channelId, long roleId) {

    public static final String ALL_PROMOS = "";

    private static final long NO_ROLE = 0;

    public boolean matches(ScheduleEvent event) {
        return promoCode.equals(ALL_PROMOS) || event.promos().isEmpty() || event.promos().contains(promoCode);
    }

    public String roleMention() {
        return roleId == NO_ROLE ? "" : "<@&" + roleId + ">";
    }

    public String titleSuffix() {
        return promoCode.equals(ALL_PROMOS) ? "" : " · " + promoCode;
    }
}
