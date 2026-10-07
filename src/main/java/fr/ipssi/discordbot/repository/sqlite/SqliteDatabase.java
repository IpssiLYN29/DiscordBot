package fr.ipssi.discordbot.repository.sqlite;

import fr.ipssi.discordbot.repository.RepositoryException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class SqliteDatabase {

    private static final List<String> SCHEMA = List.of("""
            CREATE TABLE IF NOT EXISTS warns (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guild_id INTEGER NOT NULL,
                user_id INTEGER NOT NULL,
                moderator_id INTEGER NOT NULL,
                reason TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """, """
            CREATE TABLE IF NOT EXISTS settings (
                guild_id INTEGER NOT NULL,
                key TEXT NOT NULL,
                value TEXT NOT NULL,
                PRIMARY KEY (guild_id, key)
            )
            """, """
            CREATE TABLE IF NOT EXISTS deadlines (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guild_id INTEGER NOT NULL,
                subject TEXT NOT NULL,
                description TEXT NOT NULL,
                due_at INTEGER NOT NULL,
                created_by INTEGER NOT NULL,
                last_reminder_days INTEGER NOT NULL
            )
            """, """
            CREATE TABLE IF NOT EXISTS schedule_events (
                guild_id INTEGER NOT NULL,
                uid TEXT NOT NULL,
                title TEXT NOT NULL,
                teacher TEXT NOT NULL,
                location TEXT NOT NULL,
                start_at INTEGER NOT NULL,
                end_at INTEGER NOT NULL,
                reminded INTEGER NOT NULL DEFAULT 0,
                promos TEXT NOT NULL DEFAULT '',
                PRIMARY KEY (guild_id, uid)
            )
            """, """
            CREATE TABLE IF NOT EXISTS polls (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guild_id INTEGER NOT NULL,
                channel_id INTEGER NOT NULL,
                message_id INTEGER NOT NULL,
                question TEXT NOT NULL,
                options TEXT NOT NULL,
                created_by INTEGER NOT NULL,
                closes_at INTEGER,
                closed INTEGER NOT NULL DEFAULT 0
            )
            """, """
            CREATE TABLE IF NOT EXISTS poll_votes (
                poll_id INTEGER NOT NULL,
                user_id INTEGER NOT NULL,
                option_index INTEGER NOT NULL,
                PRIMARY KEY (poll_id, user_id)
            )
            """, """
            CREATE TABLE IF NOT EXISTS meetups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guild_id INTEGER NOT NULL,
                channel_id INTEGER NOT NULL,
                message_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                location TEXT NOT NULL,
                starts_at INTEGER NOT NULL,
                created_by INTEGER NOT NULL,
                status TEXT NOT NULL
            )
            """, """
            CREATE TABLE IF NOT EXISTS meetup_rsvps (
                meetup_id INTEGER NOT NULL,
                user_id INTEGER NOT NULL,
                status TEXT NOT NULL,
                answered_at INTEGER NOT NULL,
                PRIMARY KEY (meetup_id, user_id)
            )
            """, """
            CREATE TABLE IF NOT EXISTS resources (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guild_id INTEGER NOT NULL,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                tags TEXT NOT NULL,
                created_by INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """, """
            CREATE TABLE IF NOT EXISTS promos (
                guild_id INTEGER NOT NULL,
                code TEXT NOT NULL COLLATE NOCASE,
                channel_id INTEGER NOT NULL,
                role_id INTEGER NOT NULL,
                PRIMARY KEY (guild_id, code)
            )
            """, """
            CREATE TABLE IF NOT EXISTS subjects (
                guild_id INTEGER NOT NULL,
                name TEXT NOT NULL COLLATE NOCASE,
                role_id INTEGER NOT NULL,
                PRIMARY KEY (guild_id, name)
            )
            """, """
            CREATE TABLE IF NOT EXISTS help_questions (
                thread_id INTEGER PRIMARY KEY,
                guild_id INTEGER NOT NULL,
                asker_id INTEGER NOT NULL,
                resolved INTEGER NOT NULL DEFAULT 0
            )
            """, """
            CREATE TABLE IF NOT EXISTS meetup_reminders (
                meetup_id INTEGER NOT NULL,
                user_id INTEGER NOT NULL,
                PRIMARY KEY (meetup_id, user_id)
            )
            """);

    private final String url;

    public SqliteDatabase(Path file) {
        this.url = "jdbc:sqlite:" + file;
        createParentDirectory(file);
        initSchema();
    }

    @FunctionalInterface
    interface StatementBinder {

        void bind(PreparedStatement statement) throws SQLException;
    }

    Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    void execute(String sql, String errorMessage, StatementBinder binder) {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException(errorMessage, e);
        }
    }

    private void initSchema() {
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            for (String definition : SCHEMA) {
                statement.execute(definition);
            }
            addColumnIfMissing(statement, "meetups", "reminded", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(statement, "schedule_events", "promos", "TEXT NOT NULL DEFAULT ''");
        } catch (SQLException e) {
            throw new RepositoryException("Failed to initialize the database", e);
        }
    }

    private static void addColumnIfMissing(Statement statement, String table, String column, String definition)
            throws SQLException {
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (columns.next()) {
                if (column.equals(columns.getString("name"))) {
                    return;
                }
            }
        }
        statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private static void createParentDirectory(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new RepositoryException("Failed to create the database directory", e);
        }
    }
}
