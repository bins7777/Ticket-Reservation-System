package movieticketreservation.db;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class DatabaseConfig {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final String DEFAULT_PORT = "3306";
    private static final String DEFAULT_DB   = "movieticketreservationdb";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    private static final Properties props = new Properties();

    static {
        // Load optional properties from classpath resource db.properties
        try (InputStream in = DatabaseConfig.class.getClassLoader().getResourceAsStream("movieticketreservation/db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
        }
    }

    public static String getHost() {
        String env = System.getenv("DB_HOST");
        if (env != null && !env.isEmpty()) return env;
        return props.getProperty("db.host", DEFAULT_HOST);
    }

    public static String getPort() {
        String env = System.getenv("DB_PORT");
        if (env != null && !env.isEmpty()) return env;
        return props.getProperty("db.port", DEFAULT_PORT);
    }

    public static String getDatabase() {
        String env = System.getenv("DB_NAME");
        if (env != null && !env.isEmpty()) return env;
        return props.getProperty("db.name", DEFAULT_DB);
    }

    public static String getUser() {
        String env = System.getenv("DB_USER");
        if (env != null && !env.isEmpty()) return env;
        return props.getProperty("db.user", DEFAULT_USER);
    }

    public static String getPassword() {
        // Prefer environment variable if present, fallback to properties
        String env = System.getenv("DB_PASSWORD");
        if (env != null && !env.isEmpty()) return env;
        return props.getProperty("db.password", DEFAULT_PASSWORD);
    }
}
