package movieticketreservation.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public class Showtime {
    private int id;
    private int movieId;
    private int branchId;
    private String branchName;
    private LocalDate showDate;
    private LocalTime showTime;
    private int availableSeats;
    private BigDecimal ticketPrice = BigDecimal.ZERO;

    public Showtime() {}

    public Showtime(int id,
                    int movieId,
                    int branchId,
                    String branchName,
                    LocalDate showDate,
                    LocalTime showTime,
                    int availableSeats,
                    BigDecimal ticketPrice) {
        this.id = id;
        this.movieId = movieId;
        this.branchId = branchId;
        this.branchName = branchName;
        this.showDate = showDate;
        this.showTime = showTime;
        this.availableSeats = availableSeats;
        setTicketPrice(ticketPrice);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getMovieId() {
        return movieId;
    }

    public void setMovieId(int movieId) {
        this.movieId = movieId;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public LocalDate getShowDate() {
        return showDate;
    }

    public void setShowDate(LocalDate showDate) {
        this.showDate = showDate;
    }

    public LocalTime getShowTime() {
        return showTime;
    }

    public void setShowTime(LocalTime showTime) {
        this.showTime = showTime;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(int availableSeats) {
        this.availableSeats = availableSeats;
    }

    public BigDecimal getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(BigDecimal ticketPrice) {
        this.ticketPrice = ticketPrice == null ? BigDecimal.ZERO : ticketPrice;
    }
}
