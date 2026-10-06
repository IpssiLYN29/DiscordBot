package fr.ipssi.discordbot.repository.sqlite;

import fr.ipssi.discordbot.model.SettingKey;
import fr.ipssi.discordbot.repository.RepositoryException;
import fr.ipssi.discordbot.repository.SettingsRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class SqliteSettingsRepository implements SettingsRepository {

    private static final String SELECT = "SELECT value FROM settings WHERE guild_id = ? AND key = ?";
    private static final String UPSERT = """
            INSERT INTO settings (guild_id, key, value) VALUES (?, ?, ?)
            ON CONFLICT (guild_id, key) DO UPDATE SET value = excluded.value
            """;

    private final SqliteDatabase database;

    public SqliteSettingsRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public Optional<String> find(long guildId, SettingKey key) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setLong(1, guildId);
            statement.setString(2, key.name());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(rows.getString("value")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load setting " + key, e);
        }
    }

    @Override
    public void save(long guildId, SettingKey key, String value) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setLong(1, guildId);
            statement.setString(2, key.name());
            statement.setString(3, value);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save setting " + key, e);
        }
    }
}
