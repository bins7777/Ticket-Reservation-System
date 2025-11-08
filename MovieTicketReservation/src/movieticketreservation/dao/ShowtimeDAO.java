package movieticketreservation.dao;

import movieticketreservation.db.Database;
import movieticketreservation.model.Showtime;
import movieticketreservation.util.SeatCapacity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class ShowtimeDAO {

    public List<Showtime> findByMovie(int movieId) throws SQLException {
        String sql = "SELECT s.id, s.movie_id, s.branch_id, b.name AS branch_name, s.show_date, s.show_time, " +
                "s.available_seats, s.ticket_price " +
                "FROM showtimes s JOIN branches b ON s.branch_id = b.id " +
                "WHERE s.movie_id = ? ORDER BY s.show_date, s.show_time";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapShowtimes(rs);
            }
        }
    }

    public List<Showtime> findByMovieAndBranch(int movieId, int branchId) throws SQLException {
        String sql = "SELECT s.id, s.movie_id, s.branch_id, b.name AS branch_name, s.show_date, s.show_time, " +
                "s.available_seats, s.ticket_price " +
                "FROM showtimes s JOIN branches b ON s.branch_id = b.id " +
                "WHERE s.movie_id = ? AND s.branch_id = ? " +
                "ORDER BY s.show_date, s.show_time";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, movieId);
            ps.setInt(2, branchId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapShowtimes(rs);
            }
        }
    }

    public Showtime createShowtime(int movieId,
                                   int branchId,
                                   LocalDate showDate,
                                   LocalTime showTime,
                                   int availableSeats,
                                   BigDecimal ticketPrice) throws SQLException {
        int normalizedSeats = availableSeats <= 0 ? SeatCapacity.DEFAULT_CAPACITY : availableSeats;
        BigDecimal normalizedPrice = normalizeTicketPrice(ticketPrice);
        String sql = "INSERT INTO showtimes(movie_id, branch_id, show_date, show_time, available_seats, ticket_price) " +
                "VALUES(?,?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE available_seats = VALUES(available_seats), ticket_price = VALUES(ticket_price)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, movieId);
            ps.setInt(2, branchId);
            ps.setDate(3, Date.valueOf(showDate));
            ps.setTime(4, Time.valueOf(showTime));
            ps.setInt(5, normalizedSeats);
            ps.setBigDecimal(6, normalizedPrice);
            ps.executeUpdate();

            int id = 0;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getInt(1);
                }
            }
            if (id == 0) {
                String findSql = "SELECT id FROM showtimes WHERE movie_id = ? AND branch_id = ? AND show_date = ? AND show_time = ?";
                try (PreparedStatement find = conn.prepareStatement(findSql)) {
                    find.setInt(1, movieId);
                    find.setInt(2, branchId);
                    find.setDate(3, Date.valueOf(showDate));
                    find.setTime(4, Time.valueOf(showTime));
                    try (ResultSet rs = find.executeQuery()) {
                        if (rs.next()) {
                            id = rs.getInt(1);
                        }
                    }
                }
            }
            return new Showtime(id, movieId, branchId, null, showDate, showTime, normalizedSeats, normalizedPrice);
        }
    }

    public void deleteShowtime(int showtimeId) throws SQLException {
        String sql = "DELETE FROM showtimes WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, showtimeId);
            ps.executeUpdate();
        }
    }

    private List<Showtime> mapShowtimes(ResultSet rs) throws SQLException {
        List<Showtime> showtimes = new ArrayList<>();
        while (rs.next()) {
            showtimes.add(new Showtime(
                    rs.getInt("id"),
                    rs.getInt("movie_id"),
                    rs.getInt("branch_id"),
                    rs.getString("branch_name"),
                    rs.getDate("show_date").toLocalDate(),
                    rs.getTime("show_time").toLocalTime(),
                    rs.getInt("available_seats"),
                    rs.getBigDecimal("ticket_price")
            ));
        }
        return showtimes;
    }

    private BigDecimal normalizeTicketPrice(BigDecimal ticketPrice) {
        if (ticketPrice == null || ticketPrice.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return ticketPrice.setScale(2, RoundingMode.HALF_UP);
    }
}
