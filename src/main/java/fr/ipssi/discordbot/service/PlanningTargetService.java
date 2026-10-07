package fr.ipssi.discordbot.service;

import fr.ipssi.discordbot.model.Promo;
import fr.ipssi.discordbot.repository.PromoRepository;

import java.util.List;

public final class PlanningTargetService {

    private final PromoRepository promoRepository;
    private final SettingsService settings;

    public PlanningTargetService(PromoRepository promoRepository, SettingsService settings) {
        this.promoRepository = promoRepository;
        this.settings = settings;
    }

    public List<PlanningTarget> targets(long guildId) {
        List<Promo> promos = promoRepository.findAll(guildId);
        if (!promos.isEmpty()) {
            return promos.stream()
                    .map(promo -> new PlanningTarget(promo.code().toUpperCase(), promo.channelId(), promo.roleId()))
                    .toList();
        }

        return settings.planningChannelId(guildId)
                .map(channelId -> new PlanningTarget(
                        PlanningTarget.ALL_PROMOS, channelId, settings.planningRoleId(guildId).orElse(0L)))
                .stream()
                .toList();
    }
}
