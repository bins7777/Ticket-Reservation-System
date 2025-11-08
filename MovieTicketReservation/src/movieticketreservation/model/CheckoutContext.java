package movieticketreservation.model;

import java.util.Optional;

/**
 * Holds the most recent checkout summary so different controllers can
 * coordinate without requiring direct references to one another.
 */
public final class CheckoutContext {

    private static volatile CheckoutSummary latestSummary;

    private CheckoutContext() {}

    public static void setLatest(CheckoutSummary summary) {
        latestSummary = summary;
    }

    public static Optional<CheckoutSummary> getLatest() {
        return Optional.ofNullable(latestSummary);
    }

    public static void clear() {
        latestSummary = null;
    }
}
