package movieticketreservation;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.scene.input.MouseButton;
import javafx.util.Duration;
import movieticketreservation.dao.MovieDAO;
import movieticketreservation.model.CheckoutContext;
import movieticketreservation.model.CheckoutLineItem;
import movieticketreservation.model.CheckoutSummary;
import movieticketreservation.model.Movie;
import movieticketreservation.model.Showtime;
import movieticketreservation.model.Reservation;
import movieticketreservation.service.ReservationService;
import movieticketreservation.user.User;
import movieticketreservation.user.UserDAO;

public class DashboardController implements Initializable {

    @FXML private AnchorPane dashboardSummaryPane;
    @FXML private Button dashboard_button;
    @FXML private Button dashboard_close;
    @FXML private Button dashboard_logout;
    @FXML private Button dashboard_minimize;
    @FXML private Button dashboard_movieList;
    @FXML private Button dashboard_myReservations;
    @FXML private Button dashboard_paymentCheckout;
    @FXML private AnchorPane movieListPane;
    @FXML private FlowPane movieCardContainer;
    @FXML private AnchorPane paymentPane;
    @FXML private TextField movieList_searchMovie;
    @FXML private TableView<ReservationRow> reservationsTable;
    @FXML private TableColumn<ReservationRow, String> reservationTable_date;
    @FXML private TableColumn<ReservationRow, String> reservationTable_firstName;
    @FXML private TableColumn<ReservationRow, String> reservationTable_lastName;
    @FXML private TableColumn<ReservationRow, String> reservationTable_movieName;
    @FXML private TableColumn<ReservationRow, String> reservationTable_seat;
    @FXML private TableColumn<ReservationRow, String> reservationTable_time;
    @FXML private TableColumn<ReservationRow, Void> reservationTable_action;
    @FXML private AnchorPane reservationsPane;
    @FXML private Label paymentTitleLabel;
    @FXML private Label paymentSubtitleLabel;
    @FXML private Label paymentMovieDetailsLabel;
    @FXML private Label paymentSeatsLabel;
    @FXML private Label paymentTicketPriceLabel;
    @FXML private Label paymentTicketQuantityLabel;
    @FXML private VBox paymentFoodItemsBox;
    @FXML private Label paymentNoAddonsLabel;
    @FXML private Label paymentTotalAmountLabel;
    @FXML private FlowPane paymentMethodsFlow;
    @FXML private ToggleButton paymentMethodGcash;
    @FXML private ToggleButton paymentMethodSmartBanq;
    @FXML private ToggleButton paymentMethodMaya;
    @FXML private ToggleButton paymentMethodGrabPay;
    @FXML private ToggleButton paymentMethodCoins;
    @FXML private ToggleButton paymentMethodSevenEleven;
    @FXML private Label paymentEmptyStateLabel;
    @FXML private Button paymentConfirmButton;
    @FXML private AnchorPane settingsPane;
    @FXML private TextField settingsFullNameField;
    @FXML private TextField settingsEmailField;
    @FXML private TextField settingsPhoneField;
    @FXML private ToggleButton settingsAutoPickToggle;
    @FXML private Button settingsSaveButton;
    @FXML private Label settingsStatusLabel;
    @FXML private ImageView carouselImage;
    @FXML private Label totalReservedTicketsLabel;
    @FXML private Label highlightMoviesCount;
    @FXML private Label highlightAutoPickStatus;

    private final MovieDAO movieDAO = new MovieDAO();
    private final UserDAO userDAO = new UserDAO();

    private Stage stage;
    private Scene dashboardScene;
    private Runnable logoutHandler;
    private List<AnchorPane> sections = Collections.emptyList();
    private List<Movie> allMovies = Collections.emptyList();
    private List<Movie> carouselMovies = Collections.emptyList();
    private CheckoutSummary activeCheckout;
    private User loggedInUser;
    private boolean processingPayment;
    private Timeline carouselTimeline;
    private int carouselIndex;

    private final ToggleGroup paymentToggleGroup = new ToggleGroup();
    private final ObservableList<ReservationRow> reservationRows = FXCollections.observableArrayList();

    private static final DecimalFormat CURRENCY_FORMAT;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ENGLISH);
        symbols.setDecimalSeparator('.');
        symbols.setGroupingSeparator(',');
        CURRENCY_FORMAT = new DecimalFormat("###,##0.00", symbols);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        sections = List.of(dashboardSummaryPane, movieListPane, reservationsPane, paymentPane, settingsPane);
        showSection(dashboardSummaryPane);
        if (movieList_searchMovie != null) {
            movieList_searchMovie.textProperty().addListener((obs, oldValue, newValue) -> updateDisplayedMovies(newValue));
        }
        configurePaymentMethodButtons();
        updatePaymentUI(null);
        configureReservationsTable();
        configureSettingsToggle();
        loadMovies();
    }

    /** Optional: call this from the place where you load the FXML to enable close/minimize. */
    public void setStage(Stage stage) {
        this.stage = stage;
        if (stage != null) {
            this.dashboardScene = stage.getScene();
        }
    }

    /** Optional: supply a logout action (e.g., go back to login scene). */
    public void setLogoutHandler(Runnable logoutHandler) {
        this.logoutHandler = logoutHandler;
    }

    public void setLoggedInUser(User user) {
        this.loggedInUser = user;
        refreshReservations();
        populateSettingsForm();
        updateHighlights();
    }

    private void configureReservationsTable() {
        if (reservationsTable == null) {
            return;
        }
        reservationsTable.setItems(reservationRows);
        reservationsTable.setPlaceholder(new Label("No reservations yet."));
        if (reservationTable_lastName != null) {
            reservationTable_lastName.setCellValueFactory(data -> data.getValue().lastNameProperty());
        }
        if (reservationTable_firstName != null) {
            reservationTable_firstName.setCellValueFactory(data -> data.getValue().firstNameProperty());
        }
        if (reservationTable_movieName != null) {
            reservationTable_movieName.setCellValueFactory(data -> data.getValue().movieProperty());
        }
        if (reservationTable_date != null) {
            reservationTable_date.setCellValueFactory(data -> data.getValue().dateProperty());
        }
        if (reservationTable_time != null) {
            reservationTable_time.setCellValueFactory(data -> data.getValue().timeProperty());
        }
        if (reservationTable_seat != null) {
            reservationTable_seat.setCellValueFactory(data -> data.getValue().seatsProperty());
        }
        if (reservationTable_action != null) {
            reservationTable_action.setCellFactory(col -> new TableCell<>() {
                private final Button viewButton = new Button("View");
                {
                    viewButton.getStyleClass().add("primary-button");
                    viewButton.setMinWidth(70);
                    viewButton.setPrefHeight(28);
                    viewButton.setFocusTraversable(false);
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        setGraphic(viewButton);
                        viewButton.setOnAction(evt -> {
                            ReservationRow row = getTableView().getItems().get(getIndex());
                            if (row != null) {
                                openReservationDetails(row.getReservation());
                            }
                        });
                    }
                }
            });
            reservationTable_action.setSortable(false);
        }
        reservationsTable.setRowFactory(table -> {
            TableRow<ReservationRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2
                        && event.getButton() == MouseButton.PRIMARY
                        && !row.isEmpty()) {
                    openReservationDetails(row.getItem().getReservation());
                }
            });
            return row;
        });
    }

    private void refreshReservations() {
        if (reservationsTable == null) {
            return;
        }
        if (loggedInUser == null) {
            reservationRows.clear();
            updateHighlights();
            return;
        }
        try {
            List<Reservation> reservations = ReservationService.findByUser(loggedInUser);
            List<ReservationRow> rows = new ArrayList<>();
            for (Reservation reservation : reservations) {
                rows.add(new ReservationRow(reservation));
            }
            reservationRows.setAll(rows);
            updateHighlights();
        } catch (SQLException ex) {
            showError("Unable to load reservations", ex.getMessage());
        }
    }

    private void openReservationDetails(Reservation reservation) {
        if (reservation == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("reservationDetails.fxml"));
            Parent detailRoot = loader.load();
            ReservationDetailsController controller = loader.getController();
            if (controller != null) {
                controller.configure(reservation);
            }
            Stage detailStage = new Stage();
            detailStage.setTitle("Reservation Details");
            detailStage.initOwner(resolveStage());
            detailStage.setScene(new Scene(detailRoot));
            detailStage.setResizable(false);
            detailStage.centerOnScreen();
            detailStage.show();
        } catch (IOException ex) {
            showError("Unable to open reservation details", ex.getMessage());
        }
    }

    private void loadMovies() {
        if (movieCardContainer == null) {
            return;
        }
        try {
            List<Movie> movies = movieDAO.findAll();
            allMovies = filterMoviesForDisplay(movies);
            String currentQuery = movieList_searchMovie != null ? movieList_searchMovie.getText() : null;
            updateDisplayedMovies(currentQuery);
            refreshCarouselContent();
            updateHighlights();
        } catch (SQLException ex) {
            movieCardContainer.getChildren().clear();
            showError("Unable to load movies", ex.getMessage());
            refreshCarouselContent();
            updateHighlights();
        }
    }

    private void updateDisplayedMovies(String query) {
        if (movieCardContainer == null) {
            return;
        }
        movieCardContainer.getChildren().clear();
        List<Movie> filtered = filterMoviesByQuery(allMovies, query);
        if (filtered.isEmpty()) {
            String message = (query == null || normalizeForSearch(query).isEmpty())
                    ? "No movies available yet."
                    : "No movies found for \"" + query.trim() + "\".";
            movieCardContainer.getChildren().add(createEmptyState(message));
            return;
        }
        for (Movie movie : filtered) {
            movieCardContainer.getChildren().add(createMovieCard(movie));
        }
    }

    private List<Movie> filterMoviesByQuery(List<Movie> movies, String query) {
        if (movies == null || movies.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedQuery = normalizeForSearch(query);
        if (normalizedQuery.isEmpty()) {
            return new ArrayList<>(movies);
        }
        String[] tokens = normalizedQuery.split("\\s+");
        List<Movie> filtered = new ArrayList<>();
        for (Movie movie : movies) {
            if (movie == null) {
                continue;
            }
            if (matchesTokens(movie, tokens)) {
                filtered.add(movie);
            }
        }
        return filtered;
    }

    private boolean matchesTokens(Movie movie, String[] tokens) {
        if (tokens == null || tokens.length == 0) {
            return true;
        }
        for (String candidate : buildSearchableValues(movie)) {
            String normalizedCandidate = normalizeForSearch(candidate);
            if (normalizedCandidate.isEmpty()) {
                continue;
            }
            boolean allMatch = true;
            for (String token : tokens) {
                if (!normalizedCandidate.contains(token)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildSearchableValues(Movie movie) {
        if (movie == null) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>(5);
        if (movie.getTitle() != null) {
            values.add(movie.getTitle());
        }
        if (movie.getGenres() != null) {
            values.add(movie.getGenres());
        }
        if (movie.getSynopsis() != null) {
            values.add(movie.getSynopsis());
        }
        if (movie.getSlug() != null) {
            values.add(movie.getSlug());
        }
        if (movie.getReleaseYear() > 0) {
            values.add(String.valueOf(movie.getReleaseYear()));
        }
        return values;
    }

    private String normalizeForSearch(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        String ascii = normalized.replaceAll("\\p{M}+", "");
        String sanitized = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        return sanitized;
    }

    private VBox createEmptyState(String message) {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPrefWidth(200);
        Text text = new Text(message);
        text.setTextAlignment(TextAlignment.CENTER);
        box.getChildren().add(text);
        return box;
    }

    private VBox createMovieCard(Movie movie) {
        VBox card = new VBox(12);
        card.setAlignment(Pos.TOP_CENTER);
        card.getStyleClass().add("movie-card");
        card.setMinWidth(220);
        card.setPrefWidth(220);
        card.setMaxWidth(220);

        ImageView poster = new ImageView();
        poster.setFitWidth(188);
        poster.setPreserveRatio(true);
        poster.setSmooth(true);
        poster.setCache(true);
        loadPosterImage(movie.getPosterPath(), poster);

        Text title = new Text(movie.getTitle());
        title.setTextAlignment(TextAlignment.CENTER);
        title.setWrappingWidth(188);
        title.getStyleClass().add("movie-card-title");

        Label meta = new Label(movie.getReleaseYear() + " | " + movie.getGenres());
        meta.setWrapText(true);
        meta.setAlignment(Pos.CENTER);
        meta.setMaxWidth(188);
        meta.setPrefWidth(188);
        meta.getStyleClass().add("movie-card-meta");

        Label runtime = new Label(movie.getRuntimeFormatted());
        runtime.setAlignment(Pos.CENTER);
        runtime.setMaxWidth(188);
        runtime.setPrefWidth(188);
        runtime.getStyleClass().add("movie-card-runtime");

        Button buyButton = new Button("Buy Tickets");
        buyButton.getStyleClass().add("login-btn");
        buyButton.setMaxWidth(Double.MAX_VALUE);
        buyButton.setOnAction(evt -> openTicketList(movie));

        card.getChildren().addAll(poster, title, meta, runtime, buyButton);
        return card;
    }

    private List<Movie> filterMoviesForDisplay(List<Movie> movies) {
        if (movies == null || movies.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Movie> bestByTitle = new LinkedHashMap<>();
        for (Movie movie : movies) {
            if (movie == null) {
                continue;
            }
            String canonicalKey = canonicalMovieKey(movie);
            if (canonicalKey.isEmpty()) {
                canonicalKey = "movie-" + movie.getId();
            }
            Movie existing = bestByTitle.get(canonicalKey);
            if (existing == null) {
                bestByTitle.put(canonicalKey, movie);
                continue;
            }
            if (isDifferentInstallment(existing, movie)) {
                String alternateKey = canonicalKey + "-" + dedupeSuffix(movie);
                Movie alternateExisting = bestByTitle.get(alternateKey);
                if (alternateExisting == null || scoreMovie(movie) > scoreMovie(alternateExisting)) {
                    bestByTitle.put(alternateKey, movie);
                }
                continue;
            }
            if (scoreMovie(movie) > scoreMovie(existing)) {
                bestByTitle.put(canonicalKey, movie);
            }
        }
        return new ArrayList<>(bestByTitle.values());
    }

    private String canonicalMovieKey(Movie movie) {
        String title = movie.getTitle();
        if (title != null && !title.isBlank()) {
            String slugged = slugify(title);
            if (!slugged.isEmpty()) {
                return slugged;
            }
        }
        String slug = movie.getSlug();
        if (slug != null && !slug.isBlank()) {
            return slug.trim().toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private String dedupeSuffix(Movie movie) {
        if (movie.getReleaseYear() > 0) {
            return String.valueOf(movie.getReleaseYear());
        }
        if (movie.getId() > 0) {
            return String.valueOf(movie.getId());
        }
        return "alt-" + Integer.toHexString(System.identityHashCode(movie));
    }

    private boolean isDifferentInstallment(Movie first, Movie second) {
        int firstYear = first != null ? first.getReleaseYear() : 0;
        int secondYear = second != null ? second.getReleaseYear() : 0;
        return firstYear > 0 && secondYear > 0 && firstYear != secondYear;
    }

    private int scoreMovie(Movie movie) {
        int score = 0;
        if (movie.getPosterPath() != null && !movie.getPosterPath().isBlank()) {
            score += 4;
        }
        if (movie.getSynopsis() != null && !movie.getSynopsis().isBlank()) {
            score += 2;
        }
        if (movie.getReleaseYear() > 0) {
            score += 1;
        }
        if (movie.getRuntimeMinutes() > 0) {
            score += 1;
        }
        if (movie.getGenres() != null && !movie.getGenres().isBlank()) {
            score += 1;
        }
        return score;
    }

    private String slugify(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        String ascii = normalized.replaceAll("\\p{M}+", "");
        String sanitized = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return sanitized;
    }

    private void loadPosterImage(String posterPath, ImageView poster) {
        if (poster == null) {
            return;
        }
        poster.setImage(null);
        if (posterPath == null || posterPath.isEmpty()) {
            return;
        }
        String resourcePath = posterPath.startsWith("/") ? posterPath : "/movieticketreservation/" + posterPath;
        try (InputStream stream = getClass().getResourceAsStream(resourcePath)) {
            if (stream != null) {
                poster.setImage(new Image(stream));
            }
        } catch (IOException ignored) {
            // Use default styling if image not found.
        }
    }

    private void refreshCarouselContent() {
        if (carouselImage == null) {
            return;
        }
        if (allMovies == null || allMovies.isEmpty()) {
            carouselMovies = Collections.emptyList();
            carouselIndex = 0;
            updateCarouselDisplay(null);
            stopCarouselTimeline();
            return;
        }
        carouselMovies = new ArrayList<>(allMovies);
        if (carouselIndex >= carouselMovies.size()) {
            carouselIndex = 0;
        }
        updateCarouselDisplay(carouselMovies.get(carouselIndex));
        restartCarouselTimeline();
    }

    private void restartCarouselTimeline() {
        stopCarouselTimeline();
        if (carouselMovies == null || carouselMovies.isEmpty()) {
            return;
        }
        carouselTimeline = new Timeline(new KeyFrame(Duration.seconds(5), evt -> showNextCarouselMovie()));
        carouselTimeline.setCycleCount(Timeline.INDEFINITE);
        carouselTimeline.play();
    }

    private void stopCarouselTimeline() {
        if (carouselTimeline != null) {
            carouselTimeline.stop();
            carouselTimeline = null;
        }
    }

    private void showNextCarouselMovie() {
        if (carouselMovies == null || carouselMovies.isEmpty()) {
            return;
        }
        carouselIndex = (carouselIndex + 1) % carouselMovies.size();
        updateCarouselDisplay(carouselMovies.get(carouselIndex));
    }

    private void updateCarouselDisplay(Movie movie) {
        if (carouselImage == null) {
            return;
        }
        if (movie != null) {
            loadPosterImage(movie.getPosterPath(), carouselImage);
        } else {
            carouselImage.setImage(null);
        }
    }

    private void configurePaymentMethodButtons() {
        List<ToggleButton> buttons = List.of(
                paymentMethodGcash,
                paymentMethodSmartBanq,
                paymentMethodMaya,
                paymentMethodGrabPay,
                paymentMethodCoins,
                paymentMethodSevenEleven
        );
        for (ToggleButton button : buttons) {
            if (button == null) continue;
            button.setToggleGroup(paymentToggleGroup);
        }
        paymentToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            updateConfirmAvailability();
            if (!processingPayment && newToggle instanceof ToggleButton && activeCheckout != null) {
                String label = extractPaymentLabel((ToggleButton) newToggle);
                finalizePayment(label);
            }
        });
        if (paymentConfirmButton != null) {
            paymentConfirmButton.setVisible(false);
            paymentConfirmButton.setManaged(false);
        }
        updateConfirmAvailability();
    }

    private String extractPaymentLabel(ToggleButton button) {
        if (button == null || button.getText() == null) {
            return "Payment";
        }
        String text = button.getText().trim();
        int newline = text.indexOf('\n');
        if (newline >= 0) {
            return text.substring(0, newline).trim();
        }
        return text;
    }

    private void updatePaymentUI(CheckoutSummary summary) {
        activeCheckout = summary;
        boolean hasSummary = summary != null;

        if (paymentEmptyStateLabel != null) {
            paymentEmptyStateLabel.setVisible(!hasSummary);
            paymentEmptyStateLabel.setManaged(!hasSummary);
        }
        if (paymentMethodsFlow != null) {
            paymentMethodsFlow.setDisable(!hasSummary);
        }

        if (!hasSummary) {
            if (paymentMovieDetailsLabel != null) {
                paymentMovieDetailsLabel.setText("Movie and schedule summary");
            }
            if (paymentSeatsLabel != null) {
                paymentSeatsLabel.setText("Seats: -");
            }
            if (paymentTicketPriceLabel != null) {
                paymentTicketPriceLabel.setText(formatCurrency(BigDecimal.ZERO));
            }
            if (paymentTicketQuantityLabel != null) {
                paymentTicketQuantityLabel.setText("x0");
            }
            if (paymentTotalAmountLabel != null) {
                paymentTotalAmountLabel.setText(formatCurrency(BigDecimal.ZERO));
            }
            if (paymentFoodItemsBox != null) {
                paymentFoodItemsBox.getChildren().clear();
                if (paymentNoAddonsLabel != null) {
                    paymentNoAddonsLabel.setText("No food items selected.");
                    paymentNoAddonsLabel.setVisible(true);
                    paymentNoAddonsLabel.setManaged(true);
                    paymentFoodItemsBox.getChildren().add(paymentNoAddonsLabel);
                }
            }
            paymentToggleGroup.selectToggle(null);
            updateConfirmAvailability();
            return;
        }

        Movie movie = summary.getMovie();
        Showtime showtime = summary.getShowtime();

        String movieTitle = movie != null && movie.getTitle() != null ? movie.getTitle() : "Untitled movie";
        String branchName = showtime != null && showtime.getBranchName() != null ? showtime.getBranchName() : "Branch TBD";
        String dateText = showtime != null && showtime.getShowDate() != null ? showtime.getShowDate().format(DATE_FORMAT) : "Date TBD";
        String timeText = showtime != null && showtime.getShowTime() != null ? showtime.getShowTime().format(TIME_FORMAT) : "Time TBD";

        if (paymentMovieDetailsLabel != null) {
            paymentMovieDetailsLabel.setText(String.format("%s | %s | %s %s | Total %s",
                    movieTitle,
                    branchName,
                    dateText,
                    timeText,
                    formatCurrency(summary.getGrandTotal())));
        }

        if (paymentSeatsLabel != null) {
            List<String> seats = summary.getSeats();
            String seatText = seats.isEmpty()
                    ? "Seats: -"
                    : "Seats: " + String.join(", ", seats) + " (" + summary.getTicketCount()
                    + (summary.getTicketCount() == 1 ? " ticket" : " tickets") + ")";
            paymentSeatsLabel.setText(seatText);
        }

        if (paymentTicketPriceLabel != null) {
            paymentTicketPriceLabel.setText(formatCurrency(summary.getTicketPrice()));
        }
        if (paymentTicketQuantityLabel != null) {
            paymentTicketQuantityLabel.setText("x" + summary.getTicketCount());
        }
        if (paymentTotalAmountLabel != null) {
            paymentTotalAmountLabel.setText(formatCurrency(summary.getGrandTotal()));
        }

        renderFoodItems(summary.getFoodItems());
        paymentToggleGroup.selectToggle(null);
        updateConfirmAvailability();
    }

    private void finalizePayment(String paymentLabel) {
        if (processingPayment) {
            return;
        }
        if (activeCheckout == null) {
            showInfo("No Order", "Select seats and concessions before choosing a payment partner.");
            return;
        }
        if (loggedInUser == null) {
            showError("Payment unavailable", "Login session missing. Please sign in again.");
            return;
        }

        processingPayment = true;
        try {
            Reservation reservation = ReservationService.createReservation(loggedInUser, activeCheckout, paymentLabel);
            CheckoutContext.clear();
            activeCheckout = null;
            updatePaymentUI(null);
            refreshReservations();
            showInfo("Payment Successful", "Reservation " + reservation.getConfirmationCode() + " has been added to My Reservations.");
            showSection(reservationsPane);
        } catch (Exception ex) {
            showError("Unable to complete payment", ex.getMessage());
        } finally {
            processingPayment = false;
        }
    }

    private void renderFoodItems(List<CheckoutLineItem> items) {
        if (paymentFoodItemsBox == null) {
            return;
        }
        paymentFoodItemsBox.getChildren().clear();
        if (items == null || items.isEmpty()) {
            if (paymentNoAddonsLabel != null) {
                paymentNoAddonsLabel.setText("No food items selected.");
                paymentNoAddonsLabel.setManaged(true);
                paymentNoAddonsLabel.setVisible(true);
                paymentFoodItemsBox.getChildren().add(paymentNoAddonsLabel);
            }
            return;
        }

        if (paymentNoAddonsLabel != null) {
            paymentNoAddonsLabel.setManaged(false);
            paymentNoAddonsLabel.setVisible(false);
        }

        for (CheckoutLineItem item : items) {
            if (item == null) continue;
            String labelText = String.format("%s x%d | %s",
                    item.getName(),
                    item.getQuantity(),
                    formatCurrency(item.getLineTotal()));
            Label lineLabel = new Label(labelText);
            lineLabel.getStyleClass().add("cart-entry-meta");
            lineLabel.setWrapText(true);
            paymentFoodItemsBox.getChildren().add(lineLabel);
        }
    }

    private void updateConfirmAvailability() {
        if (paymentConfirmButton != null) {
            boolean canConfirm = activeCheckout != null && paymentToggleGroup.getSelectedToggle() != null;
            paymentConfirmButton.setDisable(!canConfirm);
        }
    }

    private String formatCurrency(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount;
        synchronized (CURRENCY_FORMAT) {
            return "₱ " + CURRENCY_FORMAT.format(safe);
        }
    }

    public void showPaymentCheckout(CheckoutSummary summary) {
        if (summary != null) {
            CheckoutContext.setLatest(summary);
        }
        CheckoutSummary resolved = summary != null
                ? summary
                : CheckoutContext.getLatest().orElse(null);
        activeCheckout = resolved;

        Stage currentStage = stage != null ? stage : resolveStage();
        if (dashboardScene != null && currentStage != null && currentStage.getScene() != dashboardScene) {
            currentStage.setScene(dashboardScene);
            currentStage.centerOnScreen();
            currentStage.show();
        }
        updatePaymentUI(resolved);
        showSection(paymentPane);
    }

    // === Navigation helpers ===
    private void showSection(AnchorPane target) {
        if (sections == null) return;
        for (AnchorPane section : sections) {
            if (section == null) continue;
            boolean makeVisible = section == target;
            section.setVisible(makeVisible);
            if (makeVisible) section.toFront();
        }
    }

    private Stage resolveStage() {
        if (stage != null) {
            return stage;
        }
        if (dashboard_button != null && dashboard_button.getScene() != null) {
            Stage resolved = (Stage) dashboard_button.getScene().getWindow();
            if (resolved != null) {
                stage = resolved;
                if (dashboardScene == null) {
                    dashboardScene = resolved.getScene();
                }
            }
            return resolved;
        }
        return null;
    }

    private void openTicketList(Movie movie) {
        Stage targetStage = resolveStage();
        if (targetStage == null || movie == null) {
            return;
        }

        Scene originScene = dashboardScene != null ? dashboardScene : targetStage.getScene();
        if (originScene == null) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("movieTicketList.fxml"));
            Parent ticketRoot = loader.load();
            MovieTicketListController controller = loader.getController();
            Scene ticketScene = new Scene(ticketRoot);
            ticketScene.setFill(Color.TRANSPARENT);

            targetStage.setScene(ticketScene);
            targetStage.centerOnScreen();
            targetStage.show();

            dashboardScene = originScene;
            if (controller != null) {
                controller.setContext(targetStage, originScene);
                controller.setBackAction(() -> {
                    targetStage.setScene(originScene);
                    targetStage.centerOnScreen();
                    targetStage.show();
                });
                controller.setCheckoutHandler(this::showPaymentCheckout);
                controller.setLoggedInUser(loggedInUser);
                controller.displayMovie(movie);
            }
        } catch (IOException ex) {
            showError("Unable to open movie details", ex.getMessage());
        }
    }

    @FXML
    void handleClose(ActionEvent event) {
        Stage targetStage = resolveStage();
        if (targetStage != null) {
            targetStage.close();
        }
    }

    @FXML
    void handleLogout(ActionEvent event) {
        if (logoutHandler != null) logoutHandler.run();
    }

    @FXML
    void handleMinimize(ActionEvent event) {
        Stage targetStage = resolveStage();
        if (targetStage != null) {
            targetStage.setIconified(true);
        }
    }

    @FXML
    void handleConfirmPayment(ActionEvent event) {
        if (activeCheckout == null) {
            showError("Checkout not ready", "Select seats and complete the order before confirming payment.");
            return;
        }
        Toggle selected = paymentToggleGroup.getSelectedToggle();
        if (!(selected instanceof ToggleButton)) {
            showError("Select a payment method", "Please choose a payment partner to continue.");
            return;
        }
        ToggleButton button = (ToggleButton) selected;
        String label = button.getText() == null ? "your chosen provider" : button.getText().split("\\R", 2)[0];

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        Stage owner = resolveStage();
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setHeaderText("Payment Initiated");
        alert.setContentText("We will redirect you to " + label + " to complete the payment of "
                + formatCurrency(activeCheckout.getGrandTotal()) + ".");
        alert.showAndWait();
    }

    @FXML
    void showDashboard(ActionEvent event) {
        showSection(dashboardSummaryPane);
    }

    @FXML
    void showMovieList(ActionEvent event) {
        showSection(movieListPane);
        loadMovies();
    }

    @FXML
    void showPayment(ActionEvent event) {
        showSection(paymentPane);
        CheckoutSummary resolved = activeCheckout != null
                ? activeCheckout
                : CheckoutContext.getLatest().orElse(null);
        updatePaymentUI(resolved);
    }

    @FXML
    void showSettings(ActionEvent event) {
        populateSettingsForm();
        showSection(settingsPane);
    }

    @FXML
    void handleSaveSettings(ActionEvent event) {
        if (loggedInUser == null) {
            updateSettingsStatus("Please log in again to update profile.", true);
            return;
        }
        String fullName = sanitizeFullName(settingsFullNameField != null ? settingsFullNameField.getText() : "");
        String email = settingsEmailField != null ? settingsEmailField.getText().trim() : "";
        String phone = settingsPhoneField != null ? settingsPhoneField.getText().trim() : "";
        boolean autoPick = settingsAutoPickToggle != null && settingsAutoPickToggle.isSelected();

        if (fullName.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            updateSettingsStatus("Full name, email, and phone are required.", true);
            return;
        }

        loggedInUser.setFullName(fullName);
        loggedInUser.setEmail(email);
        loggedInUser.setPhone(phone);
        loggedInUser.setAutoPickBestSeat(autoPick);

        try {
            boolean saved = userDAO.updateProfile(loggedInUser);
            if (saved) {
                updateSettingsStatus("Profile updated.", false);
                updateHighlights();
            } else {
                updateSettingsStatus("Nothing was updated. Please try again.", true);
            }
        } catch (SQLException ex) {
            updateSettingsStatus("Unable to save changes: " + ex.getMessage(), true);
        }
    }

    private static String sanitizeFullName(String fullName) {
        return fullName == null ? "" : fullName.trim();
    }

    private void configureSettingsToggle() {
        if (settingsAutoPickToggle != null) {
            settingsAutoPickToggle.selectedProperty().addListener((obs, oldVal, newVal) -> updateSettingsToggleLabel(newVal));
            updateSettingsToggleLabel(settingsAutoPickToggle.isSelected());
        }
    }

    private void populateSettingsForm() {
        if (settingsFullNameField == null || settingsEmailField == null || settingsPhoneField == null) {
            return;
        }
        if (loggedInUser == null) {
            settingsFullNameField.setText("");
            settingsEmailField.setText("");
            settingsPhoneField.setText("");
            if (settingsAutoPickToggle != null) {
                settingsAutoPickToggle.setSelected(false);
            }
            updateSettingsStatus("No user profile loaded.", true);
            return;
        }
        settingsFullNameField.setText(sanitizeFullName(loggedInUser.getFullName()));
        settingsEmailField.setText(loggedInUser.getEmail() == null ? "" : loggedInUser.getEmail());
        settingsPhoneField.setText(loggedInUser.getPhone() == null ? "" : loggedInUser.getPhone());
        if (settingsAutoPickToggle != null) {
            settingsAutoPickToggle.setSelected(loggedInUser.isAutoPickBestSeat());
            updateSettingsToggleLabel(settingsAutoPickToggle.isSelected());
        }
        updateSettingsStatus("", false);
        updateHighlights();
    }

    private void updateSettingsStatus(String message, boolean isError) {
        if (settingsStatusLabel == null) {
            return;
        }
        String trimmed = message == null ? "" : message.trim();
        settingsStatusLabel.setText(trimmed);
        if (trimmed.isEmpty()) {
            settingsStatusLabel.setText("");
        }
        if (isError) {
            settingsStatusLabel.setTextFill(Color.web("#f87171"));
        } else {
            settingsStatusLabel.setTextFill(Color.web("#4ade80"));
        }
    }

    private void updateHighlights() {
        if (highlightMoviesCount != null) {
            int movieCount = allMovies == null ? 0 : allMovies.size();
            highlightMoviesCount.setText(Integer.toString(movieCount));
        }
        if (highlightAutoPickStatus != null) {
            boolean enabled = loggedInUser != null && loggedInUser.isAutoPickBestSeat();
            highlightAutoPickStatus.setText(enabled ? "Auto-pick ON" : "Auto-pick OFF");
        }
        if (totalReservedTicketsLabel != null) {
            totalReservedTicketsLabel.setText(Integer.toString(reservationRows.size()));
        }
    }

    private void updateSettingsToggleLabel(boolean enabled) {
        if (settingsAutoPickToggle != null) {
            settingsAutoPickToggle.setText(enabled ? "Auto pick best seat: ON" : "Auto pick best seat: OFF");
        }
    }

    private class ReservationRow {
        private final Reservation reservation;
        private final SimpleStringProperty firstName = new SimpleStringProperty("");
        private final SimpleStringProperty lastName = new SimpleStringProperty("");
        private final SimpleStringProperty movie = new SimpleStringProperty("");
        private final SimpleStringProperty date = new SimpleStringProperty("");
        private final SimpleStringProperty time = new SimpleStringProperty("");
        private final SimpleStringProperty seats = new SimpleStringProperty("");

        ReservationRow(Reservation reservation) {
            this.reservation = reservation;
            if (reservation == null) {
                return;
            }
            User user = reservation.getUser();
            if (user != null) {
                String name = sanitizeFullName(user.getFullName());
                if (!name.isEmpty()) {
                    String[] parts = name.split("\\s+");
                    if (parts.length == 1) {
                        firstName.set(parts[0]);
                        lastName.set(parts[0]);
                    } else {
                        firstName.set(String.join(" ", Arrays.copyOf(parts, parts.length - 1)));
                        lastName.set(parts[parts.length - 1]);
                    }
                } else if (user.getUsername() != null) {
                    firstName.set(user.getUsername());
                    lastName.set(user.getUsername());
                }
            }

            CheckoutSummary summary = reservation.getSummary();
            if (summary != null) {
                Movie movieModel = summary.getMovie();
                if (movieModel != null) {
                    movie.set(movieModel.getTitle());
                }
                Showtime st = summary.getShowtime();
                if (st != null) {
                    if (st.getShowDate() != null) {
                        date.set(st.getShowDate().format(DATE_FORMAT));
                    }
                    if (st.getShowTime() != null) {
                        time.set(st.getShowTime().format(TIME_FORMAT));
                    }
                }
                List<String> seatList = summary.getSeats();
                seats.set(seatList.isEmpty() ? "-" : String.join(", ", seatList));
            }
            if (date.get().isEmpty() || time.get().isEmpty()) {
                if (reservation.getPurchaseDateTime() != null) {
                    date.set(reservation.getPurchaseDateTime().toLocalDate().format(DATE_FORMAT));
                    time.set(reservation.getPurchaseDateTime().toLocalTime().format(TIME_FORMAT));
                }
            }
            if (firstName.get().isEmpty()) {
                firstName.set("Guest");
            }
            if (lastName.get().isEmpty()) {
                lastName.set("-");
            }
            if (movie.get().isEmpty()) {
                movie.set("Movie");
            }
            if (date.get().isEmpty()) {
                date.set("-");
            }
            if (time.get().isEmpty()) {
                time.set("-");
            }
            if (seats.get().isEmpty()) {
                seats.set("-");
            }
        }

        SimpleStringProperty firstNameProperty() { return firstName; }
        SimpleStringProperty lastNameProperty() { return lastName; }
        SimpleStringProperty movieProperty() { return movie; }
        SimpleStringProperty dateProperty() { return date; }
        SimpleStringProperty timeProperty() { return time; }
        SimpleStringProperty seatsProperty() { return seats; }
        Reservation getReservation() { return reservation; }
    }

    @FXML
    void showReservations(ActionEvent event) {
        refreshReservations();
        showSection(reservationsPane);
    }

    private void showInfo(String heading, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        Stage owner = resolveStage();
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setHeaderText(heading);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String heading, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(heading);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
