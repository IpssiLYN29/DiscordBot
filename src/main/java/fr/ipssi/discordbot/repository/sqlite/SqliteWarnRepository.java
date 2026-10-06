package fr.ipssi.discordbot.repository.sqlite;

import fr.ipssi.discordbot.model.Warn;
import fr.ipssi.discordbot.repository.RepositoryException;
import fr.ipssi.discordbot.repository.WarnRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SqliteWarnRepository implements WarnRepository {

    private static final String INSERT = """
            INSERT INTO warns (guild_id, user_id, moderator_id, reason, created_at) VALUES (?, ?, ?, ?, ?)
            """;
    private static final String SELECT_BY_MEMBER = """
            SELECT guild_id, user_id, moderator_id, reason, created_at
            FROM warns WHERE guild_id = ? AND user_id = ? ORDER BY created_at DESC
            """;

    private final SqliteDatabase database;

    public SqliteWarnRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public void add(Warn warn) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setLong(1, warn.guildId());
            statement.setLong(2, warn.userId());
            statement.setLong(3, warn.moderatorId());
            statement.setString(4, warn.reason());
            statement.setLong(5, warn.createdAt().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save warn", e);
        }
    }

    @Override
    public List<Warn> findByMember(long guildId, long userId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_MEMBER)) {
            statement.setLong(1, guildId);
            statement.setLong(2, userId);

            List<Warn> warns = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    warns.add(new Warn(
                            rows.getLong("guild_id"),
                            rows.getLong("user_id"),
                            rows.getLong("moderator_id"),
                            rows.getString("reason"),
                            Instant.ofEpochMilli(rows.getLong("created_at"))));
                }
            }
            return warns;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load warns", e);
        }
    }
}
