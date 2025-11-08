package movieticketreservation;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import movieticketreservation.model.CheckoutLineItem;
import movieticketreservation.model.CheckoutSummary;
import movieticketreservation.model.Movie;
import movieticketreservation.model.Reservation;
import movieticketreservation.model.Showtime;
import movieticketreservation.service.EmailService;
import movieticketreservation.user.User;
import movieticketreservation.util.QrCodeGenerator;

public class ReservationDetailsController {

    @FXML private Label movieTitleLabel;
    @FXML private Label showtimeLabel;
    @FXML private Label purchaseDateLabel;
    @FXML private Label paymentMethodLabel;
    @FXML private Label ticketPriceLabel;
    @FXML private Label ticketQuantityLabel;
    @FXML private Label seatLabel;
    @FXML private Label foodItemsLabel;
    @FXML private Label totalAmountLabel;
    @FXML private ImageView posterImageView;
    @FXML private Label barcodeLabel;
    @FXML private Button closeButton;
    @FXML private Button qrButton;
    @FXML private Button emailQrButton;

    private static final DateTimeFormatter PURCHASE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy | HH:mm");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("hh:mm a");

    private Reservation currentReservation;

    public void configure(Reservation reservation) {
        this.currentReservation = reservation;
        if (reservation == null) {
            return;
        }
        CheckoutSummary summary = reservation.getSummary();
        Movie movie = summary != null ? summary.getMovie() : null;
        Showtime showtime = summary != null ? summary.getShowtime() : null;

        if (movieTitleLabel != null) {
            movieTitleLabel.setText(movie != null && movie.getTitle() != null ? movie.getTitle() : "Movie");
        }
        if (showtimeLabel != null) {
            if (showtime != null) {
                String branch = showtime.getBranchName() == null ? "Branch TBD" : showtime.getBranchName();
                String date = showtime.getShowDate() != null ? showtime.getShowDate().format(DATE_FORMAT) : "Date TBD";
                String time = showtime.getShowTime() != null ? showtime.getShowTime().format(TIME_FORMAT) : "Time TBD";
                showtimeLabel.setText(String.format("%s | %s | %s", branch, date, time));
            } else {
                showtimeLabel.setText("Showtime details unavailable");
            }
        }
        if (purchaseDateLabel != null) {
            if (reservation.getPurchaseDateTime() != null) {
                purchaseDateLabel.setText(reservation.getPurchaseDateTime().format(PURCHASE_FORMAT));
            } else {
                purchaseDateLabel.setText("Purchase time unavailable");
            }
        }
        if (paymentMethodLabel != null) {
            paymentMethodLabel.setText(reservation.getPaymentMethod());
        }
        if (ticketPriceLabel != null) {
            BigDecimal price = summary != null ? summary.getTicketPrice() : BigDecimal.ZERO;
            ticketPriceLabel.setText(formatCurrency(price));
        }
        if (ticketQuantityLabel != null) {
            ticketQuantityLabel.setText("#" + (summary != null ? summary.getTicketCount() : 0));
        }
        if (seatLabel != null) {
            List<String> seats = summary != null ? summary.getSeats() : List.of();
            seatLabel.setText(seats.isEmpty() ? "-" : String.join(", ", seats));
        }
        if (foodItemsLabel != null) {
            if (summary != null && !summary.getFoodItems().isEmpty()) {
                StringJoiner joiner = new StringJoiner("\n");
                for (CheckoutLineItem item : summary.getFoodItems()) {
                    joiner.add(item.getName() + " x" + item.getQuantity() + " | " + formatCurrency(item.getLineTotal()));
                }
                foodItemsLabel.setText(joiner.toString());
            } else {
                foodItemsLabel.setText("No add-ons");
            }
        }
        if (totalAmountLabel != null) {
            BigDecimal total = summary != null ? summary.getGrandTotal() : BigDecimal.ZERO;
            totalAmountLabel.setText(formatCurrency(total));
        }
        if (barcodeLabel != null) {
            barcodeLabel.setText(generateBarcode(reservation.getConfirmationCode()));
        }
        boolean hasSummary = summary != null;
        if (qrButton != null) {
            qrButton.setDisable(!hasSummary);
        }
        if (emailQrButton != null) {
            emailQrButton.setDisable(!hasSummary);
        }
        loadPoster(movie);
    }

    private void loadPoster(Movie movie) {
        if (posterImageView == null || movie == null) {
            return;
        }
        String posterPath = movie.getPosterPath();
        posterImageView.setImage(null);
        if (posterPath == null || posterPath.isBlank()) {
            return;
        }
        String resourcePath = posterPath.startsWith("/") ? posterPath : "/movieticketreservation/" + posterPath;
        try (InputStream stream = getClass().getResourceAsStream(resourcePath)) {
            if (stream != null) {
                posterImageView.setImage(new Image(stream));
            }
        } catch (Exception ignored) {
        }
    }

    private String formatCurrency(BigDecimal amount) {
        BigDecimal normalized = amount == null ? BigDecimal.ZERO : amount;
        return "₱ " + normalized.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String generateBarcode(String confirmationCode) {
        if (confirmationCode == null || confirmationCode.isBlank()) {
            confirmationCode = "0000000000";
        }
        String base = "|| || ||| |||| ||| | ||| || |||| | |||";
        return base + "  " + confirmationCode;
    }

    @FXML
    void handleClose() {
        if (closeButton != null && closeButton.getScene() != null) {
            Stage stage = (Stage) closeButton.getScene().getWindow();
            if (stage != null) {
                stage.close();
            }
        }
    }

    @FXML
    void handleShowQr() {
        if (currentReservation == null) {
            showAlert(Alert.AlertType.INFORMATION, "QR Code", "Reservation data missing.");
            return;
        }
        CheckoutSummary summary = currentReservation.getSummary();
        if (summary == null) {
            showAlert(Alert.AlertType.INFORMATION, "QR Code", "Unable to generate QR code without checkout summary.");
            return;
        }
        String payload = buildQrPayload(currentReservation, summary);
        Image qrImage;
        try {
            qrImage = QrCodeGenerator.generateImage(payload, 8, 4);
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "QR Code", "Failed to generate QR code: " + ex.getMessage());
            return;
        }
        ImageView qrView = new ImageView(qrImage);
        qrView.setFitWidth(qrImage.getWidth());
        qrView.setFitHeight(qrImage.getHeight());
        qrView.setSmooth(false);
        qrView.setPreserveRatio(true);

        Label heading = new Label("Present this QR code at the ticket counter");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label codeLabel = new Label(currentReservation.getConfirmationCode());
        codeLabel.setStyle("-fx-text-fill: #555555;");

        VBox box = new VBox(12, heading, qrView, codeLabel);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20));

        Scene scene = new Scene(box);
        Stage stage = new Stage();
        stage.setTitle("QR Code");
        if (closeButton != null && closeButton.getScene() != null) {
            stage.initOwner(closeButton.getScene().getWindow());
        }
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }

    @FXML
    void handleEmailQr() {
        if (currentReservation == null) {
            showAlert(Alert.AlertType.INFORMATION, "Send QR", "Reservation data missing.");
            return;
        }
        CheckoutSummary summary = currentReservation.getSummary();
        if (summary == null) {
            showAlert(Alert.AlertType.INFORMATION, "Send QR", "Unable to generate QR code without checkout summary.");
            return;
        }
        User user = currentReservation.getUser();
        String email = user != null ? user.getEmail() : null;
        if (email == null || email.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Send QR", "No email address is associated with this reservation.");
            return;
        }
        String payload = buildQrPayload(currentReservation, summary);
        Image qrImage;
        try {
            qrImage = QrCodeGenerator.generateImage(payload, 8, 4);
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Send QR", "Failed to generate QR code: " + ex.getMessage());
            return;
        }
        String movieTitle = summary.getMovie() != null ? summary.getMovie().getTitle() : "your ticket";
        String subject = "QR Code for " + movieTitle;
        String friendlyName = user != null && user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName()
                : "Moviegoer";
        String rawBody = "Hi " + friendlyName + ",\n\n"
                + "We've prepared the QR ticket for " + movieTitle + ".\n"
                + "Confirmation Code: " + currentReservation.getConfirmationCode() + "\n"
                + "Enjoy your movie!";
        Path imagePath = null;
        try {
            imagePath = saveQrImage(qrImage);
            EmailService.sendTicketEmail(email, subject, rawBody, imagePath);
            showAlert(Alert.AlertType.INFORMATION, "Send QR", "QR code emailed to " + email + ".");
        } catch (IllegalStateException ex) {
            showAlert(Alert.AlertType.ERROR, "Send QR", ex.getMessage());
        } catch (IOException ex) {
            showAlert(Alert.AlertType.ERROR, "Send QR", "Unable to send QR code: " + ex.getMessage());
        } finally {
            if (imagePath != null) {
                try {
                    Files.deleteIfExists(imagePath);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String buildQrPayload(Reservation reservation, CheckoutSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("CONF:").append(reservation.getConfirmationCode()).append('\n');
        Movie movie = summary.getMovie();
        if (movie != null && movie.getTitle() != null) {
            sb.append("MOV:").append(movie.getTitle()).append('\n');
        }
        Showtime st = summary.getShowtime();
        if (st != null) {
            sb.append("BR:").append(st.getBranchName()).append('\n');
            if (st.getShowDate() != null) {
                sb.append("DT:").append(st.getShowDate()).append('\n');
            }
            if (st.getShowTime() != null) {
                sb.append("TM:").append(st.getShowTime()).append('\n');
            }
        }
        sb.append("SEATS:").append(String.join(",", summary.getSeats())).append('\n');
        sb.append("TOTAL:").append(summary.getGrandTotal());
        return sb.toString();
    }

    private Path saveQrImage(Image image) throws IOException {
        Path destination = Files.createTempFile("movie-qr-", ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", destination.toFile());
        return destination;
    }

    private void showAlert(Alert.AlertType type, String heading, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(heading);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
