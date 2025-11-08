package movieticketreservation.model;

import movieticketreservation.user.User;

public class AdminAccount {
    private int id;
    private Branch branch;
    private User user;

    public AdminAccount() {}

    public AdminAccount(int id, Branch branch, User user) {
        this.id = id;
        this.branch = branch;
        this.user = user;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Branch getBranch() {
        return branch;
    }

    public void setBranch(Branch branch) {
        this.branch = branch;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
