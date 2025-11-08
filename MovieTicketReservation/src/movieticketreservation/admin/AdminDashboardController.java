package movieticketreservation.admin;

import movieticketreservation.dao.FoodItemDAO;
import movieticketreservation.dao.MovieDAO;
import movieticketreservation.dao.ShowtimeDAO;
import movieticketreservation.model.AdminAccount;
import movieticketreservation.model.CheckoutSummary;
import movieticketreservation.model.FoodItem;
import movieticketreservation.model.Movie;
import movieticketreservation.model.Reservation;
import movieticketreservation.model.Showtime;
import movieticketreservation.service.ReservationService;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

import movieticketreservation.user.User;
import movieticketreservation.util.SeatCapacity;

public class AdminDashboardController implements Initializable {

    @FXML private Label branchLabel;
    @FXML private Label adminHeader;

    @FXML private TextField movieTitleField;
    @FXML private TextField movieSlugField;
    @FXML private TextField movieYearField;
    @FXML private TextField movieRuntimeField;
    @FXML private TextField movieGenresField;
    @FXML private TextField moviePosterField;
    @FXML private TextArea movieSynopsisArea;

    @FXML private TableView<Movie> moviesTable;
    @FXML private TableColumn<Movie, String> movieTitleColumn;
    @FXML private TableColumn<Movie, Number> movieYearColumn;
    @FXML private TableColumn<Movie, String> movieGenresColumn;
    @FXML private TableColumn<Movie, String> movieRuntimeColumn;

    @FXML private TableView<Showtime> showtimesTable;
    @FXML private TableColumn<Showtime, String> showDateColumn;
    @FXML private TableColumn<Showtime, String> showTimeColumn;
    @FXML private TableColumn<Showtime, Number> showSeatsColumn;
    @FXML private TableColumn<Showtime, String> showPriceColumn;

    @FXML private DatePicker showDatePicker;
    @FXML private TextField showTimeField;
    @FXML private TextField showSeatsField;
    @FXML private TextField showTicketPriceField;

    @FXML private TableView<FoodItem> foodItemsTable;
    @FXML private TableColumn<FoodItem, String> foodNameColumn;
    @FXML private TableColumn<FoodItem, String> foodDescriptionColumn;
    @FXML private TableColumn<FoodItem, String> foodPriceColumn;
    @FXML private TableColumn<FoodItem, String> foodActiveColumn;

    @FXML private TextField foodNameField;
    @FXML private TextArea foodDescriptionArea;
    @FXML private TextField foodPriceField;
    @FXML private TextField foodImageField;
    @FXML private CheckBox foodActiveCheck;
    @FXML private TableView<PurchaseRow> purchaseHistoryTable;
    @FXML private TableColumn<PurchaseRow, String> purchaseTimestampColumn;
    @FXML private TableColumn<PurchaseRow, String> purchaseCustomerColumn;
    @FXML private TableColumn<PurchaseRow, String> purchaseMovieColumn;
    @FXML private TableColumn<PurchaseRow, String> purchaseShowtimeColumn;
    @FXML private TableColumn<PurchaseRow, String> purchaseSeatsColumn;
    @FXML private TableColumn<PurchaseRow, String> purchaseTicketsColumn;
    @FXML private TableColumn<PurchaseRow, String> purchaseTotalColumn;
    @FXML private TableColumn<PurchaseRow, String> purchasePaymentColumn;

    private final MovieDAO movieDAO = new MovieDAO();
    private final ShowtimeDAO showtimeDAO = new ShowtimeDAO();
    private final FoodItemDAO foodItemDAO = new FoodItemDAO();

    private final ObservableList<Movie> movies = FXCollections.observableArrayList();
    private final ObservableList<Showtime> showtimes = FXCollections.observableArrayList();
    private final ObservableList<FoodItem> foodItems = FXCollections.observableArrayList();
    private final ObservableList<PurchaseRow> purchaseHistory = FXCollections.observableArrayList();

    private AdminAccount adminAccount;
    private Stage stage;
    private Runnable logoutHandler;
    private FoodItem selectedFoodItem;

    private static final DecimalFormat PRICE_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ENGLISH);
        symbols.setDecimalSeparator('.');
        symbols.setGroupingSeparator(',');
        PRICE_FORMAT = new DecimalFormat("###,##0.00", symbols);
        PRICE_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
    }

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter HISTORY_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter HISTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter HISTORY_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy | hh:mm a");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        movieTitleColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTitle()));
        movieYearColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getReleaseYear()));
        movieGenresColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getGenres()));
        movieRuntimeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRuntimeFormatted()));

        showDateColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getShowDate().toString()));
        showTimeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getShowTime().toString()));
        showSeatsColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getAvailableSeats()));
        if (showPriceColumn != null) {
            showPriceColumn.setCellValueFactory(data ->
                    new SimpleStringProperty(formatPrice(data.getValue().getTicketPrice())));
        }

        moviesTable.setItems(movies);
        showtimesTable.setItems(showtimes);

        foodNameColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getName() == null ? "" : data.getValue().getName()));
        foodDescriptionColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDescription() == null ? "" : data.getValue().getDescription()));
        foodPriceColumn.setCellValueFactory(data -> new SimpleStringProperty(formatPrice(data.getValue().getPrice())));
        foodActiveColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isActive() ? "Yes" : "No"));

        foodItemsTable.setItems(foodItems);

        moviesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldMovie, newMovie) -> {
            if (newMovie != null) {
                loadShowtimes(newMovie);
            } else {
                showtimes.clear();
            }
        });

        foodItemsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null) {
                populateFoodForm(newItem);
            } else {
                clearFoodFormFields();
            }
        });

        clearFoodFormFields();
        clearShowtimeForm();

        if (purchaseHistoryTable != null) {
            purchaseHistoryTable.setItems(purchaseHistory);
            purchaseHistoryTable.setPlaceholder(new Label("No purchases yet."));
        }
        if (purchaseTimestampColumn != null) {
            purchaseTimestampColumn.setCellValueFactory(data -> data.getValue().timestampProperty());
        }
        if (purchaseCustomerColumn != null) {
            purchaseCustomerColumn.setCellValueFactory(data -> data.getValue().customerProperty());
        }
        if (purchaseMovieColumn != null) {
            purchaseMovieColumn.setCellValueFactory(data -> data.getValue().movieProperty());
        }
        if (purchaseShowtimeColumn != null) {
            purchaseShowtimeColumn.setCellValueFactory(data -> data.getValue().showtimeProperty());
        }
        if (purchaseSeatsColumn != null) {
            purchaseSeatsColumn.setCellValueFactory(data -> data.getValue().seatsProperty());
        }
        if (purchaseTicketsColumn != null) {
            purchaseTicketsColumn.setCellValueFactory(data -> data.getValue().ticketsProperty());
        }
        if (purchaseTotalColumn != null) {
            purchaseTotalColumn.setCellValueFactory(data -> data.getValue().totalProperty());
        }
        if (purchasePaymentColumn != null) {
            purchasePaymentColumn.setCellValueFactory(data -> data.getValue().paymentProperty());
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setLogoutHandler(Runnable logoutHandler) {
        this.logoutHandler = logoutHandler;
    }

    public void setAdminAccount(AdminAccount adminAccount) {
        this.adminAccount = adminAccount;
        if (adminAccount != null) {
            String branchName = adminAccount.getBranch() != null
                    ? adminAccount.getBranch().getName()
                    : null;
            String adminName = adminAccount.getUser() != null
                    ? adminAccount.getUser().getFullName()
                    : null;

            if (branchName != null && !branchName.isBlank()) {
                if (branchLabel != null) {
                    branchLabel.setText("Branch: " + branchName);
                }
                adminHeader.setText("Admin Dashboard - " + branchName);
            } else {
                if (branchLabel != null) {
                    branchLabel.setText("");
                }
                if (adminName != null && !adminName.isBlank()) {
                    adminHeader.setText("Admin Dashboard - " + adminName);
                } else {
                    adminHeader.setText("Admin Dashboard");
                }
            }
        }
        refreshMovies();
        loadFoodItems();
        loadPurchaseHistory();
    }

    private void refreshMovies() {
        try {
            List<Movie> allMovies = movieDAO.findAll();
            movies.setAll(allMovies);
            if (!movies.isEmpty()) {
                moviesTable.getSelectionModel().selectFirst();
            }
        } catch (SQLException ex) {
            showError("Unable to load movies", ex);
        }
    }

    private void loadFoodItems() {
        foodItems.clear();
        if (adminAccount == null || adminAccount.getBranch() == null) {
            return;
        }
        try {
            List<FoodItem> items = foodItemDAO.findAllByBranch(adminAccount.getBranch().getId());
            foodItems.setAll(items);
            if (!foodItems.isEmpty()) {
                foodItemsTable.getSelectionModel().selectFirst();
            } else {
                clearFoodFormFields();
            }
        } catch (SQLException ex) {
            showError("Unable to load food items.", ex);
        }
    }

    private void loadShowtimes(Movie movie) {
        if (movie == null || adminAccount == null) {
            showtimes.clear();
            return;
        }
        try {
            showtimes.setAll(showtimeDAO.findByMovieAndBranch(movie.getId(), adminAccount.getBranch().getId()));
        } catch (SQLException ex) {
            showError("Unable to load showtimes for " + movie.getTitle(), ex);
        }
    }

    @FXML
    void handleAddMovie(ActionEvent event) {
        String title = trim(movieTitleField.getText());
        String slug = trim(movieSlugField.getText());
        String yearText = trim(movieYearField.getText());
        String runtimeText = trim(movieRuntimeField.getText());
        String genres = trim(movieGenresField.getText());
        String posterPath = trim(moviePosterField.getText());
        String synopsis = trim(movieSynopsisArea.getText());

        if (title.isEmpty() || genres.isEmpty() || posterPath.isEmpty() || yearText.isEmpty() || runtimeText.isEmpty()) {
            showWarning("Please fill in title, year, runtime, genres, and poster path.");
            return;
        }

        int year;
        int runtime;
        try {
            year = Integer.parseInt(yearText);
            runtime = Integer.parseInt(runtimeText);
        } catch (NumberFormatException ex) {
            showWarning("Release year and runtime must be valid numbers.");
            return;
        }

        if (slug.isEmpty()) {
            slug = slugify(title);
        }

        try {
            Movie movie = movieDAO.createMovie(slug, title, year, genres, runtime, posterPath, synopsis);
            movies.add(movie);
            moviesTable.getSelectionModel().select(movie);
            clearMovieForm();
            showInfo("Movie added", "'" + movie.getTitle() + "' is now available.");
        } catch (SQLException ex) {
            showError("Unable to add movie. Make sure the slug is unique.", ex);
        }
    }

    @FXML
    void handleDeleteMovie(ActionEvent event) {
        Movie selected = moviesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Please select a movie to delete.");
            return;
        }

        String movieTitle = selected.getTitle();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText("Delete movie?");
        confirm.setContentText("Remove '" + movieTitle + "' and all associated schedules?");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                boolean removed = movieDAO.deleteMovie(selected.getId());
                if (!removed) {
                    showWarning("Movie record no longer exists.");
                    refreshMovies();
                    return;
                }
                clearMovieForm();
                showtimes.clear();
                showInfo("Movie deleted", "'" + movieTitle + "' has been removed.");
                refreshMovies();
            } catch (SQLException ex) {
                showError("Unable to delete movie.", ex);
            }
        }
    }

    @FXML
    void handleBrowsePoster(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Movie Poster");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp")
        );

        File initialDir = new File(System.getProperty("user.home"));
        if (initialDir.exists() && initialDir.isDirectory()) {
            chooser.setInitialDirectory(initialDir);
        }

        Stage owner = stage;
        if (owner == null && moviePosterField.getScene() != null) {
            owner = (Stage) moviePosterField.getScene().getWindow();
        }

        File chosen = chooser.showOpenDialog(owner);
        if (chosen != null) {
            try {
                String relativePath = storePosterImage(chosen);
                moviePosterField.setText(relativePath);
            } catch (IOException ex) {
                showError("Unable to import poster image.", ex);
            }
        }
    }

    @FXML
    void handleBrowseFoodImage(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Food Image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp")
        );

        File initialDir = new File(System.getProperty("user.home"));
        if (initialDir.exists() && initialDir.isDirectory()) {
            chooser.setInitialDirectory(initialDir);
        }

        Stage owner = stage;
        if (owner == null && foodImageField.getScene() != null) {
            owner = (Stage) foodImageField.getScene().getWindow();
        }

        File chosen = chooser.showOpenDialog(owner);
        if (chosen != null) {
            try {
                String relativePath = storeFoodImage(chosen);
                foodImageField.setText(relativePath);
            } catch (IOException ex) {
                showError("Unable to import food image.", ex);
            }
        }
    }

    @FXML
    void handleSaveFoodItem(ActionEvent event) {
        if (adminAccount == null || adminAccount.getBranch() == null) {
            showWarning("Unable to determine admin branch.");
            return;
        }

        String name = trim(foodNameField.getText());
        String description = trim(foodDescriptionArea.getText());
        String priceText = trim(foodPriceField.getText());
        String imagePath = trim(foodImageField.getText());
        boolean active = foodActiveCheck.isSelected();

        if (name.isEmpty()) {
            showWarning("Food name is required.");
            return;
        }
        if (priceText.isEmpty()) {
            showWarning("Please provide a price (e.g., 120.00).");
            return;
        }

        BigDecimal price;
        try {
            price = parsePrice(priceText);
        } catch (NumberFormatException ex) {
            showWarning("Price must be a valid number (e.g., 120.00).");
            return;
        }

        FoodItem target = selectedFoodItem != null ? selectedFoodItem : new FoodItem();
        target.setBranchId(adminAccount.getBranch().getId());
        target.setName(name);
        target.setDescription(description.isEmpty() ? null : description);
        target.setPrice(price);
        target.setImagePath(imagePath.isEmpty() ? null : imagePath);
        target.setActive(active);

        try {
            foodItemDAO.save(target);
            if (!foodItems.contains(target)) {
                foodItems.add(target);
            }
            selectedFoodItem = target;
            foodItemsTable.getSelectionModel().select(target);
            foodItemsTable.refresh();
            showInfo("Food item saved", "'" + target.getName() + "' has been saved.");
        } catch (SQLException ex) {
            showError("Unable to save food item.", ex);
        }
    }

    @FXML
    void handleClearFoodForm(ActionEvent event) {
        foodItemsTable.getSelectionModel().clearSelection();
        clearFoodFormFields();
    }

    @FXML
    void handleDeleteFoodItem(ActionEvent event) {
        FoodItem selected = foodItemsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Please select a food item to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText("Delete food item?");
        confirm.setContentText("Remove '" + selected.getName() + "' from this branch menu?");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                foodItemDAO.delete(selected.getId());
                foodItems.remove(selected);
                clearFoodFormFields();
                showInfo("Food item removed", "'" + selected.getName() + "' has been deleted.");
            } catch (SQLException ex) {
                showError("Unable to delete food item.", ex);
            }
        }
    }

    @FXML
    void handleAddShowtime(ActionEvent event) {
        Movie selected = moviesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Please select a movie first.");
            return;
        }
        if (adminAccount == null) {
            showWarning("Unable to determine admin branch.");
            return;
        }

        LocalDate date = showDatePicker.getValue();
        String timeText = trim(showTimeField.getText());
        String seatsText = trim(showSeatsField.getText());
        String priceText = showTicketPriceField == null ? "" : trim(showTicketPriceField.getText());

        if (date == null || timeText.isEmpty() || seatsText.isEmpty() || priceText.isEmpty()) {
            showWarning("Please provide show date, time, seat capacity, and ticket price.");
            return;
        }

        LocalTime time;
        int seats;
        BigDecimal ticketPrice;
        try {
            time = LocalTime.parse(timeText, TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            showWarning("Time must be in 24-hour format (e.g., 19:30).");
            return;
        }
        try {
            seats = Integer.parseInt(seatsText);
        } catch (NumberFormatException ex) {
            showWarning("Available seats must be a number.");
            return;
        }
        try {
            ticketPrice = parsePrice(priceText);
        } catch (NumberFormatException ex) {
            showWarning("Ticket price must be a valid amount (e.g., 250.00).");
            return;
        }

        try {
            showtimeDAO.createShowtime(selected.getId(), adminAccount.getBranch().getId(), date, time, seats, ticketPrice);
            loadShowtimes(selected);
            clearShowtimeForm();
            showInfo("Showtime saved", "Showtime added for " + selected.getTitle() + " on " + date + " at " + time + ".");
        } catch (SQLException ex) {
            showError("Unable to save showtime. Make sure it is not a duplicate.", ex);
        }
    }

    @FXML
    void handleDeleteShowtime(ActionEvent event) {
        Showtime selectedShowtime = showtimesTable.getSelectionModel().getSelectedItem();
        if (selectedShowtime == null) {
            showWarning("Please select a schedule to delete.");
            return;
        }

        Movie selectedMovie = moviesTable.getSelectionModel().getSelectedItem();
        String movieTitle = selectedMovie != null ? selectedMovie.getTitle() : "this movie";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText("Delete showtime?");
        confirm.setContentText("Remove the schedule on " + selectedShowtime.getShowDate() + " at " + selectedShowtime.getShowTime() + " for " + movieTitle + "?");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                showtimeDAO.deleteShowtime(selectedShowtime.getId());
                if (selectedMovie != null) {
                    loadShowtimes(selectedMovie);
                } else {
                    showtimes.remove(selectedShowtime);
                }
                showInfo("Showtime removed", "Schedule deleted successfully.");
            } catch (SQLException ex) {
                showError("Unable to delete showtime.", ex);
            }
        }
    }

    @FXML
    void handleLogout(ActionEvent event) {
        if (logoutHandler != null) {
            logoutHandler.run();
        }
    }

    @FXML
    void handleClose(ActionEvent event) {
        if (stage != null) {
            stage.close();
        }
    }

    @FXML
    void handleMinimize(ActionEvent event) {
        if (stage != null) {
            stage.setIconified(true);
        }
    }

    @FXML
    void handleRefreshHistory(ActionEvent event) {
        loadPurchaseHistory();
    }

    private void clearMovieForm() {
        movieTitleField.clear();
        movieSlugField.clear();
        movieYearField.clear();
        movieRuntimeField.clear();
        movieGenresField.clear();
        moviePosterField.clear();
        movieSynopsisArea.clear();
    }

    private void clearShowtimeForm() {
        showDatePicker.setValue(null);
        showTimeField.clear();
        if (showTicketPriceField != null) {
            showTicketPriceField.clear();
        }
        enforceSeatCapacityLock();
    }

    private void enforceSeatCapacityLock() {
        if (showSeatsField != null) {
            showSeatsField.setText(String.valueOf(SeatCapacity.DEFAULT_CAPACITY));
            showSeatsField.setEditable(false);
        }
    }

    private void loadPurchaseHistory() {
        if (purchaseHistoryTable == null) {
            return;
        }
        if (adminAccount == null || adminAccount.getBranch() == null) {
            purchaseHistory.clear();
            return;
        }
        try {
            List<Reservation> reservations = ReservationService.findByBranch(adminAccount.getBranch().getId());
            List<PurchaseRow> rows = new ArrayList<>();
            for (Reservation reservation : reservations) {
                rows.add(new PurchaseRow(reservation));
            }
            purchaseHistory.setAll(rows);
        } catch (SQLException ex) {
            showError("Unable to load purchase history.", ex);
        }
    }

    private void populateFoodForm(FoodItem item) {
        if (item == null) {
            clearFoodFormFields();
            return;
        }
        selectedFoodItem = item;
        foodNameField.setText(item.getName() == null ? "" : item.getName());
        foodDescriptionArea.setText(item.getDescription() == null ? "" : item.getDescription());
        foodPriceField.setText(priceToInput(item.getPrice()));
        foodImageField.setText(item.getImagePath() == null ? "" : item.getImagePath());
        foodActiveCheck.setSelected(item.isActive());
    }

    private void clearFoodFormFields() {
        selectedFoodItem = null;
        if (foodNameField != null) {
            foodNameField.clear();
        }
        if (foodDescriptionArea != null) {
            foodDescriptionArea.clear();
        }
        if (foodPriceField != null) {
            foodPriceField.clear();
        }
        if (foodImageField != null) {
            foodImageField.clear();
        }
        if (foodActiveCheck != null) {
            foodActiveCheck.setSelected(true);
        }
    }

    private BigDecimal parsePrice(String text) {
        BigDecimal value = new BigDecimal(text.replace(",", "").trim());
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new NumberFormatException("Price must not be negative.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String priceToInput(BigDecimal price) {
        if (price == null) {
            return "";
        }
        return price.stripTrailingZeros().toPlainString();
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String heading, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(heading);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String heading, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(heading);
        alert.setContentText(ex.getMessage());
        alert.showAndWait();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String formatPrice(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        synchronized (PRICE_FORMAT) {
            return "₱ " + PRICE_FORMAT.format(normalized);
        }
    }

    private String buildShowtimeLabel(Showtime showtime) {
        if (showtime == null) {
            return "-";
        }
        String branch = showtime.getBranchName() == null ? "Branch" : showtime.getBranchName();
        String date = showtime.getShowDate() != null ? showtime.getShowDate().format(HISTORY_DATE_FORMAT) : "Date TBD";
        String time = showtime.getShowTime() != null ? showtime.getShowTime().format(HISTORY_TIME_FORMAT) : "Time TBD";
        return branch + " | " + date + " | " + time;
    }

    private String formatSeatList(List<String> seats) {
        if (seats == null || seats.isEmpty()) {
            return "-";
        }
        return String.join(", ", seats);
    }

    private String formatCustomerName(User user) {
        if (user == null) {
            return "-";
        }
        String fullName = user.getFullName();
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        return user.getUsername() == null ? "-" : user.getUsername();
    }

    private class PurchaseRow {
        private final SimpleStringProperty timestamp = new SimpleStringProperty("-");
        private final SimpleStringProperty customer = new SimpleStringProperty("-");
        private final SimpleStringProperty movie = new SimpleStringProperty("-");
        private final SimpleStringProperty showtime = new SimpleStringProperty("-");
        private final SimpleStringProperty seats = new SimpleStringProperty("-");
        private final SimpleStringProperty tickets = new SimpleStringProperty("0");
        private final SimpleStringProperty total = new SimpleStringProperty(formatPrice(BigDecimal.ZERO));
        private final SimpleStringProperty payment = new SimpleStringProperty("-");

        PurchaseRow(Reservation reservation) {
            if (reservation == null) {
                return;
            }
            if (reservation.getPurchaseDateTime() != null) {
                timestamp.set(reservation.getPurchaseDateTime().format(HISTORY_TIMESTAMP_FORMAT));
            }
            customer.set(formatCustomerName(reservation.getUser()));

            CheckoutSummary summary = reservation.getSummary();
            if (summary != null) {
                Movie movieModel = summary.getMovie();
                if (movieModel != null && movieModel.getTitle() != null) {
                    movie.set(movieModel.getTitle());
                }
                Showtime showtimeModel = summary.getShowtime();
                showtime.set(buildShowtimeLabel(showtimeModel));
                seats.set(formatSeatList(summary.getSeats()));
                tickets.set(Integer.toString(summary.getTicketCount()));
                total.set(formatPrice(summary.getGrandTotal()));
            }

            String paymentMethod = reservation.getPaymentMethod();
            payment.set(paymentMethod == null || paymentMethod.isBlank() ? "Unknown" : paymentMethod);
        }

        SimpleStringProperty timestampProperty() { return timestamp; }
        SimpleStringProperty customerProperty() { return customer; }
        SimpleStringProperty movieProperty() { return movie; }
        SimpleStringProperty showtimeProperty() { return showtime; }
        SimpleStringProperty seatsProperty() { return seats; }
        SimpleStringProperty ticketsProperty() { return tickets; }
        SimpleStringProperty totalProperty() { return total; }
        SimpleStringProperty paymentProperty() { return payment; }
    }

    private String storePosterImage(File source) throws IOException {
        String slugInput = trim(movieSlugField.getText());
        String titleInput = trim(movieTitleField.getText());

        String baseHint;
        if (!slugInput.isEmpty()) {
            baseHint = slugInput;
        } else if (!titleInput.isEmpty()) {
            baseHint = titleInput;
        } else {
            baseHint = removeExtension(source.getName());
        }
        return storeImageAsset(source, baseHint, "poster");
    }

    private String storeFoodImage(File source) throws IOException {
        String nameInput = trim(foodNameField.getText());
        String branchName = (adminAccount != null && adminAccount.getBranch() != null)
                ? adminAccount.getBranch().getName()
                : "";
        String fallback = removeExtension(source.getName());
        String baseHint = nameInput.isEmpty() ? fallback : nameInput;
        if (!branchName.isEmpty()) {
            baseHint = baseHint + "-" + branchName;
        }
        return storeImageAsset(source, baseHint, "food");
    }

    private String storeImageAsset(File source, String baseNameHint, String defaultPrefix) throws IOException {
        String baseName = slugify(baseNameHint);
        if (baseName.isEmpty()) {
            baseName = defaultPrefix;
        }

        String extension = extractExtension(source.getName());

        Path projectDir = Paths.get(System.getProperty("user.dir"));
        Path imagesSourceDir = projectDir.resolve("src").resolve("movieticketreservation").resolve("images");
        Files.createDirectories(imagesSourceDir);

        Path targetFile = imagesSourceDir.resolve(baseName + extension);
        Path sourcePath = source.toPath().toAbsolutePath().normalize();
        Path targetPath = targetFile.toAbsolutePath().normalize();
        if (!sourcePath.equals(targetPath)) {
            Files.copy(source.toPath(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        }

        Path classesDir = projectDir.resolve("build").resolve("classes").resolve("movieticketreservation").resolve("images");
        try {
            Files.createDirectories(classesDir);
            Path classpathTarget = classesDir.resolve(targetFile.getFileName());
            Path classpathPath = classpathTarget.toAbsolutePath().normalize();
            if (!sourcePath.equals(classpathPath)) {
                Files.copy(source.toPath(), classpathTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Build output may not be initialized yet; asset will be bundled on next build.
        }

        return "images/" + targetFile.getFileName();
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex).toLowerCase();
        }
        return ".png";
    }

    private String removeExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    private String slugify(String value) {
        return value.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}



