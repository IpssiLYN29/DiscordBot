package fr.ipssi.discordbot.repository;

import fr.ipssi.discordbot.model.SettingKey;

import java.util.Optional;

public interface SettingsRepository {

    Optional<String> find(long guildId, SettingKey key);

    void save(long guildId, SettingKey key, String value);
}
