package movieticketreservation.model;

import java.time.LocalDateTime;
import java.util.List;

import movieticketreservation.user.User;

/**
 * Represents a successfully paid booking for a showtime.
 */
public class Reservation {
    private final String confirmationCode;
    private final User user;
    private final CheckoutSummary summary;
    private final String paymentMethod;
    private final LocalDateTime purchaseDateTime;

    public Reservation(String confirmationCode,
                       User user,
                       CheckoutSummary summary,
                       String paymentMethod,
                       LocalDateTime purchaseDateTime) {
        this.confirmationCode = confirmationCode;
        this.user = user;
        this.summary = summary;
        this.paymentMethod = paymentMethod;
        this.purchaseDateTime = purchaseDateTime;
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public User getUser() {
        return user;
    }

    public CheckoutSummary getSummary() {
        return summary;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public LocalDateTime getPurchaseDateTime() {
        return purchaseDateTime;
    }

    public List<String> getSeats() {
        return summary == null ? List.of() : summary.getSeats();
    }
}
