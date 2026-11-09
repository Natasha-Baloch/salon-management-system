package com.salon;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads database settings WITHOUT hardcoding them in source code.
 * Order of precedence: environment variable (SALON_DB_HOST, SALON_DB_PORT, SALON_DB_NAME,
 * SALON_DB_USER, SALON_DB_PASSWORD, SALON_DB_URL) -> db.properties file -> defaults.
 * The file is db.properties in the project folder, or the path in env SALON_CONFIG.
 */
public final class Config {
    private static final Properties FILE = new Properties();

    static {
        String custom = System.getenv("SALON_CONFIG");
        Path path = Path.of(custom != null && !custom.isBlank() ? custom : "db.properties");
        if (Files.isReadable(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                FILE.load(in);
            } catch (IOException e) {
                System.err.println("Could not read " + path + ": " + e.getMessage());
            }
        }
    }

    private Config() {}

    /** key like "db.host" -> env SALON_DB_HOST */
    public static String get(String key, String def) {
        String env = System.getenv("SALON_" + key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isEmpty()) return env;
        String v = FILE.getProperty(key);
        return v != null && !v.trim().isEmpty() ? v.trim() : def;
    }

    /** Password is read raw (not trimmed) so spaces are preserved. */
    public static String password() {
        String env = System.getenv("SALON_DB_PASSWORD");
        if (env != null) return env;
        return FILE.getProperty("db.password", "");
    }
}
