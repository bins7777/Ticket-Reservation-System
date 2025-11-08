package movieticketreservation.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {

    public static Connection getConnection() throws SQLException {
        String host = DatabaseConfig.getHost();
        String port = DatabaseConfig.getPort();
        String db   = DatabaseConfig.getDatabase();
        String user = DatabaseConfig.getUser();
        String pass = DatabaseConfig.getPassword();

        // Ensure the database and required tables exist (no-op if already present)
        DatabaseInitializer.ensureSchema();

        String url = String.format("jdbc:mysql://%s:%s/%s?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC", host, port, db);

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found. Add mysql-connector-j to project libraries.", e);
        }

        return DriverManager.getConnection(url, user, pass);
    }
}
