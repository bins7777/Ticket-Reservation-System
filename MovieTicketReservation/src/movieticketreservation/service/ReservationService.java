package movieticketreservation.service;

import movieticketreservation.dao.ReservationDAO;
import movieticketreservation.model.CheckoutSummary;
import movieticketreservation.model.Reservation;
import movieticketreservation.model.Showtime;
import movieticketreservation.user.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class ReservationService {

    private static final ReservationDAO RESERVATION_DAO = new ReservationDAO();

    private ReservationService() {
    }

    public static Reservation createReservation(User user,
                                                CheckoutSummary summary,
                                                String paymentMethod) throws SQLException {
        if (summary == null) {
            throw new IllegalArgumentException("Checkout summary is required to create a reservation.");
        }
        Showtime showtime = summary.getShowtime();
        if (showtime == null) {
            throw new IllegalArgumentException("Showtime information is missing from the checkout summary.");
        }
        String confirmationCode = generateConfirmationCode();
        String safePayment = paymentMethod == null ? "Unknown" : paymentMethod;
        return RESERVATION_DAO.insertReservation(confirmationCode, user, summary, safePayment);
    }

    public static List<Reservation> findByUser(User user) throws SQLException {
        return RESERVATION_DAO.findByUser(user);
    }

    public static List<Reservation> findByBranch(int branchId) throws SQLException {
        return RESERVATION_DAO.findByBranch(branchId);
    }

    public static Set<String> getReservedSeats(int showtimeId) throws SQLException {
        return Set.copyOf(RESERVATION_DAO.findReservedSeatsByShowtime(showtimeId));
    }

    public static void clearAll() throws SQLException {
        RESERVATION_DAO.deleteAll();
    }

    private static String generateConfirmationCode() {
        long timeComponent = System.currentTimeMillis();
        int randomComponent = ThreadLocalRandom.current().nextInt(1000, 10_000);
        return "MT-" + Long.toString(timeComponent, 36).toUpperCase(Locale.ENGLISH) + randomComponent;
    }
}
