package eu.artofcoding.odisee.client.javafx;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import org.odisee.client.OdiseeClient;

import java.net.URL;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML
    private AnchorPane loginPane;

    @FXML
    private ImageView odiseeMaskottchenPositiv;

    @FXML
    private ImageView odiseeMaskottchenNegativ;

    @FXML
    private TextField serviceUrl;

    @FXML
    private PasswordField bearerToken;

    @FXML
    private Button loginButton;

    private OdiseeClient client;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        System.out.printf("%s.initialize(%s, %s)%n", this, url, resourceBundle);
        odiseeMaskottchenPositiv.setImage(Resources.getOdiseeMaskottchenPositiv());
        odiseeMaskottchenNegativ.setImage(Resources.getOdiseeMaskottchenNegativ());
    }

    public void loginTokenFocus() {
        bearerToken.requestFocus();
    }

    public OdiseeClient getClient() {
        return client;
    }

    @FXML
    private void loginButtonPressed(ActionEvent event) {
        final String url = serviceUrl == null ? "" : serviceUrl.getText().trim();
        final String token = bearerToken == null ? "" : bearerToken.getText().trim();
        if (!url.isEmpty() && !token.isEmpty()) {
            client = DesktopClient.open(url, token);
        }
        odiseeMaskottchenPositiv.setOpacity(0.0);
        odiseeMaskottchenPositiv.setVisible(true);
        FXHelper.fadeIn(odiseeMaskottchenPositiv, 500);
        FXHelper.fadeOut(loginPane, 3000);
    }

}
