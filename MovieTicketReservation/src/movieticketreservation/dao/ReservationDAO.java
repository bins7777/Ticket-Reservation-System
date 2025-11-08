package movieticketreservation.dao;

import movieticketreservation.db.Database;
import movieticketreservation.model.CheckoutLineItem;
import movieticketreservation.model.CheckoutSummary;
import movieticketreservation.model.Movie;
import movieticketreservation.model.Reservation;
import movieticketreservation.model.Showtime;
import movieticketreservation.user.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ReservationDAO {

    public Reservation insertReservation(String confirmationCode,
                                         User user,
                                         CheckoutSummary summary,
                                         String paymentMethod) throws SQLException {
        if (user == null || user.getId() <= 0) {
            throw new SQLException("A valid user is required to save a reservation.");
        }
        if (summary == null) {
            throw new SQLException("Checkout summary is missing.");
        }
        Showtime showtime = summary.getShowtime();
        if (showtime == null || showtime.getId() <= 0) {
            throw new SQLException("Showtime information is required.");
        }

        List<String> normalizedSeats = normalizeSeatSelection(summary.getSeats());
        String safePaymentMethod = (paymentMethod == null || paymentMethod.isBlank())
                ? "Unknown"
                : paymentMethod.trim();

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int reservationId = insertReservationRow(conn, confirmationCode, user.getId(), showtime.getId(),
                        safePaymentMethod, summary);
                int seatsBooked = normalizedSeats.size();
                if (seatsBooked > 0) {
                    insertReservationSeats(conn, reservationId, showtime.getId(), normalizedSeats);
                    adjustShowtimeAvailability(conn, showtime.getId(), seatsBooked);
                    int remaining = Math.max(showtime.getAvailableSeats() - seatsBooked, 0);
                    showtime.setAvailableSeats(remaining);
                }
                insertFoodItems(conn, reservationId, summary.getFoodItems());
                conn.commit();
                return new Reservation(
                        confirmationCode,
                        user,
                        summary,
                        safePaymentMethod,
                        LocalDateTime.now()
                );
            } catch (SQLException ex) {
                conn.rollback();
                throw translateSeatConstraint(ex);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<Reservation> findByUser(User user) throws SQLException {
        if (user == null || user.getId() <= 0) {
            return Collections.emptyList();
        }
        String sql = "SELECT r.id AS reservation_id, r.confirmation_code, r.payment_method, r.ticket_price, " +
                "r.ticket_count, r.ticket_subtotal, r.food_subtotal, r.grand_total, r.created_at, " +
                "s.id AS showtime_id, s.movie_id, s.branch_id, s.show_date, s.show_time, s.available_seats, " +
                "s.ticket_price AS showtime_ticket_price, " +
                "b.name AS branch_name, " +
                "m.slug AS movie_slug, m.title AS movie_title, m.release_year AS movie_release_year, " +
                "m.genres AS movie_genres, m.runtime_minutes AS movie_runtime, " +
                "m.poster_path AS movie_poster, m.synopsis AS movie_synopsis " +
                "FROM reservations r " +
                "JOIN showtimes s ON r.showtime_id = s.id " +
                "JOIN branches b ON s.branch_id = b.id " +
                "JOIN movies m ON s.movie_id = m.id " +
                "WHERE r.user_id = ? " +
                "ORDER BY r.created_at DESC";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                List<Reservation> reservations = new ArrayList<>();
                while (rs.next()) {
                    int reservationId = rs.getInt("reservation_id");
                    List<String> seats = fetchSeatsForReservation(conn, reservationId);
                    List<CheckoutLineItem> foodItems = fetchFoodForReservation(conn, reservationId);
                    reservations.add(mapReservation(rs, seats, foodItems, user));
                }
                return reservations;
            }
        }
    }

    public List<Reservation> findByBranch(int branchId) throws SQLException {
        if (branchId <= 0) {
            return Collections.emptyList();
        }
        String sql = "SELECT r.id AS reservation_id, r.confirmation_code, r.payment_method, r.ticket_price, " +
                "r.ticket_count, r.ticket_subtotal, r.food_subtotal, r.grand_total, r.created_at, " +
                "s.id AS showtime_id, s.movie_id, s.branch_id, s.show_date, s.show_time, s.available_seats, " +
                "s.ticket_price AS showtime_ticket_price, " +
                "b.name AS branch_name, " +
                "m.slug AS movie_slug, m.title AS movie_title, m.release_year AS movie_release_year, " +
                "m.genres AS movie_genres, m.runtime_minutes AS movie_runtime, " +
                "m.poster_path AS movie_poster, m.synopsis AS movie_synopsis, " +
                "u.id AS buyer_id, u.full_name AS buyer_full_name, u.email AS buyer_email, " +
                "u.phone AS buyer_phone, u.username AS buyer_username, " +
                "u.auto_pick_best_seat AS buyer_auto_pick " +
                "FROM reservations r " +
                "JOIN showtimes s ON r.showtime_id = s.id " +
                "JOIN branches b ON s.branch_id = b.id " +
                "JOIN movies m ON s.movie_id = m.id " +
                "JOIN users u ON r.user_id = u.id " +
                "WHERE s.branch_id = ? " +
                "ORDER BY r.created_at DESC";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, branchId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Reservation> reservations = new ArrayList<>();
                while (rs.next()) {
                    int reservationId = rs.getInt("reservation_id");
                    List<String> seats = fetchSeatsForReservation(conn, reservationId);
                    List<CheckoutLineItem> foodItems = fetchFoodForReservation(conn, reservationId);
                    User buyer = mapUserForReservation(rs);
                    reservations.add(mapReservation(rs, seats, foodItems, buyer));
                }
                return reservations;
            }
        }
    }

    public Set<String> findReservedSeatsByShowtime(int showtimeId) throws SQLException {
        if (showtimeId <= 0) {
            return Collections.emptySet();
        }
        String sql = "SELECT seat_label FROM reservation_seats WHERE showtime_id = ? ORDER BY seat_label";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> seats = new LinkedHashSet<>();
                while (rs.next()) {
                    seats.add(rs.getString("seat_label"));
                }
                return seats;
            }
        }
    }

    public void deleteAll() throws SQLException {
        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM reservation_food_items");
            st.executeUpdate("DELETE FROM reservation_seats");
            st.executeUpdate("DELETE FROM reservations");
        }
    }

    private int insertReservationRow(Connection conn,
                                     String confirmationCode,
                                     int userId,
                                     int showtimeId,
                                     String paymentMethod,
                                     CheckoutSummary summary) throws SQLException {
        String sql = "INSERT INTO reservations(" +
                "confirmation_code, user_id, showtime_id, payment_method, ticket_price, ticket_count, " +
                "ticket_subtotal, food_subtotal, grand_total) VALUES(?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, confirmationCode);
            ps.setInt(2, userId);
            ps.setInt(3, showtimeId);
            ps.setString(4, paymentMethod);
            ps.setBigDecimal(5, safe(summary.getTicketPrice()));
            ps.setInt(6, summary.getTicketCount());
            ps.setBigDecimal(7, safe(summary.getTicketSubtotal()));
            ps.setBigDecimal(8, safe(summary.getFoodSubtotal()));
            ps.setBigDecimal(9, safe(summary.getGrandTotal()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to generate a reservation id.");
    }

    private void insertReservationSeats(Connection conn,
                                        int reservationId,
                                        int showtimeId,
                                        List<String> seats) throws SQLException {
        if (seats == null || seats.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO reservation_seats(reservation_id, showtime_id, seat_label) VALUES(?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String seat : seats) {
                ps.setInt(1, reservationId);
                ps.setInt(2, showtimeId);
                ps.setString(3, seat);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertFoodItems(Connection conn,
                                 int reservationId,
                                 List<CheckoutLineItem> foodItems) throws SQLException {
        if (foodItems == null || foodItems.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO reservation_food_items(reservation_id, item_name, quantity, unit_price, line_total) " +
                "VALUES(?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (CheckoutLineItem item : foodItems) {
                String name = item.getName();
                ps.setInt(1, reservationId);
                ps.setString(2, name == null || name.isBlank() ? "Food Item" : name);
                ps.setInt(3, item.getQuantity());
                ps.setBigDecimal(4, safe(item.getUnitPrice()));
                ps.setBigDecimal(5, safe(item.getLineTotal()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void adjustShowtimeAvailability(Connection conn,
                                            int showtimeId,
                                            int seatsBooked) throws SQLException {
        if (seatsBooked <= 0) {
            return;
        }
        String sql = "UPDATE showtimes SET available_seats = available_seats - ? " +
                "WHERE id = ? AND available_seats >= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, seatsBooked);
            ps.setInt(2, showtimeId);
            ps.setInt(3, seatsBooked);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Not enough seats remaining for the selected showtime.");
            }
        }
    }

    private List<String> fetchSeatsForReservation(Connection conn, int reservationId) throws SQLException {
        String sql = "SELECT seat_label FROM reservation_seats WHERE reservation_id = ? ORDER BY seat_label";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> seats = new ArrayList<>();
                while (rs.next()) {
                    seats.add(rs.getString("seat_label"));
                }
                return seats;
            }
        }
    }

    private List<CheckoutLineItem> fetchFoodForReservation(Connection conn, int reservationId) throws SQLException {
        String sql = "SELECT item_name, quantity, unit_price, line_total " +
                "FROM reservation_food_items WHERE reservation_id = ? ORDER BY id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<CheckoutLineItem> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(new CheckoutLineItem(
                            rs.getString("item_name"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("unit_price"),
                            rs.getBigDecimal("line_total")
                    ));
                }
                return items;
            }
        }
    }

    private Reservation mapReservation(ResultSet rs,
                                       List<String> seats,
                                       List<CheckoutLineItem> foodItems,
                                       User user) throws SQLException {
        Movie movie = new Movie(
                rs.getInt("movie_id"),
                rs.getString("movie_slug"),
                rs.getString("movie_title"),
                rs.getInt("movie_release_year"),
                rs.getString("movie_genres"),
                rs.getInt("movie_runtime"),
                rs.getString("movie_poster"),
                rs.getString("movie_synopsis")
        );

        Date showDateValue = rs.getDate("show_date");
        LocalDate showDate = showDateValue == null ? null : showDateValue.toLocalDate();
        Time showTimeValue = rs.getTime("show_time");
        LocalTime showTime = showTimeValue == null ? null : showTimeValue.toLocalTime();

        Showtime showtime = new Showtime(
                rs.getInt("showtime_id"),
                rs.getInt("movie_id"),
                rs.getInt("branch_id"),
                rs.getString("branch_name"),
                showDate,
                showTime,
                rs.getInt("available_seats"),
                rs.getBigDecimal("showtime_ticket_price")
        );

        CheckoutSummary summary = new CheckoutSummary(
                movie,
                showtime,
                seats,
                rs.getInt("ticket_count"),
                rs.getBigDecimal("ticket_price"),
                rs.getBigDecimal("ticket_subtotal"),
                foodItems,
                rs.getBigDecimal("food_subtotal"),
                rs.getBigDecimal("grand_total")
        );

        Timestamp created = rs.getTimestamp("created_at");
        LocalDateTime createdAt = created == null ? null : created.toLocalDateTime();
        return new Reservation(
                rs.getString("confirmation_code"),
                user,
                summary,
                rs.getString("payment_method"),
                createdAt
        );
    }

    private List<String> normalizeSeatSelection(List<String> seats) {
        if (seats == null || seats.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String seat : seats) {
            if (seat == null) {
                continue;
            }
            String cleaned = seat.trim().toUpperCase(Locale.ENGLISH);
            if (!cleaned.isEmpty()) {
                normalized.add(cleaned);
            }
        }
        return new ArrayList<>(normalized);
    }

    private SQLException translateSeatConstraint(SQLException ex) {
        if (ex instanceof SQLIntegrityConstraintViolationException) {
            return new SQLException("One or more selected seats are no longer available. Please refresh the seating chart.", ex);
        }
        String sqlState = ex.getSQLState();
        String message = ex.getMessage();
        if ("23000".equals(sqlState) ||
                (message != null && message.toLowerCase(Locale.ENGLISH).contains("uniq_showtime_seat"))) {
            return new SQLException("One or more selected seats are no longer available. Please refresh the seating chart.", ex);
        }
        return ex;
    }

    private User mapUserForReservation(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("buyer_id"));
        user.setFullName(rs.getString("buyer_full_name"));
        user.setEmail(rs.getString("buyer_email"));
        user.setPhone(rs.getString("buyer_phone"));
        user.setUsername(rs.getString("buyer_username"));
        try {
            user.setAutoPickBestSeat(rs.getBoolean("buyer_auto_pick"));
        } catch (SQLException ignored) {
            user.setAutoPickBestSeat(false);
        }
        return user;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
