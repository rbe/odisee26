package eu.artofcoding.odisee.client.javafx;

/**
 * Classpath path of a packaged screen. No JavaFX types, so tests can read the file
 * without starting the toolkit.
 */
public final class FxmlNames {

    private FxmlNames() {
    }

    public static String path(final String name) {
        return String.format("/fxml/%s.fxml", name);
    }

}
