package fr.ipssi.discordbot.repository.sqlite;

import fr.ipssi.discordbot.model.Meetup;
import fr.ipssi.discordbot.model.MeetupStatus;
import fr.ipssi.discordbot.model.RsvpStatus;
import fr.ipssi.discordbot.repository.MeetupRepository;
import fr.ipssi.discordbot.repository.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SqliteMeetupRepository implements MeetupRepository {

    private static final String INSERT = """
            INSERT INTO meetups (guild_id, channel_id, message_id, title, description, location, starts_at, created_by, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String ATTACH_MESSAGE = "UPDATE meetups SET channel_id = ?, message_id = ? WHERE id = ?";
    private static final String SELECT_BY_ID = "SELECT * FROM meetups WHERE id = ?";
    private static final String SELECT_UPCOMING = """
            SELECT * FROM meetups WHERE guild_id = ? AND status = 'OPEN' AND starts_at > ? ORDER BY starts_at ASC
            """;
    private static final String SELECT_STARTED = "SELECT * FROM meetups WHERE status = 'OPEN' AND starts_at <= ?";
    private static final String SELECT_PENDING_REMINDERS = """
            SELECT * FROM meetups WHERE status = 'OPEN' AND reminded = 0 AND starts_at > ? AND starts_at <= ?
            """;
    private static final String MARK_REMINDED = "UPDATE meetups SET reminded = 1 WHERE id = ?";
    private static final String UPDATE_STATUS = "UPDATE meetups SET status = ? WHERE id = ?";
    private static final String SELECT_RSVP = "SELECT status FROM meetup_rsvps WHERE meetup_id = ? AND user_id = ?";
    private static final String UPSERT_RSVP = """
            INSERT INTO meetup_rsvps (meetup_id, user_id, status, answered_at) VALUES (?, ?, ?, ?)
            ON CONFLICT (meetup_id, user_id) DO UPDATE SET status = excluded.status, answered_at = excluded.answered_at
            """;
    private static final String SELECT_REMINDER = "SELECT 1 FROM meetup_reminders WHERE meetup_id = ? AND user_id = ?";
    private static final String INSERT_REMINDER = "INSERT OR IGNORE INTO meetup_reminders (meetup_id, user_id) VALUES (?, ?)";
    private static final String DELETE_REMINDER = "DELETE FROM meetup_reminders WHERE meetup_id = ? AND user_id = ?";
    private static final String SELECT_REMINDERS = "SELECT user_id FROM meetup_reminders WHERE meetup_id = ?";
    private static final String DELETE_RSVP = "DELETE FROM meetup_rsvps WHERE meetup_id = ? AND user_id = ?";
    private static final String SELECT_RSVPS = """
            SELECT user_id, status FROM meetup_rsvps WHERE meetup_id = ? ORDER BY answered_at ASC
            """;

    private final SqliteDatabase database;

    public SqliteMeetupRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public long create(Meetup meetup) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, meetup.guildId());
            statement.setLong(2, meetup.channelId());
            statement.setLong(3, meetup.messageId());
            statement.setString(4, meetup.title());
            statement.setString(5, meetup.description());
            statement.setString(6, meetup.location());
            statement.setLong(7, meetup.startsAt().toEpochMilli());
            statement.setLong(8, meetup.createdBy());
            statement.setString(9, meetup.status().name());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save event", e);
        }
    }

    @Override
    public void attachMessage(long meetupId, long channelId, long messageId) {
        database.execute(ATTACH_MESSAGE, "Failed to attach the event message", statement -> {
            statement.setLong(1, channelId);
            statement.setLong(2, messageId);
            statement.setLong(3, meetupId);
        });
    }

    @Override
    public Optional<Meetup> find(long meetupId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setLong(1, meetupId);
            return readAll(statement).stream().findFirst();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load event", e);
        }
    }

    @Override
    public List<Meetup> findUpcoming(long guildId, Instant now) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_UPCOMING)) {
            statement.setLong(1, guildId);
            statement.setLong(2, now.toEpochMilli());
            return readAll(statement);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load upcoming events", e);
        }
    }

    @Override
    public List<Meetup> findStarted(Instant now) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_STARTED)) {
            statement.setLong(1, now.toEpochMilli());
            return readAll(statement);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load started events", e);
        }
    }

    @Override
    public List<Meetup> findPendingReminders(Instant after, Instant until) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_PENDING_REMINDERS)) {
            statement.setLong(1, after.toEpochMilli());
            statement.setLong(2, until.toEpochMilli());
            return readAll(statement);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load pending event reminders", e);
        }
    }

    @Override
    public void markReminded(long meetupId) {
        database.execute(MARK_REMINDED, "Failed to mark the event reminder as sent",
                statement -> statement.setLong(1, meetupId));
    }

    @Override
    public void updateStatus(long meetupId, MeetupStatus status) {
        database.execute(UPDATE_STATUS, "Failed to update event status", statement -> {
            statement.setString(1, status.name());
            statement.setLong(2, meetupId);
        });
    }

    @Override
    public Optional<RsvpStatus> findRsvp(long meetupId, long userId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_RSVP)) {
            statement.setLong(1, meetupId);
            statement.setLong(2, userId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(RsvpStatus.valueOf(rows.getString("status"))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load answer", e);
        }
    }

    @Override
    public void saveRsvp(long meetupId, long userId, RsvpStatus status) {
        database.execute(UPSERT_RSVP, "Failed to save answer", statement -> {
            statement.setLong(1, meetupId);
            statement.setLong(2, userId);
            statement.setString(3, status.name());
            statement.setLong(4, System.currentTimeMillis());
        });
    }

    @Override
    public void removeRsvp(long meetupId, long userId) {
        database.execute(DELETE_RSVP, "Failed to remove answer", statement -> {
            statement.setLong(1, meetupId);
            statement.setLong(2, userId);
        });
    }

    @Override
    public Map<RsvpStatus, List<Long>> findRsvps(long meetupId) {
        Map<RsvpStatus, List<Long>> rsvps = new EnumMap<>(RsvpStatus.class);
        for (RsvpStatus status : RsvpStatus.values()) {
            rsvps.put(status, new ArrayList<>());
        }

        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_RSVPS)) {
            statement.setLong(1, meetupId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    rsvps.get(RsvpStatus.valueOf(rows.getString("status"))).add(rows.getLong("user_id"));
                }
            }
            return rsvps;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load answers", e);
        }
    }

    @Override
    public boolean hasReminder(long meetupId, long userId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_REMINDER)) {
            statement.setLong(1, meetupId);
            statement.setLong(2, userId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load reminder", e);
        }
    }

    @Override
    public void addReminder(long meetupId, long userId) {
        database.execute(INSERT_REMINDER, "Failed to save reminder", statement -> {
            statement.setLong(1, meetupId);
            statement.setLong(2, userId);
        });
    }

    @Override
    public void removeReminder(long meetupId, long userId) {
        database.execute(DELETE_REMINDER, "Failed to remove reminder", statement -> {
            statement.setLong(1, meetupId);
            statement.setLong(2, userId);
        });
    }

    @Override
    public List<Long> findReminders(long meetupId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_REMINDERS)) {
            statement.setLong(1, meetupId);

            List<Long> userIds = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    userIds.add(rows.getLong("user_id"));
                }
            }
            return userIds;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load reminders", e);
        }
    }

    private static List<Meetup> readAll(PreparedStatement statement) throws SQLException {
        List<Meetup> meetups = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                meetups.add(new Meetup(
                        rows.getLong("id"),
                        rows.getLong("guild_id"),
                        rows.getLong("channel_id"),
                        rows.getLong("message_id"),
                        rows.getString("title"),
                        rows.getString("description"),
                        rows.getString("location"),
                        Instant.ofEpochMilli(rows.getLong("starts_at")),
                        rows.getLong("created_by"),
                        MeetupStatus.valueOf(rows.getString("status"))));
            }
        }
        return meetups;
    }
}
