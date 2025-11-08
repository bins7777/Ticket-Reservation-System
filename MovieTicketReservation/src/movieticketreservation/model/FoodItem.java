package movieticketreservation.model;

import java.math.BigDecimal;

/**
 * Represents a concession item offered by a cinema branch.
 */
public class FoodItem {
    private int id;
    private int branchId;
    private String name;
    private String description;
    private BigDecimal price;
    private String imagePath;
    private boolean active;

    public FoodItem() {
    }

    public FoodItem(int id,
                    int branchId,
                    String name,
                    String description,
                    BigDecimal price,
                    String imagePath,
                    boolean active) {
        this.id = id;
        this.branchId = branchId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.imagePath = imagePath;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
