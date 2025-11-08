package movieticketreservation;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import movieticketreservation.dao.FoodItemDAO;
import movieticketreservation.model.CheckoutContext;
import movieticketreservation.model.CheckoutLineItem;
import movieticketreservation.model.CheckoutSummary;
import movieticketreservation.model.FoodItem;
import movieticketreservation.model.Movie;
import movieticketreservation.model.Showtime;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class FoodSelectionController implements Initializable {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    private static final DecimalFormat CURRENCY_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ENGLISH);
        symbols.setCurrencySymbol("\u20B1");
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        CURRENCY_FORMAT = new DecimalFormat("\u20B1 ###,##0.00", symbols);
        CURRENCY_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
    }

    @FXML private Label movieTitleLabel;
    @FXML private Label showtimeLabel;
    @FXML private FlowPane menuFlowPane;
    @FXML private VBox cartItemsBox;
    @FXML private Label cartTotalLabel;
    @FXML private Button confirmOrderButton;
    @FXML private Button backButton;
    @FXML private ScrollPane menuScroll;

    private final FoodItemDAO foodItemDAO = new FoodItemDAO();

    private Stage stage;
    private Scene previousScene;
    private Movie movie;
    private Showtime showtime;
    private List<String> selectedSeats = List.of();
    private int ticketCount;
    private BigDecimal ticketPrice = BigDecimal.ZERO;
    private BigDecimal ticketSubtotal = BigDecimal.ZERO;
    private Consumer<CheckoutSummary> checkoutHandler;

    private final Map<Integer, FoodItem> foodById = new HashMap<>();
    private final Map<Integer, Integer> quantities = new LinkedHashMap<>();
    private final Map<Integer, Label> quantityLabels = new HashMap<>();
    private final Map<Integer, Button> decrementButtons = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cartItemsBox.setSpacing(12);
        if (menuScroll != null) {
            menuScroll.setFitToWidth(true);
        }
    }

    public void configure(Stage stage,
                          Scene previousScene,
                          Movie movie,
                          Showtime showtime,
                          List<String> selectedSeats,
                          int ticketCount,
                          BigDecimal ticketPrice,
                          Consumer<CheckoutSummary> checkoutHandler) {
        this.stage = stage;
        this.previousScene = previousScene;
        this.movie = movie;
        this.showtime = showtime;
        this.selectedSeats = selectedSeats == null ? List.of() : new ArrayList<>(selectedSeats);
        this.ticketCount = Math.max(ticketCount, 1);
        this.ticketPrice = ticketPrice == null ? BigDecimal.ZERO : ticketPrice;
        this.ticketSubtotal = this.ticketPrice.multiply(BigDecimal.valueOf(this.ticketCount));
        this.checkoutHandler = checkoutHandler;

        updateHeader();
        loadMenu();
        updateCartSummary();
    }

    @FXML
    void handleBack() {
        if (stage != null && previousScene != null) {
            stage.setScene(previousScene);
            stage.centerOnScreen();
            stage.show();
        }
    }

    @FXML
    void handleClose() {
        if (stage != null) {
            stage.close();
        }
    }

    @FXML
    void handleMinimize() {
        if (stage != null) {
            stage.setIconified(true);
        }
    }

    @FXML
    void handleConfirmOrder() {
        BigDecimal foodTotal = calculateFoodSubtotal();
        BigDecimal grandTotal = ticketSubtotal.add(foodTotal);

        CheckoutSummary summary = buildCheckoutSummary(foodTotal, grandTotal);
        CheckoutContext.setLatest(summary);
        if (checkoutHandler != null) {
            Platform.runLater(() -> checkoutHandler.accept(summary));
        } else {
            showInformation("Order Summary", buildSummaryText(summary));
        }
    }

    private void updateHeader() {
        if (movieTitleLabel != null && movie != null) {
            movieTitleLabel.setText(movie.getTitle());
        }
        if (showtimeLabel != null && showtime != null) {
            showtimeLabel.setText(formatShowtime(showtime));
        }
    }

    private void loadMenu() {
        if (menuFlowPane == null || showtime == null) {
            return;
        }
        menuFlowPane.getChildren().clear();
        foodById.clear();
        quantities.clear();
        quantityLabels.clear();
        decrementButtons.clear();

        try {
            List<FoodItem> items = foodItemDAO.findActiveByBranch(showtime.getBranchId());
            if (items.isEmpty()) {
                Label empty = new Label("No food items available for this branch yet.");
                empty.getStyleClass().add("showtime-label");
                menuFlowPane.getChildren().add(empty);
                return;
            }
            for (FoodItem item : items) {
                if (item == null) continue;
                foodById.put(item.getId(), item);
                quantities.put(item.getId(), 0);
                menuFlowPane.getChildren().add(createFoodCard(item));
            }
        } catch (Exception ex) {
            Label error = new Label("Unable to load food menu.");
            error.getStyleClass().add("showtime-label");
            menuFlowPane.getChildren().add(error);
        }
    }

    private VBox createFoodCard(FoodItem item) {
        VBox card = new VBox(8);
        card.getStyleClass().add("food-card");

        ImageView imageView = new ImageView();
        imageView.getStyleClass().add("food-image");
        imageView.setFitWidth(110);
        imageView.setFitHeight(70);
        loadFoodImage(imageView, item.getImagePath());

        Label title = new Label(item.getName());
        title.getStyleClass().add("food-title");
        title.setWrapText(true);
        title.setAlignment(Pos.CENTER);

        Label price = new Label(formatCurrency(item.getPrice()));
        price.getStyleClass().add("price-text");

        HBox quantityBar = new HBox(6);
        quantityBar.getStyleClass().add("quantity-bar");
        quantityBar.setAlignment(Pos.CENTER);

        Button minusButton = new Button("-");
        minusButton.getStyleClass().add("quantity-button");
        minusButton.setDisable(true);

        Label quantityLabel = new Label("0");
        quantityLabel.getStyleClass().add("quantity-label");

        Button plusButton = new Button("+");
        plusButton.getStyleClass().add("quantity-button");

        minusButton.setOnAction(evt -> adjustQuantity(item, -1));
        plusButton.setOnAction(evt -> adjustQuantity(item, +1));

        quantityBar.getChildren().addAll(minusButton, quantityLabel, plusButton);

        card.getChildren().addAll(imageView, title, price, quantityBar);
        VBox.setMargin(quantityBar, new Insets(4, 0, 0, 0));

        quantityLabels.put(item.getId(), quantityLabel);
        decrementButtons.put(item.getId(), minusButton);

        return card;
    }

    private void adjustQuantity(FoodItem item, int delta) {
        if (item == null) return;
        int itemId = item.getId();
        int current = quantities.getOrDefault(itemId, 0);
        int updated = current + delta;
        if (updated < 0) updated = 0;
        quantities.put(itemId, updated);
        updateQuantityControls(itemId, updated);
        updateCartSummary();
    }

    private void updateQuantityControls(int itemId, int newQuantity) {
        Label label = quantityLabels.get(itemId);
        if (label != null) {
            label.setText(Integer.toString(newQuantity));
        }
        Button minusButton = decrementButtons.get(itemId);
        if (minusButton != null) {
            minusButton.setDisable(newQuantity <= 0);
        }
    }

    private void updateCartSummary() {
        if (cartItemsBox == null) {
            return;
        }
        cartItemsBox.getChildren().clear();

        // Tickets entry
        if (ticketCount > 0) {
            HBox ticketHeader = new HBox(8);
            Label title = new Label(ticketCount + " Ticket" + (ticketCount > 1 ? "s" : ""));
            title.getStyleClass().add("cart-entry-title");
            Region spacer = new Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            Label amount = new Label(formatCurrency(ticketSubtotal));
            amount.getStyleClass().add("cart-entry-amount");
            ticketHeader.getChildren().addAll(title, spacer, amount);

            Label seatsMeta = new Label(selectedSeats.isEmpty()
                    ? "Seat selection confirmed"
                    : String.join(", ", selectedSeats));
            seatsMeta.getStyleClass().add("cart-entry-meta");
            seatsMeta.setWrapText(true);

            VBox ticketBox = new VBox(4, ticketHeader, seatsMeta);
            ticketBox.getStyleClass().add("cart-entry");
            cartItemsBox.getChildren().add(ticketBox);
        }

        BigDecimal foodTotal = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            int qty = entry.getValue();
            if (qty <= 0) continue;
            FoodItem item = foodById.get(entry.getKey());
            if (item == null) continue;
            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(qty));
            foodTotal = foodTotal.add(lineTotal);
            cartItemsBox.getChildren().add(createCartEntry(item.getName(), qty, lineTotal));
        }

        BigDecimal grandTotal = ticketSubtotal.add(foodTotal);
        cartTotalLabel.setText(formatCurrency(grandTotal));
        boolean canConfirm = ticketCount > 0 || foodTotal.compareTo(BigDecimal.ZERO) > 0;
        confirmOrderButton.setDisable(!canConfirm);
    }

    private VBox createCartEntry(String name, int quantity, BigDecimal lineTotal) {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(name);
        title.getStyleClass().add("cart-entry-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        Label amount = new Label(formatCurrency(lineTotal));
        amount.getStyleClass().add("cart-entry-amount");
        header.getChildren().addAll(title, spacer, amount);

        Label meta = new Label("Qty: " + quantity);
        meta.getStyleClass().add("cart-entry-meta");

        VBox entry = new VBox(4, header, meta);
        entry.getStyleClass().add("cart-entry");
        return entry;
    }

    private BigDecimal calculateFoodSubtotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            int qty = entry.getValue();
            if (qty <= 0) continue;
            FoodItem item = foodById.get(entry.getKey());
            if (item == null || item.getPrice() == null) continue;
            total = total.add(item.getPrice().multiply(BigDecimal.valueOf(qty)));
        }
        return total;
    }

    private CheckoutSummary buildCheckoutSummary(BigDecimal foodTotal, BigDecimal grandTotal) {
        List<CheckoutLineItem> lines = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            int qty = entry.getValue();
            if (qty <= 0) continue;
            FoodItem item = foodById.get(entry.getKey());
            if (item == null || item.getPrice() == null) continue;
            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(qty));
            lines.add(new CheckoutLineItem(item.getName(), qty, item.getPrice(), lineTotal));
        }
        return new CheckoutSummary(
                movie,
                showtime,
                selectedSeats,
                ticketCount,
                ticketPrice,
                ticketSubtotal,
                lines,
                foodTotal,
                grandTotal
        );
    }

    private String buildSummaryText(CheckoutSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Movie: ").append(summary.getMovie() != null ? summary.getMovie().getTitle() : "N/A").append('\n');
        sb.append("Showtime: ").append(summary.getShowtime() != null ? formatShowtime(summary.getShowtime()) : "N/A").append('\n');
        sb.append("Seats: ").append(String.join(", ", summary.getSeats())).append('\n');
        sb.append("Tickets: ").append(summary.getTicketCount()).append(" x ").append(formatCurrency(summary.getTicketPrice()))
                .append(" = ").append(formatCurrency(summary.getTicketSubtotal())).append('\n');
        if (!summary.getFoodItems().isEmpty()) {
            sb.append('\n').append("Concessions:\n");
            for (CheckoutLineItem line : summary.getFoodItems()) {
                sb.append("- ").append(line.getName()).append(" x ").append(line.getQuantity())
                        .append(" = ").append(formatCurrency(line.getLineTotal())).append('\n');
            }
        }
        sb.append("\nGrand Total: ").append(formatCurrency(summary.getGrandTotal()));
        return sb.toString();
    }

    private void loadFoodImage(ImageView imageView, String imagePath) {
        imageView.setImage(null);
        if (imagePath == null || imagePath.isBlank()) {
            return;
        }
        String resourcePath = imagePath.startsWith("/") ? imagePath : "/movieticketreservation/" + imagePath;
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in != null) {
                imageView.setImage(new Image(in));
            }
        } catch (Exception ignored) {
        }
    }

    private String formatShowtime(Showtime showtime) {
        if (showtime == null) {
            return "Showtime not available";
        }
        return String.format("%s | %s | %s",
                showtime.getBranchName(),
                showtime.getShowDate().format(DATE_FORMATTER),
                showtime.getShowTime().format(TIME_FORMATTER));
    }

    private String formatCurrency(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        return CURRENCY_FORMAT.format(normalized);
    }

    private void showInformation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        if (stage != null) {
            alert.initOwner(stage);
        }
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}




