package fr.ipssi.discordbot.repository.sqlite;

import fr.ipssi.discordbot.model.Subject;
import fr.ipssi.discordbot.repository.RepositoryException;
import fr.ipssi.discordbot.repository.SubjectRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteSubjectRepository implements SubjectRepository {

    private static final String UPSERT = """
            INSERT INTO subjects (guild_id, name, role_id) VALUES (?, ?, ?)
            ON CONFLICT (guild_id, name) DO UPDATE SET role_id = excluded.role_id
            """;
    private static final String DELETE = "DELETE FROM subjects WHERE guild_id = ? AND name = ?";
    private static final String SELECT_ONE = "SELECT * FROM subjects WHERE guild_id = ? AND name = ?";
    private static final String SELECT_ALL = "SELECT * FROM subjects WHERE guild_id = ? ORDER BY name ASC";

    private final SqliteDatabase database;

    public SqliteSubjectRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public void save(Subject subject) {
        database.execute(UPSERT, "Failed to save subject", statement -> {
            statement.setLong(1, subject.guildId());
            statement.setString(2, subject.name());
            statement.setLong(3, subject.roleId());
        });
    }

    @Override
    public boolean remove(long guildId, String name) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setLong(1, guildId);
            statement.setString(2, name);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete subject", e);
        }
    }

    @Override
    public Optional<Subject> find(long guildId, String name) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
            statement.setLong(1, guildId);
            statement.setString(2, name);
            return readAll(statement).stream().findFirst();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load subject", e);
        }
    }

    @Override
    public List<Subject> findAll(long guildId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL)) {
            statement.setLong(1, guildId);
            return readAll(statement);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load subjects", e);
        }
    }

    private static List<Subject> readAll(PreparedStatement statement) throws SQLException {
        List<Subject> subjects = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                subjects.add(new Subject(rows.getLong("guild_id"), rows.getString("name"), rows.getLong("role_id")));
            }
        }
        return subjects;
    }
}
