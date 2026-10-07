package fr.ipssi.discordbot.service;

import fr.ipssi.discordbot.model.Promo;
import fr.ipssi.discordbot.model.Subject;
import fr.ipssi.discordbot.repository.PromoRepository;
import fr.ipssi.discordbot.repository.SubjectRepository;

import java.util.LinkedHashSet;
import java.util.Set;

public final class RoleMenuService {

    private final SettingsService settings;
    private final PromoRepository promoRepository;
    private final SubjectRepository subjectRepository;

    public RoleMenuService(
            SettingsService settings, PromoRepository promoRepository, SubjectRepository subjectRepository) {
        this.settings = settings;
        this.promoRepository = promoRepository;
        this.subjectRepository = subjectRepository;
    }

    public Set<Long> menuRoleIds(long guildId) {
        Set<Long> roleIds = new LinkedHashSet<>(settings.selectableRoleIds(guildId));
        settings.planningRoleId(guildId).ifPresent(roleIds::add);
        promoRepository.findAll(guildId).stream().map(Promo::roleId).forEach(roleIds::add);
        subjectRepository.findAll(guildId).stream().map(Subject::roleId).forEach(roleIds::add);
        return roleIds;
    }
}
