package eu.artofcoding.odisee.client.javafx;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Reads the screens as classpath bytes. It does not start the JavaFX toolkit.
 */
public class FxResourcesTest {

    @Test
    public void screensNameTheirControllers() throws Exception {
        assertScreen("AppMainFrame", "eu.artofcoding.odisee.client.javafx.AppMainFrameController");
        assertScreen("LoginPane", "eu.artofcoding.odisee.client.javafx.LoginController");
        assertScreen("OdiseeDevPane", "eu.artofcoding.odisee.client.javafx.OdiseeDevController");
    }

    @Test
    public void loginImagesArePackaged() throws Exception {
        assertResource("/images/Odisee_Maskottchen_positiv.png");
        assertResource("/images/Odisee_Maskottchen_negativ.png");
        assertResource("/images/Odisee_Signet.png");
    }

    private static void assertScreen(final String name, final String controller) throws Exception {
        final String fxml = read(FxmlNames.path(name));
        assertTrue(fxml.contains("fx:controller=\"" + controller + "\""));
    }

    private static void assertResource(final String path) throws Exception {
        try (InputStream in = FxResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(path, in);
            assertTrue(path, in.read() >= 0);
        }
    }

    private static String read(final String path) throws Exception {
        try (InputStream in = FxResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(path, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
