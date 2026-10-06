package fr.ipssi.discordbot.repository.sqlite;

import fr.ipssi.discordbot.model.Resource;
import fr.ipssi.discordbot.repository.RepositoryException;
import fr.ipssi.discordbot.repository.ResourceRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class SqliteResourceRepository implements ResourceRepository {

    private static final String TAG_SEPARATOR = ",";

    private static final String INSERT = """
            INSERT INTO resources (guild_id, url, title, description, tags, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_BY_ID = "SELECT * FROM resources WHERE id = ?";
    private static final String SELECT_BY_URL = "SELECT * FROM resources WHERE guild_id = ? AND url = ?";
    private static final String DELETE = "DELETE FROM resources WHERE guild_id = ? AND id = ?";

    private final SqliteDatabase database;

    public SqliteResourceRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public long add(Resource resource) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, resource.guildId());
            statement.setString(2, resource.url());
            statement.setString(3, resource.title());
            statement.setString(4, resource.description());
            statement.setString(5, encodeTags(resource.tags()));
            statement.setLong(6, resource.createdBy());
            statement.setLong(7, resource.createdAt().toEpochMilli());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save resource", e);
        }
    }

    @Override
    public Optional<Resource> find(long resourceId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setLong(1, resourceId);
            return readAll(statement).stream().findFirst();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load resource", e);
        }
    }

    @Override
    public Optional<Resource> findByUrl(long guildId, String url) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_URL)) {
            statement.setLong(1, guildId);
            statement.setString(2, url);
            return readAll(statement).stream().findFirst();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load resource", e);
        }
    }

    @Override
    public List<Resource> search(long guildId, String query, String tag, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM resources WHERE guild_id = ?");
        List<Object> parameters = new ArrayList<>(List.of(guildId));

        if (!tag.isEmpty()) {
            sql.append(" AND tags LIKE ? ESCAPE '\\'");
            parameters.add("%" + TAG_SEPARATOR + escapeLike(tag) + TAG_SEPARATOR + "%");
        }
        if (!query.isEmpty()) {
            sql.append(" AND (title LIKE ? ESCAPE '\\' OR description LIKE ? ESCAPE '\\'")
                    .append(" OR url LIKE ? ESCAPE '\\' OR tags LIKE ? ESCAPE '\\')");
            String pattern = "%" + escapeLike(query) + "%";
            parameters.addAll(List.of(pattern, pattern, pattern, pattern));
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        parameters.add(limit);

        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i));
            }
            return readAll(statement);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to search resources", e);
        }
    }

    @Override
    public boolean remove(long guildId, long resourceId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setLong(1, guildId);
            statement.setLong(2, resourceId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete resource", e);
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String encodeTags(List<String> tags) {
        return TAG_SEPARATOR + String.join(TAG_SEPARATOR, tags) + TAG_SEPARATOR;
    }

    private static List<String> decodeTags(String raw) {
        return Arrays.stream(raw.split(TAG_SEPARATOR)).filter(tag -> !tag.isEmpty()).toList();
    }

    private static List<Resource> readAll(PreparedStatement statement) throws SQLException {
        List<Resource> resources = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                resources.add(new Resource(
                        rows.getLong("id"),
                        rows.getLong("guild_id"),
                        rows.getString("url"),
                        rows.getString("title"),
                        rows.getString("description"),
                        decodeTags(rows.getString("tags")),
                        rows.getLong("created_by"),
                        Instant.ofEpochMilli(rows.getLong("created_at"))));
            }
        }
        return resources;
    }
}
