package movieticketreservation;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.HPos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.Parent;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.stage.Stage;
import movieticketreservation.model.Movie;
import movieticketreservation.model.Showtime;
import movieticketreservation.model.CheckoutSummary;
import movieticketreservation.service.ReservationService;
import movieticketreservation.util.SeatCapacity;

/**
 * Seat selection controller responsible for rendering a seating map and
 * allowing the user to pick seats with a target ticket count.
 */
public class SeatSelectionController implements Initializable {

    private static final char[] ROW_LABELS = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I'};
    private static final int[] BLOCK_SIZES = {8, 8, 8};
    private static final int AISLE_MARKER = -1;
    private static final int TOTAL_SEATS_PER_ROW = Arrays.stream(BLOCK_SIZES).sum();
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    @FXML private Label movieTitleText;
    @FXML private Label showtimeDetailsText;
    @FXML private Label availableSeatsLabel;
    @FXML private GridPane seatGrid;
    @FXML private Label ticketCountLabel;
    @FXML private Label selectedSeatsLabel;
    @FXML private Button confirmButton;
    @FXML private Button incrementButton;
    @FXML private Button decrementButton;
    @FXML private Button nextButton;
    @FXML private ToggleButton autoPickToggle;

    private final Map<String, ToggleButton> seatButtons = new LinkedHashMap<>();
    private final LinkedHashSet<String> selectedSeats = new LinkedHashSet<>();
    private final IntegerProperty ticketCount = new SimpleIntegerProperty(1);

    private Stage stage;
    private Scene previousScene;
    private Movie movie;
    private Showtime showtime;
    private Set<String> soldSeats = Collections.emptySet();
    private int maxSelectableSeats = 1;
    private BigDecimal ticketPrice = BigDecimal.ZERO;
    private Consumer<CheckoutSummary> checkoutHandler;
    private boolean autoPickBestSeat;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ticketCount.addListener((obs, oldVal, newVal) -> {
            int target = Math.max(newVal == null ? 1 : newVal.intValue(), 1);
            ticketCountLabel.setText(Integer.toString(target));
            trimSelectionsToTicketLimit(target);
            updateSelectedSeatInfo();
            updateTicketControlsState();
            if (autoPickBestSeat) {
                autoSelectSeatsIfPossible();
            }
        });
        ticketCountLabel.setText(Integer.toString(ticketCount.get()));
        updateSelectedSeatInfo();
        updateTicketControlsState();
        if (autoPickToggle != null) {
            autoPickToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
                autoPickBestSeat = newVal;
                updateAutoPickToggleLabel();
                if (newVal) {
                    autoSelectSeatsIfPossible();
                }
            });
            updateAutoPickToggleLabel();
        }
    }

    /**
     * Configure the scene with contextual data before showing it to the user.
     */
    public void configure(Stage stage,
                          Scene previousScene,
                          Movie movie,
                          Showtime showtime) {
        configure(stage, previousScene, movie, showtime, null, null);
    }

    public void configure(Stage stage,
                          Scene previousScene,
                          Movie movie,
                          Showtime showtime,
                          BigDecimal ticketPrice) {
        configure(stage, previousScene, movie, showtime, ticketPrice, null);
    }

    public void configure(Stage stage,
                          Scene previousScene,
                          Movie movie,
                          Showtime showtime,
                          Consumer<CheckoutSummary> checkoutHandler) {
        configure(stage, previousScene, movie, showtime, null, checkoutHandler);
    }

    public void configure(Stage stage,
                          Scene previousScene,
                          Movie movie,
                          Showtime showtime,
                          BigDecimal ticketPrice,
                          Consumer<CheckoutSummary> checkoutHandler) {
        this.stage = stage;
        this.previousScene = previousScene;
        this.movie = movie;
        this.showtime = showtime;
        BigDecimal resolvedTicketPrice = ticketPrice;
        if ((resolvedTicketPrice == null || resolvedTicketPrice.compareTo(BigDecimal.ZERO) <= 0) && showtime != null) {
            resolvedTicketPrice = showtime.getTicketPrice();
        }
        this.ticketPrice = resolvedTicketPrice == null ? BigDecimal.ZERO : resolvedTicketPrice;
        this.checkoutHandler = checkoutHandler;

        if (movieTitleText != null && movie != null) {
            movieTitleText.setText(movie.getTitle());
        }
        if (showtimeDetailsText != null && showtime != null) {
            showtimeDetailsText.setText(formatShowtimeDetails(showtime));
        }
        loadSeatLayout();
        autoSelectSeatsIfPossible();
    }

    public void setAutoPickPreference(boolean enabled) {
        this.autoPickBestSeat = enabled;
        if (autoPickToggle != null) {
            autoPickToggle.setSelected(enabled);
            updateAutoPickToggleLabel();
        } else if (enabled) {
            autoSelectSeatsIfPossible();
        }
    }

    @FXML
    void handleBack(ActionEvent event) {
        if (stage != null && previousScene != null) {
            stage.setScene(previousScene);
            stage.centerOnScreen();
            stage.show();
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
    void incrementTicketCount(ActionEvent event) {
        if (ticketCount.get() < maxSelectableSeats) {
            ticketCount.set(ticketCount.get() + 1);
        }
    }

    @FXML
    void decrementTicketCount(ActionEvent event) {
        if (ticketCount.get() > 1) {
            ticketCount.set(ticketCount.get() - 1);
        }
    }

    @FXML
    void handleConfirm(ActionEvent event) {
        proceedToFoodSelection();
    }

    @FXML
    void handleNext(ActionEvent event) {
        proceedToFoodSelection();
    }

    private void proceedToFoodSelection() {
        if (!hasRequiredSelection()) {
            return;
        }
        openFoodSelection();
    }

    private void openFoodSelection() {
        Stage currentStage = stage;
        if (currentStage == null && movieTitleText != null && movieTitleText.getScene() != null) {
            currentStage = (Stage) movieTitleText.getScene().getWindow();
        }
        if (currentStage == null) {
            showInfo("Unable to open food selection. Window context missing.");
            return;
        }

        Scene seatScene = currentStage.getScene();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("foodSelection.fxml"));
            Parent root = loader.load();
            FoodSelectionController controller = loader.getController();
            Scene foodScene = new Scene(root);

            if (controller != null) {
                controller.configure(
                        currentStage,
                        seatScene,
                        movie,
                        showtime,
                        new ArrayList<>(selectedSeats),
                        ticketCount.get(),
                        ticketPrice,
                        checkoutHandler
                );
            }

            currentStage.setScene(foodScene);
            currentStage.centerOnScreen();
            currentStage.show();
        } catch (IOException ex) {
            showInfo("Unable to open food selection.\n" + ex.getMessage());
        }
    }

    private void loadSeatLayout() {
        if (seatGrid == null) {
            return;
        }
        seatGrid.getChildren().clear();
        seatGrid.getColumnConstraints().clear();
        seatGrid.getRowConstraints().clear();
        seatButtons.clear();
        selectedSeats.clear();

        soldSeats = fetchSoldSeats(showtime);

        List<Integer> seatColumns = buildSeatColumns();
        applyColumnConstraints(seatColumns);
        applyRowConstraints();

        // Build seats row by row.
        for (int rowIndex = 0; rowIndex < ROW_LABELS.length; rowIndex++) {
            char rowLetter = ROW_LABELS[rowIndex];
            addRowLabel(rowLetter, 0, rowIndex);

            int gridColumn = 1;
            for (Integer seatNumber : seatColumns) {
                if (seatNumber == AISLE_MARKER) {
                    gridColumn++;
                    continue;
                }
                String seatId = rowLetter + seatNumber.toString();
                ToggleButton seatButton = createSeatButton(seatId, soldSeats.contains(seatId));
                seatGrid.add(seatButton, gridColumn, rowIndex);
                seatButtons.put(seatId, seatButton);
                gridColumn++;
            }

            addRowLabel(rowLetter, seatColumns.size() + 1, rowIndex);
        }

        addColumnLabels(seatColumns, ROW_LABELS.length);

        maxSelectableSeats = computeMaxSelectableSeats();
        updateAvailableSeatsLabel(maxSelectableSeats);
        if (maxSelectableSeats <= 0) {
            ticketCount.set(0);
            updateTicketControlsState();
            disableAllSeats();
            if (selectedSeatsLabel != null) {
                selectedSeatsLabel.setText("Selected: Sold out");
            }
            if (confirmButton != null) {
                confirmButton.setDisable(true);
            }
            if (nextButton != null) {
                nextButton.setDisable(true);
            }
        } else {
            int desired = ticketCount.get();
            if (desired <= 0) {
                ticketCount.set(1);
            } else if (desired > maxSelectableSeats) {
                ticketCount.set(maxSelectableSeats);
            }
            updateTicketControlsState();
        }

        updateSelectedSeatInfo();
        autoSelectSeatsIfPossible();
    }

    private List<Integer> buildSeatColumns() {
        List<Integer> columns = new ArrayList<>();
        int seatNumber = 1;
        for (int i = 0; i < BLOCK_SIZES.length; i++) {
            for (int j = 0; j < BLOCK_SIZES[i]; j++) {
                columns.add(seatNumber++);
            }
            if (i < BLOCK_SIZES.length - 1) {
                columns.add(AISLE_MARKER);
            }
        }
        return columns;
    }

    private void applyColumnConstraints(List<Integer> seatColumns) {
        // Left row label column.
        ColumnConstraints leftLabel = new ColumnConstraints();
        leftLabel.setPrefWidth(24);
        leftLabel.setHalignment(HPos.CENTER);
        seatGrid.getColumnConstraints().add(leftLabel);

        // Seat and aisle columns.
        for (Integer seatNumber : seatColumns) {
            ColumnConstraints cc = new ColumnConstraints();
            if (seatNumber == AISLE_MARKER) {
                cc.setPrefWidth(18);
            } else {
                cc.setPrefWidth(26);
            }
            cc.setHalignment(HPos.CENTER);
            seatGrid.getColumnConstraints().add(cc);
        }

        // Right row label column.
        ColumnConstraints rightLabel = new ColumnConstraints();
        rightLabel.setPrefWidth(24);
        rightLabel.setHalignment(HPos.CENTER);
        seatGrid.getColumnConstraints().add(rightLabel);
    }

    private void applyRowConstraints() {
        for (int i = 0; i < ROW_LABELS.length; i++) {
            RowConstraints rc = new RowConstraints();
            rc.setPrefHeight(28);
            seatGrid.getRowConstraints().add(rc);
        }
        RowConstraints bottomLabels = new RowConstraints();
        bottomLabels.setPrefHeight(24);
        seatGrid.getRowConstraints().add(bottomLabels);
    }

    private void addRowLabel(char rowLetter, int columnIndex, int rowIndex) {
        Label label = new Label(String.valueOf(rowLetter));
        label.getStyleClass().add("row-label");
        seatGrid.add(label, columnIndex, rowIndex);
    }

    private void addColumnLabels(List<Integer> seatColumns, int seatRowCount) {
        int gridColumn = 1;
        for (Integer seatNumber : seatColumns) {
            if (seatNumber != AISLE_MARKER) {
                Label label = new Label(Integer.toString(seatNumber));
                label.getStyleClass().add("column-label");
                seatGrid.add(label, gridColumn, seatRowCount);
            } else {
                Region spacer = new Region();
                spacer.setPrefWidth(28);
                spacer.getStyleClass().add("aisle-spacer");
                seatGrid.add(spacer, gridColumn, 0, 1, seatRowCount);
            }
            gridColumn++;
        }
    }

    private ToggleButton createSeatButton(String seatId, boolean isSold) {
        ToggleButton button = new ToggleButton();
        button.setText("");
        button.setPrefSize(24, 24);
        button.setMinSize(24, 24);
        button.setMaxSize(24, 24);
        button.setFocusTraversable(false);
        button.getStyleClass().addAll("seat-button", "seat-available");

        if (isSold) {
            button.getStyleClass().remove("seat-available");
            button.getStyleClass().add("seat-sold");
            button.setDisable(true);
        } else {
            button.setOnAction(evt -> handleSeatToggle(seatId, button));
        }

        return button;
    }

    private void handleSeatToggle(String seatId, ToggleButton button) {
        if (button.isSelected()) {
            if (selectedSeats.contains(seatId)) {
                return;
            }
            if (selectedSeats.size() >= ticketCount.get()) {
                button.setSelected(false);
                return;
            }
            selectedSeats.add(seatId);
            button.getStyleClass().remove("seat-available");
            if (!button.getStyleClass().contains("seat-selected")) {
                button.getStyleClass().add("seat-selected");
            }
        } else {
            selectedSeats.remove(seatId);
            button.getStyleClass().remove("seat-selected");
            if (!button.getStyleClass().contains("seat-available")) {
                button.getStyleClass().add("seat-available");
            }
        }
        updateSelectedSeatInfo();
    }

    private void clearSeatSelections() {
        selectedSeats.clear();
        for (ToggleButton button : seatButtons.values()) {
            if (button.isDisable()) {
                continue;
            }
            button.setSelected(false);
            button.getStyleClass().remove("seat-selected");
            if (!button.getStyleClass().contains("seat-available")) {
                button.getStyleClass().add("seat-available");
            }
        }
    }

    private void trimSelectionsToTicketLimit(int limit) {
        if (limit <= 0 || selectedSeats.size() <= limit) {
            return;
        }
        Iterator<String> iterator = selectedSeats.iterator();
        while (selectedSeats.size() > limit && iterator.hasNext()) {
            String seatId = iterator.next();
            iterator.remove();
            ToggleButton button = seatButtons.get(seatId);
            if (button != null) {
                button.setSelected(false);
                button.getStyleClass().remove("seat-selected");
                if (!button.getStyleClass().contains("seat-available")) {
                    button.getStyleClass().add("seat-available");
                }
            }
        }
    }

    private void updateSelectedSeatInfo() {
        if (selectedSeatsLabel != null) {
            if (selectedSeats.isEmpty()) {
                selectedSeatsLabel.setText("Selected: None");
            } else {
                selectedSeatsLabel.setText("Selected: " + String.join(", ", selectedSeats));
            }
        }
        if (confirmButton != null) {
            confirmButton.setDisable(selectedSeats.size() != ticketCount.get() || ticketCount.get() <= 0);
        }
        if (nextButton != null) {
            nextButton.setDisable(selectedSeats.size() != ticketCount.get() || ticketCount.get() <= 0);
        }
    }

    private void updateAutoPickToggleLabel() {
        if (autoPickToggle != null) {
            autoPickToggle.setText(autoPickToggle.isSelected()
                    ? "Auto pick best seats: ON"
                    : "Auto pick best seats: OFF");
        }
    }

    private void autoSelectSeatsIfPossible() {
        if (!autoPickBestSeat || seatButtons.isEmpty()) {
            return;
        }
        int desired = Math.min(ticketCount.get(), maxSelectableSeats);
        if (desired <= 0) {
            return;
        }
        List<String> seats = pickBestSeats(desired);
        if (seats.isEmpty()) {
            return;
        }
        clearSeatSelections();
        for (String seatId : seats) {
            ToggleButton button = seatButtons.get(seatId);
            if (button == null || button.isDisable()) {
                continue;
            }
            selectedSeats.add(seatId);
            button.setSelected(true);
            button.getStyleClass().remove("seat-available");
            if (!button.getStyleClass().contains("seat-selected")) {
                button.getStyleClass().add("seat-selected");
            }
        }
        updateSelectedSeatInfo();
    }

    private List<String> pickBestSeats(int desiredCount) {
        if (desiredCount <= 0) {
            return Collections.emptyList();
        }
        List<String> contiguous = findBestContiguousBlock(desiredCount);
        if (!contiguous.isEmpty()) {
            return contiguous;
        }
        return pickTopIndividualSeats(desiredCount);
    }

    private List<String> findBestContiguousBlock(int desiredCount) {
        Map<Character, List<Integer>> availableByRow = collectAvailableSeatsByRow();
        double rowCenter = (ROW_LABELS.length - 1) / 2.0;
        double columnCenter = (TOTAL_SEATS_PER_ROW - 1) / 2.0;
        double bestScore = Double.MAX_VALUE;
        List<String> bestSeats = Collections.emptyList();

        for (Map.Entry<Character, List<Integer>> entry : availableByRow.entrySet()) {
            List<Integer> seats = entry.getValue();
            if (seats.size() < desiredCount) {
                continue;
            }
            Collections.sort(seats);
            for (int start = 0; start <= seats.size() - desiredCount; start++) {
                boolean contiguous = true;
                for (int offset = 1; offset < desiredCount; offset++) {
                    if (seats.get(start + offset) - seats.get(start + offset - 1) != 1) {
                        contiguous = false;
                        break;
                    }
                }
                if (!contiguous) {
                    continue;
                }
                double rowIdx = getRowIndex(entry.getKey());
                double midSeat = ((seats.get(start) + seats.get(start + desiredCount - 1)) / 2.0) - 1;
                double score = Math.abs(rowIdx - rowCenter) * 2.0 + Math.abs(midSeat - columnCenter);
                if (score < bestScore) {
                    bestScore = score;
                    bestSeats = buildSeatIds(entry.getKey(), seats.subList(start, start + desiredCount));
                }
            }
        }
        return bestScore == Double.MAX_VALUE ? Collections.emptyList() : bestSeats;
    }

    private Map<Character, List<Integer>> collectAvailableSeatsByRow() {
        Map<Character, List<Integer>> byRow = new HashMap<>();
        for (Map.Entry<String, ToggleButton> entry : seatButtons.entrySet()) {
            ToggleButton button = entry.getValue();
            if (button.isDisable()) {
                continue;
            }
            String seatId = entry.getKey();
            char row = seatId.charAt(0);
            int seatNumber = parseSeatNumber(seatId);
            byRow.computeIfAbsent(row, key -> new ArrayList<>()).add(seatNumber);
        }
        return byRow;
    }

    private List<String> pickTopIndividualSeats(int desiredCount) {
        List<String> available = new ArrayList<>();
        for (Map.Entry<String, ToggleButton> entry : seatButtons.entrySet()) {
            if (!entry.getValue().isDisable()) {
                available.add(entry.getKey());
            }
        }
        available.sort(Comparator.comparingDouble(this::scoreSeat));
        if (available.size() > desiredCount) {
            return new ArrayList<>(available.subList(0, desiredCount));
        }
        return available;
    }

    private List<String> buildSeatIds(char row, List<Integer> seatNumbers) {
        List<String> ids = new ArrayList<>();
        for (Integer number : seatNumbers) {
            ids.add(row + Integer.toString(number));
        }
        return ids;
    }

    private double scoreSeat(String seatId) {
        int rowIndex = getRowIndex(seatId.charAt(0));
        int seatNumber = parseSeatNumber(seatId) - 1;
        double rowCenter = (ROW_LABELS.length - 1) / 2.0;
        double columnCenter = (TOTAL_SEATS_PER_ROW - 1) / 2.0;
        double rowScore = Math.abs(rowIndex - rowCenter);
        double columnScore = Math.abs(seatNumber - columnCenter);
        return rowScore * 2.0 + columnScore;
    }

    private int parseSeatNumber(String seatId) {
        String digits = seatId.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private int getRowIndex(char rowLetter) {
        for (int i = 0; i < ROW_LABELS.length; i++) {
            if (ROW_LABELS[i] == rowLetter) {
                return i;
            }
        }
        return 0;
    }

    private void updateTicketControlsState() {
        if (incrementButton != null) {
            incrementButton.setDisable(ticketCount.get() >= maxSelectableSeats);
        }
        if (decrementButton != null) {
            decrementButton.setDisable(ticketCount.get() <= 1);
        }
    }

    private void updateAvailableSeatsLabel(int availableCount) {
        if (availableSeatsLabel == null) {
            return;
        }
        int totalCapacity = seatButtons.isEmpty()
                ? SeatCapacity.DEFAULT_CAPACITY
                : Math.max(SeatCapacity.DEFAULT_CAPACITY, seatButtons.size());
        int safeAvailable = Math.max(availableCount, 0);
        availableSeatsLabel.setText(String.format("Available seats: %d / %d", safeAvailable, totalCapacity));
    }

    private int computeMaxSelectableSeats() {
        int totalSeats = seatButtons.size();
        int soldCount = soldSeats.size();
        int availableByLayout = Math.max(totalSeats - soldCount, 0);
        int availableByShowtime = showtime != null ? showtime.getAvailableSeats() : availableByLayout;
        return Math.max(Math.min(availableByShowtime, availableByLayout), 0);
    }

    private void disableAllSeats() {
        for (ToggleButton button : seatButtons.values()) {
            button.setDisable(true);
            button.getStyleClass().remove("seat-available");
            if (!button.getStyleClass().contains("seat-sold")) {
                button.getStyleClass().add("seat-sold");
            }
        }
    }

    private String formatShowtimeDetails(Showtime showtime) {
        if (showtime == null) {
            return "Showtime not available";
        }
        return String.format("%s | %s | %s",
                showtime.getBranchName(),
                showtime.getShowDate().format(DATE_FORMATTER),
                showtime.getShowTime().format(TIME_FORMATTER));
    }

    private Set<String> fetchSoldSeats(Showtime showtime) {
        if (showtime == null) {
            return Collections.emptySet();
        }
        try {
            return ReservationService.getReservedSeats(showtime.getId());
        } catch (SQLException ex) {
            showInfo("Unable to load reserved seats. Please try again.\n" + ex.getMessage());
            return Collections.emptySet();
        }
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        if (stage != null) {
            alert.initOwner(stage);
        }
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();
    }

    private boolean hasRequiredSelection() {
        if (ticketCount.get() <= 0) {
            showInfo("No seats are available for this showtime.");
            return false;
        }
        if (selectedSeats.size() != ticketCount.get()) {
            showInfo("Please pick " + ticketCount.get() + " seat(s) to continue.");
            return false;
        }
        return true;
    }
}

