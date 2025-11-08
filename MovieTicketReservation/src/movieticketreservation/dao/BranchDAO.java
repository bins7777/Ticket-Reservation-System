package movieticketreservation.dao;

import movieticketreservation.db.Database;
import movieticketreservation.model.Branch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BranchDAO {

    public Optional<Branch> findById(int id) throws SQLException {
        String sql = "SELECT id, name FROM branches WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Branch> findByName(String name) throws SQLException {
        String sql = "SELECT id, name FROM branches WHERE name = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Branch> findAll() throws SQLException {
        String sql = "SELECT id, name FROM branches ORDER BY name";
        List<Branch> branches = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                branches.add(mapRow(rs));
            }
        }
        return branches;
    }

    private Branch mapRow(ResultSet rs) throws SQLException {
        return new Branch(rs.getInt("id"), rs.getString("name"));
    }
}
