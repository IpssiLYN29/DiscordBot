package fr.ipssi.discordbot.repository.sqlite;

import fr.ipssi.discordbot.model.ScheduleEvent;
import fr.ipssi.discordbot.repository.RepositoryException;
import fr.ipssi.discordbot.repository.ScheduleRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class SqliteScheduleRepository implements ScheduleRepository {

    private static final String PROMO_SEPARATOR = ",";

    private static final String SELECT_UIDS = "SELECT uid FROM schedule_events WHERE guild_id = ?";
    private static final String UPSERT = """
            INSERT INTO schedule_events (guild_id, uid, title, teacher, location, start_at, end_at, promos)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (guild_id, uid) DO UPDATE SET
                reminded = CASE WHEN start_at <> excluded.start_at THEN 0 ELSE reminded END,
                title = excluded.title,
                teacher = excluded.teacher,
                location = excluded.location,
                start_at = excluded.start_at,
                end_at = excluded.end_at,
                promos = excluded.promos
            """;
    private static final String DELETE = "DELETE FROM schedule_events WHERE guild_id = ? AND uid = ?";
    private static final String SELECT_PENDING = """
            SELECT * FROM schedule_events WHERE reminded = 0 AND start_at > ? AND start_at <= ? ORDER BY start_at ASC
            """;
    private static final String SELECT_BETWEEN = """
            SELECT * FROM schedule_events WHERE guild_id = ? AND start_at >= ? AND start_at < ? ORDER BY start_at ASC
            """;
    private static final String SELECT_ALL = "SELECT * FROM schedule_events WHERE guild_id = ? ORDER BY start_at ASC";
    private static final String MARK_REMINDED = "UPDATE schedule_events SET reminded = 1 WHERE guild_id = ? AND uid = ?";

    private final SqliteDatabase database;

    public SqliteScheduleRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public void replaceAll(long guildId, List<ScheduleEvent> events) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                replace(connection, guildId, events);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to import the schedule", e);
        }
    }

    @Override
    public List<ScheduleEvent> findPendingReminders(Instant after, Instant until) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_PENDING)) {
            statement.setLong(1, after.toEpochMilli());
            statement.setLong(2, until.toEpochMilli());
            return readAll(statement);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load pending reminders", e);
        }
    }

    @Override
    public List<ScheduleEvent> findStartingBetween(long guildId, Instant from, Instant until) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_BETWEEN)) {
            statement.setLong(1, guildId);
            statement.setLong(2, from.toEpochMilli());
            statement.setLong(3, until.toEpochMilli());
            return readAll(statement);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load the schedule", e);
        }
    }

    @Override
    public List<ScheduleEvent> findAll(long guildId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
            statement.setLong(1, guildId);
            return readAll(statement);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load the schedule", e);
        }
    }

    @Override
    public void markReminded(long guildId, String uid) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(MARK_REMINDED)) {
            statement.setLong(1, guildId);
            statement.setString(2, uid);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to mark the reminder as sent", e);
        }
    }

    private static void replace(Connection connection, long guildId, List<ScheduleEvent> events) throws SQLException {
        Set<String> staleUids = existingUids(connection, guildId);

        try (PreparedStatement upsert = connection.prepareStatement(UPSERT)) {
            for (ScheduleEvent event : events) {
                upsert.setLong(1, guildId);
                upsert.setString(2, event.uid());
                upsert.setString(3, event.title());
                upsert.setString(4, event.teacher());
                upsert.setString(5, event.location());
                upsert.setLong(6, event.startsAt().toEpochMilli());
                upsert.setLong(7, event.endsAt().toEpochMilli());
                upsert.setString(8, encodePromos(event.promos()));
                upsert.addBatch();
                staleUids.remove(event.uid());
            }
            upsert.executeBatch();
        }

        try (PreparedStatement delete = connection.prepareStatement(DELETE)) {
            for (String uid : staleUids) {
                delete.setLong(1, guildId);
                delete.setString(2, uid);
                delete.addBatch();
            }
            delete.executeBatch();
        }
    }

    private static Set<String> existingUids(Connection connection, long guildId) throws SQLException {
        Set<String> uids = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_UIDS)) {
            statement.setLong(1, guildId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    uids.add(rows.getString("uid"));
                }
            }
        }
        return uids;
    }

    private static List<ScheduleEvent> readAll(PreparedStatement statement) throws SQLException {
        List<ScheduleEvent> events = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                events.add(new ScheduleEvent(
                        rows.getLong("guild_id"),
                        rows.getString("uid"),
                        rows.getString("title"),
                        rows.getString("teacher"),
                        rows.getString("location"),
                        Instant.ofEpochMilli(rows.getLong("start_at")),
                        Instant.ofEpochMilli(rows.getLong("end_at")),
                        decodePromos(rows.getString("promos"))));
            }
        }
        return events;
    }

    private static String encodePromos(Set<String> promos) {
        return promos.isEmpty() ? "" : PROMO_SEPARATOR + String.join(PROMO_SEPARATOR, promos) + PROMO_SEPARATOR;
    }

    private static Set<String> decodePromos(String raw) {
        return Arrays.stream(raw.split(PROMO_SEPARATOR))
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
