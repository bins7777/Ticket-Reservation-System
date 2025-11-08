package movieticketreservation.auth;

import java.sql.SQLException;
import java.util.Optional;

import movieticketreservation.dao.AdminAccountDAO;
import movieticketreservation.model.AdminAccount;
import movieticketreservation.user.User;
import movieticketreservation.user.UserDAO;

public class AuthService {

    private final UserDAO userDAO;
    private final AdminAccountDAO adminAccountDAO;

    public AuthService() {
        this(new UserDAO(), new AdminAccountDAO());
    }

    public AuthService(UserDAO userDAO, AdminAccountDAO adminAccountDAO) {
        this.userDAO = userDAO;
        this.adminAccountDAO = adminAccountDAO;
    }

    public Optional<LoginResult> login(String username, String password) throws SQLException {
        Optional<AdminAccount> adminOpt = adminAccountDAO.authenticate(username, password);
        if (adminOpt.isPresent()) {
            return Optional.of(LoginResult.forAdmin(adminOpt.get()));
        }

        Optional<User> userOpt = userDAO.authenticate(username, password);
        if (userOpt.isPresent()) {
            return Optional.of(LoginResult.forUser(userOpt.get()));
        }

        return Optional.empty();
    }
}
