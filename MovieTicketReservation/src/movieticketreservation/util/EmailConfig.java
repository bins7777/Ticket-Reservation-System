package movieticketreservation.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads SMTP configuration from classpath resource
 * /movieticketreservation/email.properties or environment variables.
 */
public final class EmailConfig {

    private static final EmailConfig INSTANCE = load();

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromAddress;

    private EmailConfig(String host,
                        int port,
                        String username,
                        String password,
                        String fromAddress) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress == null || fromAddress.isBlank()
                ? username
                : fromAddress;
    }

    public static EmailConfig getInstance() {
        return INSTANCE;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    private static EmailConfig load() {
        Properties props = new Properties();
        try (InputStream in = EmailConfig.class.getResourceAsStream("/movieticketreservation/email.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
        }

        String host = firstNonEmpty(
                props.getProperty("smtp.host"),
                System.getenv("SMTP_HOST"),
                "smtp.gmail.com"
        );
        int port = parsePort(firstNonEmpty(
                props.getProperty("smtp.port"),
                System.getenv("SMTP_PORT"),
                "465"
        ));
        String username = firstNonEmpty(
                props.getProperty("smtp.username"),
                System.getenv("SMTP_USERNAME"),
                ""
        );
        String password = firstNonEmpty(
                props.getProperty("smtp.password"),
                System.getenv("SMTP_PASSWORD"),
                ""
        );
        String from = firstNonEmpty(
                props.getProperty("smtp.from"),
                System.getenv("SMTP_FROM"),
                username
        );
        if (host.isBlank() || username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("SMTP configuration is incomplete. Please edit email.properties or set SMTP_* env vars.");
        }
        return new EmailConfig(host, port, username, password, from);
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 465;
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
