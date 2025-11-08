package movieticketreservation.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import movieticketreservation.security.PasswordUtil;
import movieticketreservation.util.SeatCapacity;

/**
 * Best-effort schema bootstrap for local development.
 * Creates the target database and users table if missing.
 */
public final class DatabaseInitializer {
    private static volatile boolean initialized = false;
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern LEADING_TRAILING_DASH = Pattern.compile("^-+|-+$");

    private DatabaseInitializer() {}

    public static void ensureSchema() throws SQLException {
        if (initialized) return;
        synchronized (DatabaseInitializer.class) {
            if (initialized) return;

            String host = DatabaseConfig.getHost();
            String port = DatabaseConfig.getPort();
            String db   = DatabaseConfig.getDatabase();
            String user = DatabaseConfig.getUser();
            String pass = DatabaseConfig.getPassword();

            // Load driver once
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                throw new SQLException("MySQL JDBC Driver not found. Add mysql-connector-j to project libraries.", e);
            }

            String dbUrl = String.format(
                "jdbc:mysql://%s:%s/%s?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, db
            );

            try {
                try (Connection conn = DriverManager.getConnection(dbUrl, user, pass)) {
                    ensureTables(conn);
                    seedData(conn);
                }
            } catch (SQLException ex) {
                if (!isUnknownDatabase(ex)) {
                    throw ex;
                }
                // Database is missing, try to create it (requires CREATE privilege)
                createDatabaseIfNeeded(host, port, db, user, pass);
                try (Connection conn = DriverManager.getConnection(dbUrl, user, pass)) {
                    ensureTables(conn);
                    seedData(conn);
                }
            }

            initialized = true;
        }
    }

    private static boolean isUnknownDatabase(SQLException ex) {
        String sqlState = ex.getSQLState();
        if (sqlState != null && sqlState.startsWith("42") && ex.getMessage() != null) {
            return ex.getMessage().toLowerCase().contains("unknown database");
        }
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("unknown database");
    }

    private static void createDatabaseIfNeeded(String host, String port, String db,
                                               String user, String pass) throws SQLException {
        String rootUrl = String.format(
            "jdbc:mysql://%s:%s/?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            host, port
        );
        try (Connection rootConn = DriverManager.getConnection(rootUrl, user, pass);
             Statement st = rootConn.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + db + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException createEx) {
            throw new SQLException(
                "Unable to ensure database '" + db + "'. Please create it manually or grant CREATE DATABASE permission. " +
                "Original error: " + createEx.getMessage(), createEx);
        }
    }

    private static void ensureTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS branches (" +
                "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                "name VARCHAR(120) NOT NULL UNIQUE, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB"
            );
            ensureTableEngine(conn, "branches", "InnoDB");
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                "full_name VARCHAR(150) NOT NULL, " +
                "email VARCHAR(255) NOT NULL, " +
                "phone VARCHAR(50) NOT NULL, " +
                "username VARCHAR(100) NOT NULL UNIQUE, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "auto_pick_best_seat TINYINT(1) NOT NULL DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB"
            );
            ensureTableEngine(conn, "users", "InnoDB");
            ensureColumn(conn, "users", "auto_pick_best_seat", "TINYINT(1) NOT NULL DEFAULT 0");
            ensureAdminAccountsTable(conn);
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS movies (" +
                "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                "slug VARCHAR(150) NOT NULL UNIQUE, " +
                "title VARCHAR(200) NOT NULL, " +
                "release_year INT NOT NULL, " +
                "genres VARCHAR(200) NOT NULL, " +
                "runtime_minutes INT NOT NULL, " +
                "poster_path VARCHAR(255) NOT NULL, " +
                "synopsis TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB"
            );
            ensureTableEngine(conn, "movies", "InnoDB");
            ensureTableEngine(conn, "admin_accounts", "InnoDB");
            ensureIntAutoIncrementPrimary(conn, "branches");
            ensureIntAutoIncrementPrimary(conn, "movies");
            ensureMoviesSchema(conn);

            String movieIdColumnType = resolveIdColumnType(conn, "movies");
            String branchIdColumnType = resolveIdColumnType(conn, "branches");
            String userIdColumnType = resolveIdColumnType(conn, "users");

            String showtimesDdl = String.format(
                    "CREATE TABLE IF NOT EXISTS showtimes (" +
                    "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "movie_id %s NOT NULL, " +
                    "branch_id %s NOT NULL, " +
                    "show_date DATE NOT NULL, " +
                    "show_time TIME NOT NULL, " +
                    "available_seats INT NOT NULL DEFAULT %d, " +
                    "ticket_price DECIMAL(10,2) NOT NULL DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE KEY unique_showtime (movie_id, branch_id, show_date, show_time), " +
                    "INDEX idx_showtimes_movie (movie_id), " +
                    "INDEX idx_showtimes_branch (branch_id), " +
                    "CONSTRAINT fk_showtimes_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE, " +
                    "CONSTRAINT fk_showtimes_branch FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB",
                    movieIdColumnType,
                    branchIdColumnType,
                    SeatCapacity.DEFAULT_CAPACITY
            );
            st.executeUpdate(showtimesDdl);
            ensureTableEngine(conn, "showtimes", "InnoDB");
            ensureIntAutoIncrementPrimary(conn, "showtimes");
            ensureShowtimesSchema(conn);
            ensureShowtimeCapacityDefaults(conn);
            String showtimeIdColumnType = resolveIdColumnType(conn, "showtimes");

            String foodItemsDdl = String.format(
                    "CREATE TABLE IF NOT EXISTS food_items (" +
                    "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "branch_id %s NOT NULL, " +
                    "name VARCHAR(150) NOT NULL, " +
                    "description TEXT, " +
                    "price DECIMAL(10,2) NOT NULL DEFAULT 0, " +
                    "image_path VARCHAR(255), " +
                    "is_active TINYINT(1) NOT NULL DEFAULT 1, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "INDEX idx_food_branch (branch_id), " +
                    "CONSTRAINT fk_food_branch FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB",
                    branchIdColumnType
            );
            st.executeUpdate(foodItemsDdl);
            ensureTableEngine(conn, "food_items", "InnoDB");
            ensureIntAutoIncrementPrimary(conn, "food_items");
            ensureFoodItemsSchema(conn, branchIdColumnType);
            ensureReservationTables(conn, userIdColumnType, showtimeIdColumnType);
        }
    }

    private static void seedData(Connection conn) throws SQLException {
        int antipoloId = ensureBranch(conn, "Antipolo");
        int adminUserId = ensureAdminUser(conn,
                "Antipolo Branch Admin",
                "antipolo.admin@cinema.local",
                "1234567890",
                "antipolo_admin",
                "Antipolo123!");
        ensureAdminAccount(conn, antipoloId, adminUserId);

        int sanJuanId = ensureBranch(conn, "San Juan");
        int sanJuanAdminUserId = ensureAdminUser(conn,
                "San Juan Branch Admin",
                "sanjuan.admin@cinema.local",
                "0987654321",
                "sanjuan_admin",
                "SanJuan123!");
        ensureAdminAccount(conn, sanJuanId, sanJuanAdminUserId);

        seedDefaultMovies(conn);
        seedDefaultFoodItems(conn, antipoloId);
        seedDefaultFoodItems(conn, sanJuanId);
    }

    private static String resolveIdColumnType(Connection conn,
                                              String tableName) throws SQLException {
        String sql = "SELECT COLUMN_TYPE FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = 'id'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DatabaseConfig.getDatabase());
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String columnType = rs.getString("COLUMN_TYPE");
                    if (columnType != null && !columnType.isBlank()) {
                        return columnType.toUpperCase(Locale.ROOT);
                    }
                }
            }
        }
        return "INT";
    }

    private static void ensureMoviesSchema(Connection conn) throws SQLException {
        ensureColumn(conn, "movies", "title", "VARCHAR(200) NOT NULL DEFAULT ''");
        ensureColumn(conn, "movies", "release_year", "INT NOT NULL DEFAULT 0");
        ensureColumn(conn, "movies", "genres", "VARCHAR(200) NOT NULL DEFAULT ''");
        ensureColumn(conn, "movies", "runtime_minutes", "INT NOT NULL DEFAULT 0");
        ensureColumn(conn, "movies", "poster_path", "VARCHAR(255) NOT NULL DEFAULT ''");
        ensureColumn(conn, "movies", "synopsis", "TEXT");
        ensureColumn(conn, "movies", "created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        ensureSlugColumn(conn);
    }

    private static void ensureFoodItemsSchema(Connection conn, String branchIdColumnType) throws SQLException {
        ensureColumn(conn, "food_items", "branch_id", branchIdColumnType + " DEFAULT NULL");
        ensureColumn(conn, "food_items", "name", "VARCHAR(150) NOT NULL DEFAULT ''");
        ensureColumn(conn, "food_items", "description", "TEXT");
        ensureColumn(conn, "food_items", "price", "DECIMAL(10,2) NOT NULL DEFAULT 0");
        ensureColumn(conn, "food_items", "image_path", "VARCHAR(255)");
        ensureColumn(conn, "food_items", "is_active", "TINYINT(1) NOT NULL DEFAULT 1");
        ensureColumn(conn, "food_items", "created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        ensureColumn(conn, "food_items", "updated_at",
                "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");

        try (Statement st = conn.createStatement()) {
            st.executeUpdate("ALTER TABLE food_items ADD INDEX idx_food_branch (branch_id)");
        } catch (SQLException ex) {
            if (!isDuplicateKeyName(ex)) {
                throw ex;
            }
        }

    }

    private static void ensureReservationTables(Connection conn,
                                                String userIdColumnType,
                                                String showtimeIdColumnType) throws SQLException {
        if (userIdColumnType == null || userIdColumnType.isBlank()) {
            userIdColumnType = "INT";
        }
        if (showtimeIdColumnType == null || showtimeIdColumnType.isBlank()) {
            showtimeIdColumnType = "INT";
        }
        try (Statement st = conn.createStatement()) {
            String reservationsDdl = String.format(
                    "CREATE TABLE IF NOT EXISTS reservations (" +
                    "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "confirmation_code VARCHAR(40) NOT NULL UNIQUE, " +
                    "user_id %s NOT NULL, " +
                    "showtime_id %s NOT NULL, " +
                    "payment_method VARCHAR(60) NOT NULL, " +
                    "ticket_price DECIMAL(10,2) NOT NULL DEFAULT 0, " +
                    "ticket_count INT NOT NULL DEFAULT 0, " +
                    "ticket_subtotal DECIMAL(10,2) NOT NULL DEFAULT 0, " +
                    "food_subtotal DECIMAL(10,2) NOT NULL DEFAULT 0, " +
                    "grand_total DECIMAL(10,2) NOT NULL DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "INDEX idx_reservations_user (user_id), " +
                    "INDEX idx_reservations_showtime (showtime_id), " +
                    "CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE, " +
                    "CONSTRAINT fk_reservation_showtime FOREIGN KEY (showtime_id) REFERENCES showtimes(id) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB",
                    userIdColumnType,
                    showtimeIdColumnType
            );
            st.executeUpdate(reservationsDdl);

            String reservationSeatsDdl = String.format(
                    "CREATE TABLE IF NOT EXISTS reservation_seats (" +
                    "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "reservation_id INT NOT NULL, " +
                    "showtime_id %s NOT NULL, " +
                    "seat_label VARCHAR(8) NOT NULL, " +
                    "UNIQUE KEY uniq_showtime_seat (showtime_id, seat_label), " +
                    "INDEX idx_reservation_seats_reservation (reservation_id), " +
                    "CONSTRAINT fk_reservation_seats_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE, " +
                    "CONSTRAINT fk_reservation_seats_showtime FOREIGN KEY (showtime_id) REFERENCES showtimes(id) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB",
                    showtimeIdColumnType
            );
            st.executeUpdate(reservationSeatsDdl);

            String reservationFoodItemsDdl =
                    "CREATE TABLE IF NOT EXISTS reservation_food_items (" +
                    "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "reservation_id INT NOT NULL, " +
                    "item_name VARCHAR(200) NOT NULL, " +
                    "quantity INT NOT NULL DEFAULT 0, " +
                    "unit_price DECIMAL(10,2) NOT NULL DEFAULT 0, " +
                    "line_total DECIMAL(10,2) NOT NULL DEFAULT 0, " +
                    "INDEX idx_food_reservation (reservation_id), " +
                    "CONSTRAINT fk_food_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB";
            st.executeUpdate(reservationFoodItemsDdl);
        }

        ensureTableEngine(conn, "reservations", "InnoDB");
        ensureTableEngine(conn, "reservation_seats", "InnoDB");
        ensureTableEngine(conn, "reservation_food_items", "InnoDB");
        ensureIntAutoIncrementPrimary(conn, "reservations");
        ensureIntAutoIncrementPrimary(conn, "reservation_seats");
        ensureIntAutoIncrementPrimary(conn, "reservation_food_items");
    }

    private static void ensureShowtimesSchema(Connection conn) throws SQLException {
        ensureColumn(conn, "showtimes", "ticket_price", "DECIMAL(10,2) NOT NULL DEFAULT 0");
        ensureColumn(conn, "showtimes", "available_seats", "INT NOT NULL DEFAULT " + SeatCapacity.DEFAULT_CAPACITY);
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE showtimes SET ticket_price = 0 WHERE ticket_price IS NULL")) {
            ps.executeUpdate();
        }
    }

    private static void ensureShowtimeCapacityDefaults(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE showtimes SET available_seats = ? WHERE available_seats IS NULL OR available_seats <= 0")) {
            ps.setInt(1, SeatCapacity.DEFAULT_CAPACITY);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE showtimes SET ticket_price = 0 WHERE ticket_price IS NULL OR ticket_price < 0")) {
            ps.executeUpdate();
        }
    }

    // Legacy databases may predate the slug column; bring them up to date without dropping data.
    private static void ensureSlugColumn(Connection conn) throws SQLException {
        boolean hasSlug = hasColumn(conn, "movies", "slug");
        if (!hasSlug) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE movies ADD COLUMN slug VARCHAR(150)");
            }
            populateMissingSlugs(conn);
        } else {
            populateMissingSlugs(conn);
        }

        try (Statement st = conn.createStatement()) {
            st.executeUpdate("ALTER TABLE movies MODIFY COLUMN slug VARCHAR(150) NOT NULL");
        } catch (SQLException ex) {
            if (!isBenignAlterError(ex)) {
                throw ex;
            }
        }

        try (Statement st = conn.createStatement()) {
            st.executeUpdate("ALTER TABLE movies ADD UNIQUE KEY unique_movies_slug (slug)");
        } catch (SQLException ex) {
            if (!isDuplicateKeyName(ex)) {
                throw ex;
            }
        }
    }

    private static void populateMissingSlugs(Connection conn) throws SQLException {
        Set<String> assigned = new HashSet<>();
        try (PreparedStatement select = conn.prepareStatement(
                "SELECT id, title, slug FROM movies ORDER BY id");
             PreparedStatement update = conn.prepareStatement(
                "UPDATE movies SET slug = ? WHERE id = ?")) {
            boolean hasUpdates = false;
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String title = rs.getString("title");
                    String currentSlug = rs.getString("slug");
                    String baseSlug = slugify(title);
                    if (baseSlug.isBlank()) {
                        baseSlug = "movie-" + id;
                    }
                    String candidate = slugify(currentSlug);
                    if (candidate.isBlank()) {
                        candidate = baseSlug;
                    }
                    String unique = candidate;
                    int suffix = 2;
                    while (!assigned.add(unique)) {
                        unique = candidate + "-" + suffix;
                        suffix++;
                    }
                    if (!unique.equals(currentSlug)) {
                        update.setString(1, unique);
                        update.setInt(2, id);
                        update.addBatch();
                        hasUpdates = true;
                    }
                }
            }
            if (hasUpdates) {
                update.executeBatch();
            }
        }
    }

    private static void ensureColumn(Connection conn,
                                     String tableName,
                                     String columnName,
                                     String columnDefinition) throws SQLException {
        if (hasColumn(conn, tableName, columnName)) {
            return;
        }
        String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static boolean hasColumn(Connection conn,
                                     String tableName,
                                     String columnName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DatabaseConfig.getDatabase());
            ps.setString(2, tableName);
            ps.setString(3, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String slugify(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll("-");
        normalized = LEADING_TRAILING_DASH.matcher(normalized).replaceAll("");
        return normalized;
    }

    private static boolean isDuplicateKeyName(SQLException ex) {
        String message = ex.getMessage();
        return (ex.getErrorCode() == 1061) ||
                (message != null && message.toLowerCase(Locale.ROOT).contains("duplicate key name"));
    }

    private static boolean isBenignAlterError(SQLException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("doesn't change column") ||
                lower.contains("no change") ||
                lower.contains("already") ||
                lower.contains("not null");
    }

    private static int ensureBranch(Connection conn, String branchName) throws SQLException {
        try (PreparedStatement find = conn.prepareStatement("SELECT id FROM branches WHERE name = ?")) {
            find.setString(1, branchName);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO branches(name) VALUES(?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, branchName);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        // Fallback lookup in case insert did not return key
        try (PreparedStatement findAgain = conn.prepareStatement("SELECT id FROM branches WHERE name = ?")) {
            findAgain.setString(1, branchName);
            try (ResultSet rs = findAgain.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Unable to ensure branch: " + branchName);
    }

    private static int ensureAdminUser(Connection conn,
                                       String fullName,
                                       String email,
                                       String phone,
                                       String username,
                                       String defaultPassword) throws SQLException {
        try (PreparedStatement find = conn.prepareStatement(
                "SELECT id FROM users WHERE username = ?")) {
            find.setString(1, username);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        String hash = PasswordUtil.hashPassword(defaultPassword);
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO users(full_name, email, phone, username, password_hash, auto_pick_best_seat) VALUES(?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, fullName);
            insert.setString(2, email);
            insert.setString(3, phone);
            insert.setString(4, username);
            insert.setString(5, hash);
            insert.setBoolean(6, false);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        try (PreparedStatement find = conn.prepareStatement(
                "SELECT id FROM users WHERE username = ?")) {
            find.setString(1, username);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Unable to ensure admin user for username: " + username);
    }

    private static void ensureAdminAccount(Connection conn,
                                           int branchId,
                                           int userId) throws SQLException {
        try (PreparedStatement find = conn.prepareStatement(
                "SELECT id FROM admin_accounts WHERE user_id = ?")) {
            find.setInt(1, userId);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO admin_accounts(branch_id, user_id) VALUES(?,?)")) {
            insert.setInt(1, branchId);
            insert.setInt(2, userId);
            insert.executeUpdate();
        }
    }

    private static void ensureAdminAccountsTable(Connection conn) throws SQLException {
        boolean needsRebuild = false;
        String columnCheck = "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'admin_accounts' AND COLUMN_NAME = 'user_id'";
        try (PreparedStatement ps = conn.prepareStatement(columnCheck)) {
            ps.setString(1, DatabaseConfig.getDatabase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    needsRebuild = rs.getInt(1) == 0;
                }
            }
        }
        if (needsRebuild) {
            try (Statement drop = conn.createStatement()) {
                drop.executeUpdate("DROP TABLE IF EXISTS admin_accounts");
            }
        }
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS admin_accounts (" +
                "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                "branch_id INT NOT NULL, " +
                "user_id INT NOT NULL UNIQUE, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE KEY unique_branch_admin (branch_id), " +
                "KEY idx_admin_branch (branch_id)" +
                ") ENGINE=InnoDB"
            );
        }
    }

    private static void ensureIntAutoIncrementPrimary(Connection conn,
                                                      String tableName) throws SQLException {
        String sql = "SELECT DATA_TYPE, COLUMN_TYPE, COLUMN_KEY, EXTRA FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = 'id'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DatabaseConfig.getDatabase());
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return; // Table does not exist yet; creation statement will handle it.
                }
                String dataType = rs.getString("DATA_TYPE");
                String columnType = rs.getString("COLUMN_TYPE");
                if (dataType == null || !"int".equalsIgnoreCase(dataType)) {
                    throw new SQLException("Expected INT primary key for table '" + tableName +
                            "', but found '" + columnType + "'. Please migrate the table or drop it so the application can recreate it.");
                }
                String columnKey = rs.getString("COLUMN_KEY");
                if (columnKey == null || !"PRI".equalsIgnoreCase(columnKey)) {
                    try (Statement alter = conn.createStatement()) {
                        alter.executeUpdate("ALTER TABLE `" + tableName + "` ADD PRIMARY KEY (`id`)");
                    } catch (SQLException ex) {
                        throw new SQLException("Unable to ensure primary key on `" + tableName + "`.`id`: " + ex.getMessage(), ex);
                    }
                }
                String extra = rs.getString("EXTRA");
                if (extra == null || !extra.toLowerCase().contains("auto_increment")) {
                    try (Statement alter = conn.createStatement()) {
                        alter.executeUpdate("ALTER TABLE `" + tableName + "` MODIFY COLUMN `id` INT NOT NULL AUTO_INCREMENT");
                    } catch (SQLException ex) {
                        throw new SQLException("Unable to ensure auto-increment on `" + tableName + "`.`id`: " + ex.getMessage(), ex);
                    }
                }
            }
        }
    }

    private static void ensureTableEngine(Connection conn,
                                          String tableName,
                                          String targetEngine) throws SQLException {
        String sql = "SELECT ENGINE FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DatabaseConfig.getDatabase());
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String currentEngine = rs.getString(1);
                    if (currentEngine != null && !currentEngine.equalsIgnoreCase(targetEngine)) {
                        try (Statement alter = conn.createStatement()) {
                            alter.executeUpdate("ALTER TABLE `" + tableName + "` ENGINE=" + targetEngine);
                        }
                    }
                }
            }
        }
    }

    private static void seedDefaultMovies(Connection conn) throws SQLException {
        ensureMovie(conn,
                "chainsaw-man-reze",
                "Chainsaw Man - The Movie: Reze Arc",
                2025,
                "Horror/Adventure",
                100,
                "images/chainsaw-man.jpg",
                "Denji faces a mysterious assassin known as the Bomb Girl in this highly anticipated arc from Chainsaw Man.");
        ensureMovie(conn,
                "good-boy",
                "Good Boy",
                2025,
                "Horror",
                73,
                "images/good-boy.jpg",
                "A chilling story that blurs the line between loyalty and terror when man's best friend turns menacing.");
        ensureMovie(conn,
                "spider-man-2",
                "Spider-Man 2",
                2004,
                "Action/Sci-Fi",
                141,
                "images/spiderman-2.jpg",
                "Peter Parker struggles to balance life and heroism while facing the brilliant Doctor Octopus.");
        ensureMovie(conn,
                "deadpool",
                "Deadpool",
                2016,
                "Action/Comedy",
                108,
                "images/deadpool.png",
                "The merc with a mouth seeks revenge in this irreverent, fourth-wall-breaking adventure.");
        ensureMovie(conn,
                "howls-moving-castle",
                "Howl's Moving Castle",
                2004,
                "Family/Fantasy",
                119,
                "images/howls.jpg",
                "Sophie embarks on a magical journey with the mysterious wizard Howl and his walking castle.");
        ensureMovie(conn,
                "ratatouille",
                "Ratatouille",
                2007,
                "Family/Comedy",
                111,
                "images/ratatouille.jpg",
                "A culinary rat teams up with a hapless chef to prove that anyone can cook.");
    }

    private static void seedDefaultFoodItems(Connection conn, int branchId) throws SQLException {
        ensureFoodItem(conn, branchId, "Nachos", "Loaded with salsa and cheese.", new BigDecimal("80.00"), null, true);
        ensureFoodItem(conn, branchId, "Fries", "Crispy golden fries.", new BigDecimal("100.00"), null, true);
        ensureFoodItem(conn, branchId, "Potato Mojos", "Seasoned potato slices.", new BigDecimal("100.00"), null, true);
        ensureFoodItem(conn, branchId, "Soda (Large)", "Ice-cold soft drink.", new BigDecimal("55.00"), null, true);
        ensureFoodItem(conn, branchId, "Hotdog Sandwich", "Classic bun with condiments.", new BigDecimal("70.00"), null, true);
        ensureFoodItem(conn, branchId, "Chicken Nuggets", "Six-piece chicken nuggets.", new BigDecimal("90.00"), null, true);
        ensureFoodItem(conn, branchId, "Popcorn (Large)", "Buttery movie popcorn.", new BigDecimal("120.00"), null, true);
    }

    private static void ensureFoodItem(Connection conn,
                                       int branchId,
                                       String name,
                                       String description,
                                       BigDecimal price,
                                       String imagePath,
                                       boolean active) throws SQLException {
        try (PreparedStatement find = conn.prepareStatement(
                "SELECT id, description, price, image_path, is_active FROM food_items WHERE branch_id = ? AND name = ?")) {
            try (PreparedStatement upgrade = conn.prepareStatement(
                    "UPDATE food_items SET branch_id = ? WHERE branch_id IS NULL OR branch_id = 0")) {
                upgrade.setInt(1, branchId);
                upgrade.executeUpdate();
            } catch (SQLException ignored) {
            }

            find.setInt(1, branchId);
            find.setString(2, name);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String currentDescription = rs.getString("description");
                    BigDecimal currentPrice = rs.getBigDecimal("price");
                    String currentImage = rs.getString("image_path");
                    boolean currentActive = rs.getBoolean("is_active");

                    boolean requiresUpdate = false;
                    if ((currentDescription == null || currentDescription.isBlank()) && description != null) {
                        requiresUpdate = true;
                    }
                    if (currentPrice == null && price != null) {
                        requiresUpdate = true;
                    }
                    if ((currentImage == null || currentImage.isBlank()) && imagePath != null && !imagePath.isBlank()) {
                        requiresUpdate = true;
                    }
                    if (!currentActive && active) {
                        requiresUpdate = true;
                    }

                    if (requiresUpdate) {
                        try (PreparedStatement update = conn.prepareStatement(
                                "UPDATE food_items SET description = ?, price = ?, image_path = ?, is_active = ? WHERE id = ?")) {
                            update.setString(1, description);
                            update.setBigDecimal(2, price);
                            update.setString(3, imagePath);
                            update.setBoolean(4, active);
                            update.setInt(5, id);
                            update.executeUpdate();
                        }
                    }
                    return;
                }
            }
        }

        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO food_items(branch_id, name, description, price, image_path, is_active) VALUES(?,?,?,?,?,?)")) {
            insert.setInt(1, branchId);
            insert.setString(2, name);
            insert.setString(3, description);
            insert.setBigDecimal(4, price == null ? BigDecimal.ZERO : price);
            insert.setString(5, imagePath);
            insert.setBoolean(6, active);
            insert.executeUpdate();
        }
    }

    private static void ensureMovie(Connection conn,
                                    String slug,
                                    String title,
                                    int releaseYear,
                                    String genres,
                                    int runtimeMinutes,
                                    String posterPath,
                                    String synopsis) throws SQLException {
        try (PreparedStatement find = conn.prepareStatement(
                "SELECT id, title, release_year, genres, runtime_minutes, poster_path, synopsis FROM movies WHERE slug = ?")) {
            find.setString(1, slug);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String currentTitle = rs.getString("title");
                    int currentReleaseYear = rs.getInt("release_year");
                    String currentGenres = rs.getString("genres");
                    int currentRuntime = rs.getInt("runtime_minutes");
                    String currentPoster = rs.getString("poster_path");
                    String currentSynopsis = rs.getString("synopsis");

                    boolean hasMissingData =
                            (currentTitle == null || currentTitle.isBlank()) ||
                            (currentReleaseYear == 0 && releaseYear > 0) ||
                            (currentGenres == null || currentGenres.isBlank()) ||
                            (currentRuntime <= 0 && runtimeMinutes > 0) ||
                            (currentPoster == null || currentPoster.isBlank()) ||
                            ((currentSynopsis == null || currentSynopsis.isBlank()) &&
                                    synopsis != null && !synopsis.isBlank());

                    if (!hasMissingData) {
                        return;
                    }

                    String newTitle = (currentTitle == null || currentTitle.isBlank()) ? title : currentTitle;
                    int newReleaseYear = currentReleaseYear == 0 && releaseYear > 0 ? releaseYear : currentReleaseYear;
                    String newGenres = (currentGenres == null || currentGenres.isBlank()) ? genres : currentGenres;
                    int newRuntime = currentRuntime <= 0 && runtimeMinutes > 0 ? runtimeMinutes : currentRuntime;
                    String newPoster = (currentPoster == null || currentPoster.isBlank()) ? posterPath : currentPoster;
                    String newSynopsis = (currentSynopsis == null || currentSynopsis.isBlank()) ? synopsis : currentSynopsis;

                    try (PreparedStatement update = conn.prepareStatement(
                            "UPDATE movies SET title = ?, release_year = ?, genres = ?, runtime_minutes = ?, poster_path = ?, synopsis = ? WHERE id = ?")) {
                        update.setString(1, newTitle);
                        update.setInt(2, newReleaseYear);
                        update.setString(3, newGenres);
                        update.setInt(4, newRuntime);
                        update.setString(5, newPoster);
                        update.setString(6, newSynopsis);
                        update.setInt(7, id);
                        update.executeUpdate();
                    }
                    return;
                }
            }
        }
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO movies(slug, title, release_year, genres, runtime_minutes, poster_path, synopsis) VALUES(?,?,?,?,?,?,?)")) {
            insert.setString(1, slug);
            insert.setString(2, title);
            insert.setInt(3, releaseYear);
            insert.setString(4, genres);
            insert.setInt(5, runtimeMinutes);
            insert.setString(6, posterPath);
            insert.setString(7, synopsis);
            insert.executeUpdate();
        }
    }
}
