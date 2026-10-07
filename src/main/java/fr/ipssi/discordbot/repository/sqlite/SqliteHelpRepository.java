package fr.ipssi.discordbot.repository.sqlite;

import fr.ipssi.discordbot.model.HelpQuestion;
import fr.ipssi.discordbot.repository.HelpRepository;
import fr.ipssi.discordbot.repository.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class SqliteHelpRepository implements HelpRepository {

    private static final String INSERT = "INSERT INTO help_questions (thread_id, guild_id, asker_id) VALUES (?, ?, ?)";
    private static final String SELECT = "SELECT * FROM help_questions WHERE thread_id = ?";
    private static final String MARK_RESOLVED = "UPDATE help_questions SET resolved = 1 WHERE thread_id = ?";

    private final SqliteDatabase database;

    public SqliteHelpRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public void save(HelpQuestion question) {
        database.execute(INSERT, "Failed to save question", statement -> {
            statement.setLong(1, question.threadId());
            statement.setLong(2, question.guildId());
            statement.setLong(3, question.askerId());
        });
    }

    @Override
    public Optional<HelpQuestion> find(long threadId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setLong(1, threadId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new HelpQuestion(
                        rows.getLong("thread_id"),
                        rows.getLong("guild_id"),
                        rows.getLong("asker_id"),
                        rows.getInt("resolved") == 1));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load question", e);
        }
    }

    @Override
    public void markResolved(long threadId) {
        database.execute(MARK_RESOLVED, "Failed to mark question as resolved",
                statement -> statement.setLong(1, threadId));
    }
}
