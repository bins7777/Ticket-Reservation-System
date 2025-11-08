package movieticketreservation.db;

/**
 * Small utility entry point to run the schema bootstrap without launching the UI.
 */
public final class SchemaRunner {

    private SchemaRunner() {
        // no instances
    }

    public static void main(String[] args) throws Exception {
        DatabaseInitializer.ensureSchema();
        System.out.println("Schema check complete.");
    }
}

