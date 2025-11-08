package movieticketreservation.user;

import movieticketreservation.db.Database;
import movieticketreservation.security.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class UserDAO {

    public boolean usernameExists(String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ? LIMIT 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean register(User user, String plainPassword) throws SQLException {
        String sql = "INSERT INTO users(full_name, email, phone, username, password_hash, auto_pick_best_seat) VALUES(?,?,?,?,?,?)";
        String hash = PasswordUtil.hashPassword(plainPassword);
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getUsername());
            ps.setString(5, hash);
            ps.setBoolean(6, user.isAutoPickBestSeat());
            return ps.executeUpdate() == 1;
        }
    }

    public Optional<User> authenticate(String username, String password) throws SQLException {
        String sql = "SELECT id, full_name, email, phone, username, password_hash, auto_pick_best_seat FROM users WHERE username = ? LIMIT 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String storedHash = rs.getString("password_hash");
                if (!PasswordUtil.verifyPassword(password, storedHash)) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        }
    }

    public Optional<User> findById(int userId) throws SQLException {
        String sql = "SELECT id, full_name, email, phone, username, password_hash, auto_pick_best_seat FROM users WHERE id = ? LIMIT 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public boolean updateProfile(User user) throws SQLException {
        if (user == null || user.getId() <= 0) {
            return false;
        }
        String sql = "UPDATE users SET full_name = ?, email = ?, phone = ?, auto_pick_best_seat = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setBoolean(4, user.isAutoPickBestSeat());
            ps.setInt(5, user.getId());
            return ps.executeUpdate() == 1;
        }
    }

    public boolean validateLogin(String username, String password) throws SQLException {
        String sql = "SELECT password_hash FROM users WHERE username = ? LIMIT 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String stored = rs.getString(1);
                return PasswordUtil.verifyPassword(password, stored);
            }
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setPhone(rs.getString("phone"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        try {
            boolean autoPick = rs.getBoolean("auto_pick_best_seat");
            user.setAutoPickBestSeat(autoPick);
        } catch (SQLException ex) {
            user.setAutoPickBestSeat(false);
        }
        return user;
    }
}
