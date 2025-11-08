package movieticketreservation.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate of the user's ticket and concession selections that feeds the payment view.
 */
public class CheckoutSummary {
    private final Movie movie;
    private final Showtime showtime;
    private final List<String> seats;
    private final int ticketCount;
    private final BigDecimal ticketPrice;
    private final BigDecimal ticketSubtotal;
    private final List<CheckoutLineItem> foodItems;
    private final BigDecimal foodSubtotal;
    private final BigDecimal grandTotal;

    public CheckoutSummary(Movie movie,
                           Showtime showtime,
                           List<String> seats,
                           int ticketCount,
                           BigDecimal ticketPrice,
                           BigDecimal ticketSubtotal,
                           List<CheckoutLineItem> foodItems,
                           BigDecimal foodSubtotal,
                           BigDecimal grandTotal) {
        this.movie = movie;
        this.showtime = showtime;
        this.seats = seats == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(seats));
        this.ticketCount = Math.max(ticketCount, 0);
        this.ticketPrice = ticketPrice == null ? BigDecimal.ZERO : ticketPrice;
        this.ticketSubtotal = ticketSubtotal == null ? BigDecimal.ZERO : ticketSubtotal;
        this.foodItems = foodItems == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(foodItems));
        this.foodSubtotal = foodSubtotal == null ? BigDecimal.ZERO : foodSubtotal;
        this.grandTotal = grandTotal == null ? BigDecimal.ZERO : grandTotal;
    }

    public Movie getMovie() {
        return movie;
    }

    public Showtime getShowtime() {
        return showtime;
    }

    public List<String> getSeats() {
        return seats;
    }

    public int getTicketCount() {
        return ticketCount;
    }

    public BigDecimal getTicketPrice() {
        return ticketPrice;
    }

    public BigDecimal getTicketSubtotal() {
        return ticketSubtotal;
    }

    public List<CheckoutLineItem> getFoodItems() {
        return foodItems;
    }

    public BigDecimal getFoodSubtotal() {
        return foodSubtotal;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }
}
