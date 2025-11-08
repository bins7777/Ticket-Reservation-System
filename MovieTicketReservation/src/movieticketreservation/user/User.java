package movieticketreservation.user;

public class User {
    private int id;
    private String fullName;
    private String email;
    private String phone;
    private String username;
    private String passwordHash; // securely stored hash
    private boolean autoPickBestSeat;

    public User() {}

    public User(String fullName, String email, String phone, String username, String passwordHash) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.username = username;
        this.passwordHash = passwordHash;
        this.autoPickBestSeat = false;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isAutoPickBestSeat() { return autoPickBestSeat; }
    public void setAutoPickBestSeat(boolean autoPickBestSeat) { this.autoPickBestSeat = autoPickBestSeat; }
}
