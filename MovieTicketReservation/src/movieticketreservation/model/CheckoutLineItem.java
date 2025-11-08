package movieticketreservation.model;

import java.math.BigDecimal;

/**
 * Represents a single line item in the checkout summary, such as a food add-on.
 */
public class CheckoutLineItem {
    private final String name;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal lineTotal;

    public CheckoutLineItem(String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        this.name = name;
        this.quantity = quantity;
        this.unitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        this.lineTotal = lineTotal == null ? BigDecimal.ZERO : lineTotal;
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
