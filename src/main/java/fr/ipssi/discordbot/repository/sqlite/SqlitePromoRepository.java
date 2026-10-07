package fr.ipssi.discordbot.repository.sqlite;

import fr.ipssi.discordbot.model.Promo;
import fr.ipssi.discordbot.repository.PromoRepository;
import fr.ipssi.discordbot.repository.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class SqlitePromoRepository implements PromoRepository {

    private static final String UPSERT = """
            INSERT INTO promos (guild_id, code, channel_id, role_id) VALUES (?, ?, ?, ?)
            ON CONFLICT (guild_id, code) DO UPDATE SET channel_id = excluded.channel_id, role_id = excluded.role_id
            """;
    private static final String DELETE = "DELETE FROM promos WHERE guild_id = ? AND code = ?";
    private static final String SELECT_ALL = "SELECT * FROM promos WHERE guild_id = ? ORDER BY code ASC";

    private final SqliteDatabase database;

    public SqlitePromoRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public void save(Promo promo) {
        database.execute(UPSERT, "Failed to save promo", statement -> {
            statement.setLong(1, promo.guildId());
            statement.setString(2, promo.code());
            statement.setLong(3, promo.channelId());
            statement.setLong(4, promo.roleId());
        });
    }

    @Override
    public boolean remove(long guildId, String code) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setLong(1, guildId);
            statement.setString(2, code);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete promo", e);
        }
    }

    @Override
    public List<Promo> findAll(long guildId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
            statement.setLong(1, guildId);

            List<Promo> promos = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    promos.add(new Promo(
                            rows.getLong("guild_id"),
                            rows.getString("code"),
                            rows.getLong("channel_id"),
                            rows.getLong("role_id")));
                }
            }
            return promos;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load promos", e);
        }
    }
}
