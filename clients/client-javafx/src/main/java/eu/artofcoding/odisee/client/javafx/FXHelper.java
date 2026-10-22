package eu.artofcoding.odisee.client.javafx;

import javafx.animation.FadeTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Loads the packaged FXML screens and runs the login fade.
 */
public final class FXHelper {

    private FXHelper() {
    }

    public static FXMLLoader getLoader(final String... name) {
        return new FXMLLoader(FXHelper.class.getResource(FxmlNames.path(name[0])));
    }

    /**
     * Fade component in.
     *
     * @param node     the node to fade
     * @param duration duration in milliseconds
     */
    public static void fadeIn(final Node node, final int duration) {
        final FadeTransition fadeTransition = new FadeTransition(Duration.millis(duration), node);
        fadeTransition.setFromValue(0.0);
        fadeTransition.setToValue(1.0);
        fadeTransition.play();
    }

    /**
     * Fade component out.
     *
     * @param node     the node to fade
     * @param duration duration in milliseconds
     */
    public static void fadeOut(final Node node, final int duration) {
        final FadeTransition fadeTransition = new FadeTransition(Duration.millis(duration), node);
        fadeTransition.setFromValue(1.0);
        fadeTransition.setToValue(0.0);
        fadeTransition.play();
    }

}
