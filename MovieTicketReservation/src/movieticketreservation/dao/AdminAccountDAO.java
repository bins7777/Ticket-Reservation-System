package movieticketreservation.dao;

import movieticketreservation.db.Database;
import movieticketreservation.model.AdminAccount;
import movieticketreservation.model.Branch;
import movieticketreservation.user.User;
import movieticketreservation.security.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AdminAccountDAO {

    public Optional<AdminAccount> authenticate(String username, String password) throws SQLException {
        String sql = "SELECT a.id AS admin_id, a.branch_id, b.name AS branch_name, " +
                     "u.id AS user_id, u.full_name, u.email, u.phone, u.username, u.password_hash, u.auto_pick_best_seat " +
                     "FROM admin_accounts a " +
                     "JOIN users u ON a.user_id = u.id " +
                     "JOIN branches b ON a.branch_id = b.id " +
                     "WHERE u.username = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String hash = rs.getString("password_hash");
                if (!PasswordUtil.verifyPassword(password, hash)) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        }
    }

    public Optional<AdminAccount> findByUsername(String username) throws SQLException {
        String sql = "SELECT a.id AS admin_id, a.branch_id, b.name AS branch_name, " +
                     "u.id AS user_id, u.full_name, u.email, u.phone, u.username, u.password_hash, u.auto_pick_best_seat " +
                     "FROM admin_accounts a " +
                     "JOIN users u ON a.user_id = u.id " +
                     "JOIN branches b ON a.branch_id = b.id " +
                     "WHERE u.username = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    private AdminAccount mapRow(ResultSet rs) throws SQLException {
        Branch branch = new Branch(rs.getInt("branch_id"), rs.getString("branch_name"));
        User user = new User();
        user.setId(rs.getInt("user_id"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setPhone(rs.getString("phone"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setAutoPickBestSeat(rs.getBoolean("auto_pick_best_seat"));

        AdminAccount admin = new AdminAccount();
        admin.setId(rs.getInt("admin_id"));
        admin.setBranch(branch);
        admin.setUser(user);
        return admin;
    }
}
