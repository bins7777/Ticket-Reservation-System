package movieticketreservation.dao;

import movieticketreservation.db.Database;
import movieticketreservation.model.FoodItem;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class FoodItemDAO {

    public List<FoodItem> findActiveByBranch(int branchId) throws SQLException {
        String sql = "SELECT id, branch_id, name, description, price, image_path, is_active " +
                     "FROM food_items WHERE branch_id = ? AND is_active = 1 ORDER BY name";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, branchId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapFoodItems(rs);
            }
        }
    }

    public List<FoodItem> findAllByBranch(int branchId) throws SQLException {
        String sql = "SELECT id, branch_id, name, description, price, image_path, is_active " +
                     "FROM food_items WHERE branch_id = ? ORDER BY name";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, branchId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapFoodItems(rs);
            }
        }
    }

    public FoodItem save(FoodItem item) throws SQLException {
        if (item.getId() == 0) {
            return insert(item);
        }
        return update(item);
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM food_items WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private FoodItem insert(FoodItem item) throws SQLException {
        String sql = "INSERT INTO food_items(branch_id, name, description, price, image_path, is_active) " +
                     "VALUES(?,?,?,?,?,?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, item.getBranchId());
            ps.setString(2, item.getName());
            ps.setString(3, item.getDescription());
            ps.setBigDecimal(4, item.getPrice() == null ? BigDecimal.ZERO : item.getPrice());
            ps.setString(5, item.getImagePath());
            ps.setBoolean(6, item.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    item.setId(keys.getInt(1));
                }
            }
            return item;
        }
    }

    private FoodItem update(FoodItem item) throws SQLException {
        String sql = "UPDATE food_items SET branch_id = ?, name = ?, description = ?, price = ?, image_path = ?, " +
                     "is_active = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, item.getBranchId());
            ps.setString(2, item.getName());
            ps.setString(3, item.getDescription());
            ps.setBigDecimal(4, item.getPrice() == null ? BigDecimal.ZERO : item.getPrice());
            ps.setString(5, item.getImagePath());
            ps.setBoolean(6, item.isActive());
            ps.setInt(7, item.getId());
            ps.executeUpdate();
            return item;
        }
    }

    private List<FoodItem> mapFoodItems(ResultSet rs) throws SQLException {
        List<FoodItem> items = new ArrayList<>();
        while (rs.next()) {
            FoodItem item = new FoodItem(
                    rs.getInt("id"),
                    rs.getInt("branch_id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getBigDecimal("price"),
                    rs.getString("image_path"),
                    rs.getBoolean("is_active")
            );
            items.add(item);
        }
        return items;
    }
}
