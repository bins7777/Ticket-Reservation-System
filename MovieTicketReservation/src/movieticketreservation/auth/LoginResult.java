package movieticketreservation.auth;

import movieticketreservation.model.AdminAccount;
import movieticketreservation.user.User;

public class LoginResult {
    private final AccountType accountType;
    private final User user;
    private final AdminAccount adminAccount;

    private LoginResult(AccountType accountType, User user, AdminAccount adminAccount) {
        this.accountType = accountType;
        this.user = user;
        this.adminAccount = adminAccount;
    }

    public static LoginResult forUser(User user) {
        return new LoginResult(AccountType.USER, user, null);
    }

    public static LoginResult forAdmin(AdminAccount adminAccount) {
        User adminUser = adminAccount != null ? adminAccount.getUser() : null;
        return new LoginResult(AccountType.ADMIN, adminUser, adminAccount);
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public User getUser() {
        return user;
    }

    public AdminAccount getAdminAccount() {
        return adminAccount;
    }
}
