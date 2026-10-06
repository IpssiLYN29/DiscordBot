package fr.ipssi.discordbot.repository.sqlite;

import fr.ipssi.discordbot.model.Deadline;
import fr.ipssi.discordbot.repository.DeadlineRepository;
import fr.ipssi.discordbot.repository.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SqliteDeadlineRepository implements DeadlineRepository {

    private static final String INSERT = """
            INSERT INTO deadlines (guild_id, subject, description, due_at, created_by, last_reminder_days)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String DELETE = "DELETE FROM deadlines WHERE guild_id = ? AND id = ?";
    private static final String SELECT_UPCOMING_BY_GUILD = """
            SELECT * FROM deadlines WHERE guild_id = ? AND due_at > ? ORDER BY due_at ASC
            """;
    private static final String SELECT_ALL_UPCOMING = "SELECT * FROM deadlines WHERE due_at > ? ORDER BY due_at ASC";
    private static final String UPDATE_REMINDER = "UPDATE deadlines SET last_reminder_days = ? WHERE id = ?";

    private final SqliteDatabase database;

    public SqliteDeadlineRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public long add(Deadline deadline) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, deadline.guildId());
            statement.setString(2, deadline.subject());
            statement.setString(3, deadline.description());
            statement.setLong(4, deadline.dueAt().toEpochMilli());
            statement.setLong(5, deadline.createdBy());
            statement.setInt(6, deadline.lastReminderDays());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save deadline", e);
        }
    }

    @Override
    public boolean remove(long guildId, long id) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setLong(1, guildId);
            statement.setLong(2, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete deadline", e);
        }
    }

    @Override
    public List<Deadline> findUpcoming(long guildId, Instant now) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_UPCOMING_BY_GUILD)) {
            statement.setLong(1, guildId);
            statement.setLong(2, now.toEpochMilli());
            return readAll(statement);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load deadlines", e);
        }
    }

    @Override
    public List<Deadline> findAllUpcoming(Instant now) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL_UPCOMING)) {
            statement.setLong(1, now.toEpochMilli());
            return readAll(statement);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load deadlines", e);
        }
    }

    @Override
    public void updateLastReminder(long id, int days) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(UPDATE_REMINDER)) {
            statement.setInt(1, days);
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to update deadline reminder", e);
        }
    }

    private static List<Deadline> readAll(PreparedStatement statement) throws SQLException {
        List<Deadline> deadlines = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                deadlines.add(new Deadline(
                        rows.getLong("id"),
                        rows.getLong("guild_id"),
                        rows.getString("subject"),
                        rows.getString("description"),
                        Instant.ofEpochMilli(rows.getLong("due_at")),
                        rows.getLong("created_by"),
                        rows.getInt("last_reminder_days")));
            }
        }
        return deadlines;
    }
}
