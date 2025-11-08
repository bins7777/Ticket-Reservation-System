package movieticketreservation;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import movieticketreservation.admin.AdminDashboardController;
import movieticketreservation.auth.AccountType;
import movieticketreservation.auth.AuthService;
import movieticketreservation.auth.LoginResult;
import movieticketreservation.user.User;
import movieticketreservation.user.UserDAO;

public class FXMLDocumentController implements Initializable {

    @FXML private Button logIn_button;
    @FXML private Button logIn_close;
    @FXML private Hyperlink logIn_createNewAccount;
    @FXML private Button logIn_minimize;
    @FXML private PasswordField logIn_password;
    @FXML private TextField logIn_username;
    @FXML private Hyperlink signUp_alreadyHaveAccount;
    @FXML private Button signUp_button;
    @FXML private Button signUp_close;
    @FXML private PasswordField signUp_confirmPassword;
    @FXML private TextField signUp_email;
    @FXML private TextField signUp_firstName;
    @FXML private TextField signUp_lastName;
    @FXML private Button signUp_minimize;
    @FXML private PasswordField signUp_password;
    @FXML private TextField signUp_phone;
    @FXML private TextField signUp_username;
    @FXML private AnchorPane logIn;
    @FXML private AnchorPane signUp;

    private final AuthService authService = new AuthService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        logIn.setVisible(true);
        signUp.setVisible(false);

        logIn_close.setOnAction(e -> System.exit(0));
        signUp_close.setOnAction(e -> System.exit(0));

        logIn_minimize.setOnAction(e -> {
            Stage stage = (Stage) logIn_minimize.getScene().getWindow();
            stage.setIconified(true);
        });

        signUp_minimize.setOnAction(e -> {
            Stage stage = (Stage) signUp_minimize.getScene().getWindow();
            stage.setIconified(true);
        });

        logIn_createNewAccount.setOnAction(e -> {
            logIn_createNewAccount.getScene().lookup("#signUp").setVisible(true);
            logIn_createNewAccount.getScene().lookup("#logIn").setVisible(false);
        });

        signUp_alreadyHaveAccount.setOnAction(e -> {
            signUp_alreadyHaveAccount.getScene().lookup("#logIn").setVisible(true);
            signUp_alreadyHaveAccount.getScene().lookup("#signUp").setVisible(false);
        });

        logIn_button.setOnAction(this::handleLogin);
        signUp_button.setOnAction(this::handleSignup);
    }

    private void handleLogin(ActionEvent event) {
        String username = trim(logIn_username.getText());
        String password = trim(logIn_password.getText());
        if (username.isEmpty() || password.isEmpty()) {
            showAlert(AlertType.WARNING, "Please enter username and password.");
            return;
        }
        try {
            LoginResult result = authService.login(username, password)
                    .orElse(null);
            if (result == null) {
                showAlert(AlertType.ERROR, "Invalid username or password.");
                return;
            }
            if (result.getAccountType() == AccountType.ADMIN) {
                switchToAdminDashboard(result);
            } else {
                switchToDashboard(result);
            }
        } catch (SQLException ex) {
            showDetailedError("Login failed", ex);
        }
    }

    private void switchToDashboard(LoginResult loginResult) {
        URL dashboardUrl = getClass().getResource("dashboard.fxml");
        if (dashboardUrl == null) {
            showAlert(AlertType.ERROR, "Dashboard view not found on classpath.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(dashboardUrl);
            Parent dashboardRoot = loader.load();
            DashboardController controller = loader.getController();
            Stage stage = (Stage) logIn_button.getScene().getWindow();
            if (controller != null) {
                controller.setLogoutHandler(() -> returnToLogin(stage));
                controller.setLoggedInUser(loginResult.getUser());
            }
            Scene dashboardScene = new Scene(dashboardRoot);
            dashboardScene.setFill(Color.TRANSPARENT);
            stage.setScene(dashboardScene);
            stage.centerOnScreen();
            stage.show();
            if (controller != null) {
                controller.setStage(stage);
            }
        } catch (Exception ex) {
            showDetailedError("Unable to open dashboard", ex);
        }
    }

    private void switchToAdminDashboard(LoginResult loginResult) {
        URL adminUrl = getClass().getResource("adminDashboard.fxml");
        if (adminUrl == null) {
            showAlert(AlertType.ERROR, "Admin dashboard view not found on classpath.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(adminUrl);
            Parent adminRoot = loader.load();
            AdminDashboardController controller = loader.getController();
            Stage stage = (Stage) logIn_button.getScene().getWindow();
            if (controller != null) {
                controller.setStage(stage);
                controller.setLogoutHandler(() -> returnToLogin(stage));
                controller.setAdminAccount(loginResult.getAdminAccount());
            }
            Scene adminScene = new Scene(adminRoot);
            adminScene.setFill(Color.TRANSPARENT);
            stage.setScene(adminScene);
            stage.centerOnScreen();
            stage.show();
        } catch (Exception ex) {
            showDetailedError("Unable to open admin dashboard", ex);
        }
    }

    private void returnToLogin(Stage stage) {
        URL loginUrl = getClass().getResource("FXMLDocument.fxml");
        if (loginUrl == null) {
            showAlert(AlertType.ERROR, "Login view not found on classpath.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(loginUrl);
            Parent loginRoot = loader.load();
            Scene loginScene = new Scene(loginRoot);
            loginScene.setFill(Color.TRANSPARENT);
            stage.setScene(loginScene);
            stage.centerOnScreen();
            stage.show();
        } catch (Exception ex) {
            showDetailedError("Unable to return to login", ex);
        }
    }

    private void handleSignup(ActionEvent event) {
        String firstName = trim(signUp_firstName.getText());
        String lastName = trim(signUp_lastName.getText());
        String email = trim(signUp_email.getText());
        String phone = trim(signUp_phone.getText());
        String username = trim(signUp_username.getText());
        String password = trim(signUp_password.getText());
        String confirmPass = trim(signUp_confirmPassword.getText());

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || phone.isEmpty()
                || username.isEmpty() || password.isEmpty() || confirmPass.isEmpty()) {
            showAlert(AlertType.WARNING, "Please fill out all fields.");
            return;
        }
        if (!password.equals(confirmPass)) {
            showAlert(AlertType.WARNING, "Passwords do not match.");
            return;
        }
        try {
            UserDAO dao = new UserDAO();
            if (dao.usernameExists(username)) {
                showAlert(AlertType.WARNING, "Username already exists. Choose another.");
                return;
            }
            String fullName = (firstName + " " + lastName).trim();
            User user = new User(fullName, email, phone, username, null);
            boolean created = dao.register(user, password);
            if (created) {
                showAlert(AlertType.INFORMATION, "Account created. You can now log in.");
                signUp_alreadyHaveAccount.getScene().lookup("#logIn").setVisible(true);
                signUp_alreadyHaveAccount.getScene().lookup("#signUp").setVisible(false);
            } else {
                showAlert(AlertType.ERROR, "Failed to create account.");
            }
        } catch (Exception ex) {
            showAlert(AlertType.ERROR, "Signup failed: " + ex.getMessage());
        }
    }

    private void showAlert(AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showDetailedError(String heading, Exception ex) {
        ex.printStackTrace();
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        Alert alert = new Alert(AlertType.ERROR);
        alert.setHeaderText(heading);
        alert.setContentText(ex.getMessage());

        TextArea details = new TextArea(sw.toString());
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefColumnCount(65);
        details.setPrefRowCount(20);

        alert.getDialogPane().setExpandableContent(details);
        alert.getDialogPane().setExpanded(true);
        alert.showAndWait();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
