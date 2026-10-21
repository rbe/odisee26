package eu.artofcoding.odisee.client.javafx;

/**
 * Confirms the JavaFX modules are named modules. Does not start the toolkit.
 */
public final class JavaFxModules {

    private JavaFxModules() {
    }

    public static void main(final String[] args) {
        require("javafx.graphics");
        require("javafx.controls");
        require("javafx.fxml");
        System.out.println("javafx.graphics javafx.controls javafx.fxml");
    }

    private static void require(final String name) {
        ModuleLayer.boot().findModule(name).orElseThrow(() -> new IllegalStateException(name));
    }

}
