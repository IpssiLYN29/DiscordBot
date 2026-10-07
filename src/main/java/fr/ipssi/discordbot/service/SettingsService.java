package fr.ipssi.discordbot.service;

import fr.ipssi.discordbot.model.SettingKey;
import fr.ipssi.discordbot.repository.SettingsRepository;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class SettingsService {

    private static final String SEPARATOR = ",";

    private final SettingsRepository repository;

    public SettingsService(SettingsRepository repository) {
        this.repository = repository;
    }

    public Optional<Long> welcomeChannelId(long guildId) {
        return findId(guildId, SettingKey.WELCOME_CHANNEL);
    }

    public Optional<Long> logChannelId(long guildId) {
        return findId(guildId, SettingKey.LOG_CHANNEL);
    }

    public Optional<Long> deadlineChannelId(long guildId) {
        return findId(guildId, SettingKey.DEADLINE_CHANNEL);
    }

    public Optional<Long> helpChannelId(long guildId) {
        return findId(guildId, SettingKey.HELP_CHANNEL);
    }

    public Optional<Long> planningChannelId(long guildId) {
        return findId(guildId, SettingKey.PLANNING_CHANNEL);
    }

    public Optional<Long> planningRoleId(long guildId) {
        return findId(guildId, SettingKey.PLANNING_ROLE);
    }

    public Optional<Long> memberRoleId(long guildId) {
        return findId(guildId, SettingKey.MEMBER_ROLE);
    }

    public Set<Long> selectableRoleIds(long guildId) {
        return repository.find(guildId, SettingKey.SELECTABLE_ROLES)
                .filter(raw -> !raw.isBlank())
                .map(raw -> Arrays.stream(raw.split(SEPARATOR))
                        .map(Long::parseLong)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElseGet(LinkedHashSet::new);
    }

    public void setId(long guildId, SettingKey key, long id) {
        repository.save(guildId, key, Long.toString(id));
    }

    public void addSelectableRole(long guildId, long roleId) {
        Set<Long> roleIds = selectableRoleIds(guildId);
        roleIds.add(roleId);
        saveSelectableRoles(guildId, roleIds);
    }

    public void removeSelectableRole(long guildId, long roleId) {
        Set<Long> roleIds = selectableRoleIds(guildId);
        roleIds.remove(roleId);
        saveSelectableRoles(guildId, roleIds);
    }

    private Optional<Long> findId(long guildId, SettingKey key) {
        return repository.find(guildId, key).map(Long::parseLong);
    }

    private void saveSelectableRoles(long guildId, Set<Long> roleIds) {
        String raw = roleIds.stream().map(String::valueOf).collect(Collectors.joining(SEPARATOR));
        repository.save(guildId, SettingKey.SELECTABLE_ROLES, raw);
    }
}
