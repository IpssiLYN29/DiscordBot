package fr.ipssi.discordbot;

import java.nio.file.Path;

public record Config(
        String token,
        Path databaseFile,
        boolean devMode,
        long delegueRoleId,
        long promoLynRoleId,
        long promoBdxRoleId) {

    private static final String DEFAULT_DATABASE_FILE = "data/bot.db";
    private static final long DEFAULT_DELEGUE_ROLE_ID = 1558036770782847007L;
    private static final long DEFAULT_PROMO_LYN_ROLE_ID = 1556707717601697863L;
    private static final long DEFAULT_PROMO_BDX_ROLE_ID = 1557018331842617376L;

    public static Config load() {
        String token = System.getenv("DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Missing environment variable: DISCORD_TOKEN");
        }
        return new Config(
                token.trim(),
                Path.of(System.getenv().getOrDefault("DATABASE_FILE", DEFAULT_DATABASE_FILE)),
                "1".equals(System.getenv("DEV_MODE")),
                roleId("DELEGUE_ROLE_ID", DEFAULT_DELEGUE_ROLE_ID),
                roleId("PROMO_LYN_ROLE_ID", DEFAULT_PROMO_LYN_ROLE_ID),
                roleId("PROMO_BDX_ROLE_ID", DEFAULT_PROMO_BDX_ROLE_ID));
    }

    private static long roleId(String variable, long defaultValue) {
        String value = System.getenv(variable);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            long roleId = Long.parseLong(value.trim());
            if (roleId > 0) {
                return roleId;
            }
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid environment variable: " + variable + " must be a numeric Discord role ID", e);
        }
        throw new IllegalStateException("Invalid environment variable: " + variable + " must be a positive Discord role ID");
    }
}
