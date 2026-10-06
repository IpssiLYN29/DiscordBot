package fr.ipssi.discordbot.repository.sqlite;

import fr.ipssi.discordbot.model.Poll;
import fr.ipssi.discordbot.repository.PollRepository;
import fr.ipssi.discordbot.repository.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class SqlitePollRepository implements PollRepository {

    private static final String OPTION_SEPARATOR = "\n";

    private static final String INSERT = """
            INSERT INTO polls (guild_id, channel_id, message_id, question, options, created_by, closes_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String ATTACH_MESSAGE = "UPDATE polls SET channel_id = ?, message_id = ? WHERE id = ?";
    private static final String SELECT_BY_ID = "SELECT * FROM polls WHERE id = ?";
    private static final String SELECT_DUE = """
            SELECT * FROM polls WHERE closed = 0 AND closes_at IS NOT NULL AND closes_at <= ?
            """;
    private static final String CLOSE = "UPDATE polls SET closed = 1 WHERE id = ?";
    private static final String SELECT_VOTE = "SELECT option_index FROM poll_votes WHERE poll_id = ? AND user_id = ?";
    private static final String UPSERT_VOTE = """
            INSERT INTO poll_votes (poll_id, user_id, option_index) VALUES (?, ?, ?)
            ON CONFLICT (poll_id, user_id) DO UPDATE SET option_index = excluded.option_index
            """;
    private static final String DELETE_VOTE = "DELETE FROM poll_votes WHERE poll_id = ? AND user_id = ?";
    private static final String COUNT_VOTES = """
            SELECT option_index, COUNT(*) AS total FROM poll_votes WHERE poll_id = ? GROUP BY option_index
            """;

    private final SqliteDatabase database;

    public SqlitePollRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public long create(Poll poll) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, poll.guildId());
            statement.setLong(2, poll.channelId());
            statement.setLong(3, poll.messageId());
            statement.setString(4, poll.question());
            statement.setString(5, String.join(OPTION_SEPARATOR, poll.options()));
            statement.setLong(6, poll.createdBy());
            if (poll.closesAt() == null) {
                statement.setNull(7, java.sql.Types.INTEGER);
            } else {
                statement.setLong(7, poll.closesAt().toEpochMilli());
            }
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save poll", e);
        }
    }

    @Override
    public void attachMessage(long pollId, long channelId, long messageId) {
        database.execute(ATTACH_MESSAGE, "Failed to attach the poll message", statement -> {
            statement.setLong(1, channelId);
            statement.setLong(2, messageId);
            statement.setLong(3, pollId);
        });
    }

    @Override
    public Optional<Poll> find(long pollId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setLong(1, pollId);
            return readAll(statement).stream().findFirst();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load poll", e);
        }
    }

    @Override
    public List<Poll> findDue(Instant now) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_DUE)) {
            statement.setLong(1, now.toEpochMilli());
            return readAll(statement);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load due polls", e);
        }
    }

    @Override
    public void close(long pollId) {
        database.execute(CLOSE, "Failed to close poll", statement -> statement.setLong(1, pollId));
    }

    @Override
    public Optional<Integer> findVote(long pollId, long userId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_VOTE)) {
            statement.setLong(1, pollId);
            statement.setLong(2, userId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(rows.getInt("option_index")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load vote", e);
        }
    }

    @Override
    public void saveVote(long pollId, long userId, int optionIndex) {
        database.execute(UPSERT_VOTE, "Failed to save vote", statement -> {
            statement.setLong(1, pollId);
            statement.setLong(2, userId);
            statement.setInt(3, optionIndex);
        });
    }

    @Override
    public void removeVote(long pollId, long userId) {
        database.execute(DELETE_VOTE, "Failed to remove vote", statement -> {
            statement.setLong(1, pollId);
            statement.setLong(2, userId);
        });
    }

    @Override
    public List<Integer> countVotes(long pollId, int optionCount) {
        List<Integer> counts = new ArrayList<>(Collections.nCopies(optionCount, 0));
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(COUNT_VOTES)) {
            statement.setLong(1, pollId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int index = rows.getInt("option_index");
                    if (index >= 0 && index < optionCount) {
                        counts.set(index, rows.getInt("total"));
                    }
                }
            }
            return counts;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to count votes", e);
        }
    }

    private static List<Poll> readAll(PreparedStatement statement) throws SQLException {
        List<Poll> polls = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                long closesAtMillis = rows.getLong("closes_at");
                Instant closesAt = rows.wasNull() ? null : Instant.ofEpochMilli(closesAtMillis);
                polls.add(new Poll(
                        rows.getLong("id"),
                        rows.getLong("guild_id"),
                        rows.getLong("channel_id"),
                        rows.getLong("message_id"),
                        rows.getString("question"),
                        List.of(rows.getString("options").split(OPTION_SEPARATOR)),
                        rows.getLong("created_by"),
                        closesAt,
                        rows.getInt("closed") == 1));
            }
        }
        return polls;
    }
}
