package fr.ipssi.discordbot;

import java.nio.file.Path;

public record Config(String token, Path databaseFile, boolean devMode) {

    private static final String DEFAULT_DATABASE_FILE = "data/bot.db";

    public static Config load() {
        String token = System.getenv("DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Missing environment variable: DISCORD_TOKEN");
        }
        return new Config(
                token.trim(),
                Path.of(System.getenv().getOrDefault("DATABASE_FILE", DEFAULT_DATABASE_FILE)),
                "1".equals(System.getenv("DEV_MODE")));
    }
}
