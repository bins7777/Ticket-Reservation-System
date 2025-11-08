package movieticketreservation;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.Parent;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import movieticketreservation.dao.ShowtimeDAO;
import movieticketreservation.model.CheckoutSummary;
import movieticketreservation.model.Showtime;
import movieticketreservation.model.Movie;
import movieticketreservation.user.User;

import java.net.URL;
import java.util.ResourceBundle;

public class MovieTicketListController implements Initializable {

    @FXML private ImageView posterImage;
    @FXML private Text movieTitle;
    @FXML private Text movieMetadata;
    @FXML private Text movieRuntime;
    @FXML private TextArea movieSynopsis;
    @FXML private Text ticketPriceText;
    @FXML private TextField branchSearchField;
    @FXML private TableView<ShowtimeRow> showtimeTable;
    @FXML private TableColumn<ShowtimeRow, String> priceColumn;
    @FXML private TableColumn<ShowtimeRow, String> branchColumn;
    @FXML private TableColumn<ShowtimeRow, String> dateColumn;
    @FXML private TableColumn<ShowtimeRow, String> timeColumn;
    @FXML private TableColumn<ShowtimeRow, Number> seatsColumn;
    @FXML private Button selectSeatsButton;

    private final ShowtimeDAO showtimeDAO = new ShowtimeDAO();

    private Stage stage;
    private Scene dashboardScene;
    private Runnable backAction;
    private Consumer<CheckoutSummary> checkoutHandler;
    private User loggedInUser;

    private Movie movie;
    private final ObservableList<ShowtimeRow> tableItems = FXCollections.observableArrayList();
    private final List<ShowtimeRow> allShowtimes = new ArrayList<>();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DecimalFormat PRICE_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setCurrencySymbol("₱");
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        PRICE_FORMAT = new DecimalFormat("₱ ###,##0.00", symbols);
        PRICE_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (priceColumn != null) {
            priceColumn.setCellValueFactory(data -> data.getValue().priceProperty());
        }
        branchColumn.setCellValueFactory(data -> data.getValue().branchProperty());
        dateColumn.setCellValueFactory(data -> data.getValue().dateProperty());
        timeColumn.setCellValueFactory(data -> data.getValue().timeProperty());
        seatsColumn.setCellValueFactory(data -> data.getValue().seatsProperty());

        showtimeTable.setItems(tableItems);
        showtimeTable.setRowFactory(table -> {
            TableRow<ShowtimeRow> row = new TableRow<>();
            row.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openSeatSelection(row.getItem());
                }
            });
            return row;
        });

        showtimeTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateSelectSeatsButton(newVal));

        branchSearchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter(newVal));
        updateSelectSeatsButton(null);
    }

    public void setContext(Stage stage, Scene dashboardScene) {
        this.stage = stage;
        this.dashboardScene = dashboardScene;
    }

    public void setBackAction(Runnable backAction) {
        this.backAction = backAction;
    }

    public void setCheckoutHandler(Consumer<CheckoutSummary> checkoutHandler) {
        this.checkoutHandler = checkoutHandler;
    }

    public void setLoggedInUser(User user) {
        this.loggedInUser = user;
    }

    public void displayMovie(Movie movie) {
        this.movie = movie;
        updateMovieDetails();
        loadShowtimes();
    }

    @FXML
    void handleSelectSeats(ActionEvent event) {
        ShowtimeRow selected = showtimeTable.getSelectionModel().getSelectedItem();
        openSeatSelection(selected);
    }

    private void updateMovieDetails() {
        if (movie == null) {
            if (ticketPriceText != null) {
                ticketPriceText.setText("Ticket Price: -");
            }
            return;
        }
        movieTitle.setText(movie.getTitle());
        movieMetadata.setText(movie.getReleaseYear() + " | " + movie.getGenres());
        movieRuntime.setText("Run Time: " + movie.getRuntimeFormatted());
        movieSynopsis.setText(movie.getSynopsis() == null ? "" : movie.getSynopsis());
        if (ticketPriceText != null) {
            ticketPriceText.setText("Ticket Price: Loading...");
        }
        loadPosterImage(movie.getPosterPath());
    }

    private void loadPosterImage(String posterPath) {
        posterImage.setImage(null);
        if (posterPath == null || posterPath.isEmpty()) {
            return;
        }
        String resourcePath = posterPath.startsWith("/") ? posterPath : "/movieticketreservation/" + posterPath;
        try (InputStream stream = getClass().getResourceAsStream(resourcePath)) {
            if (stream != null) {
                posterImage.setImage(new Image(stream));
            }
        } catch (Exception ignored) {
        }
    }

    private void loadShowtimes() {
        allShowtimes.clear();
        tableItems.clear();
        if (movie == null) {
            updateTicketPriceSummary();
            return;
        }
        try {
            List<Showtime> showtimes = showtimeDAO.findByMovie(movie.getId());
            for (Showtime st : showtimes) {
                allShowtimes.add(new ShowtimeRow(st));
            }
            updateTicketPriceSummary();
            applyFilter(branchSearchField.getText());
        } catch (SQLException ex) {
            // Showtimes remain empty; optional logging could go here.
            showError("Unable to load showtimes", ex.getMessage());
            updateTicketPriceSummary();
        }
    }

    private void applyFilter(String filterText) {
        String term = filterText == null ? "" : filterText.trim().toLowerCase();
        List<ShowtimeRow> filtered = new ArrayList<>();
        for (ShowtimeRow row : allShowtimes) {
            String branchName = row.branchProperty().get();
            if (term.isEmpty() || (branchName != null && branchName.toLowerCase().contains(term))) {
                filtered.add(row);
            }
        }
        tableItems.setAll(filtered);
    }

    @FXML
    void handleBack(ActionEvent event) {
        if (backAction != null) {
            backAction.run();
            return;
        }
        Stage currentStage = resolveStage();
        if (currentStage == null) {
            return;
        }
        if (dashboardScene != null) {
            currentStage.setScene(dashboardScene);
            currentStage.centerOnScreen();
        } else {
            currentStage.close();
        }
    }

    @FXML
    void handleClose(ActionEvent event) {
        Stage currentStage = resolveStage();
        if (currentStage != null) {
            currentStage.close();
        }
    }

    @FXML
    void handleMinimize(ActionEvent event) {
        Stage currentStage = resolveStage();
        if (currentStage != null) {
            currentStage.setIconified(true);
        }
    }

    private void openSeatSelection(ShowtimeRow row) {
        if (row == null || row.getShowtime() == null) {
            return;
        }
        Stage currentStage = resolveStage();
        if (currentStage == null || movie == null) {
            showError("Seat selection unavailable", "Please choose a movie and schedule first.");
            return;
        }

        Scene originScene = currentStage.getScene();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("seatSelection.fxml"));
            Parent seatRoot = loader.load();
            SeatSelectionController controller = loader.getController();
            Scene seatScene = new Scene(seatRoot);
            seatScene.setFill(Color.TRANSPARENT);

            currentStage.setScene(seatScene);
            currentStage.centerOnScreen();
            currentStage.show();

            if (controller != null) {
                boolean autoPick = loggedInUser != null && loggedInUser.isAutoPickBestSeat();
                controller.setAutoPickPreference(autoPick);
                controller.configure(
                        currentStage,
                        originScene,
                        movie,
                        row.getShowtime(),
                        row.getShowtime().getTicketPrice(),
                        checkoutHandler
                );
            }
        } catch (Exception ex) {
            currentStage.setScene(originScene);
            showError("Unable to open seat selection", ex.getMessage());
        }
    }

    private void updateSelectSeatsButton(ShowtimeRow selection) {
        if (selectSeatsButton != null) {
            selectSeatsButton.setDisable(selection == null);
        }
    }

    private Stage resolveStage() {
        if (stage != null) {
            return stage;
        }
        if (movieTitle != null && movieTitle.getScene() != null && movieTitle.getScene().getWindow() instanceof Stage) {
            stage = (Stage) movieTitle.getScene().getWindow();
            return stage;
        }
        return null;
    }

    private void showError(String heading, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        Stage currentStage = resolveStage();
        if (currentStage != null) {
            alert.initOwner(currentStage);
        }
        alert.setHeaderText(heading);
        alert.setContentText(message);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();
    }

    private void updateTicketPriceSummary() {
        if (ticketPriceText == null) {
            return;
        }
        if (allShowtimes.isEmpty()) {
            ticketPriceText.setText("Ticket Price: -");
            return;
        }
        BigDecimal min = null;
        BigDecimal max = null;
        for (ShowtimeRow row : allShowtimes) {
            Showtime st = row.getShowtime();
            if (st == null) continue;
            BigDecimal price = st.getTicketPrice();
            if (price == null) continue;
            if (min == null || price.compareTo(min) < 0) {
                min = price;
            }
            if (max == null || price.compareTo(max) > 0) {
                max = price;
            }
        }
        if (min == null) {
            ticketPriceText.setText("Ticket Price: -");
        } else if (min.compareTo(max) == 0) {
            ticketPriceText.setText("Ticket Price: " + formatCurrency(min));
        } else {
            ticketPriceText.setText("Ticket Price: " + formatCurrency(min) + " - " + formatCurrency(max));
        }
    }

    private static String formatCurrency(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        synchronized (PRICE_FORMAT) {
            return PRICE_FORMAT.format(normalized);
        }
    }

    private static class ShowtimeRow {
        private final Showtime showtime;
        private final SimpleStringProperty price = new SimpleStringProperty();
        private final SimpleStringProperty branch = new SimpleStringProperty();
        private final SimpleStringProperty date = new SimpleStringProperty();
        private final SimpleStringProperty time = new SimpleStringProperty();
        private final SimpleIntegerProperty seats = new SimpleIntegerProperty();

        ShowtimeRow(Showtime showtime) {
            this.showtime = showtime;
            if (showtime != null) {
                BigDecimal ticketPrice = showtime.getTicketPrice();
                this.price.set(formatCurrency(ticketPrice));
                this.branch.set(showtime.getBranchName());
                this.date.set(showtime.getShowDate().format(DATE_FORMATTER));
                this.time.set(showtime.getShowTime().format(TIME_FORMATTER));
                this.seats.set(showtime.getAvailableSeats());
            }
        }

        SimpleStringProperty priceProperty() {
            return price;
        }

        SimpleStringProperty branchProperty() {
            return branch;
        }

        SimpleStringProperty dateProperty() {
            return date;
        }

        SimpleStringProperty timeProperty() {
            return time;
        }

        SimpleIntegerProperty seatsProperty() {
            return seats;
        }

        Showtime getShowtime() {
            return showtime;
        }
    }
}
