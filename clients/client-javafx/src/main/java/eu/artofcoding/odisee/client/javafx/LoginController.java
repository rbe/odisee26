package eu.artofcoding.odisee.client.javafx;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;

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
    private TextField loginUsername;

    @FXML
    private PasswordField loginPassword;

    @FXML
    private Button loginButton;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        System.out.printf("%s.initialize(%s, %s)%n", this, url, resourceBundle);
        odiseeMaskottchenPositiv.setImage(Resources.getOdiseeMaskottchenPositiv());
        odiseeMaskottchenNegativ.setImage(Resources.getOdiseeMaskottchenNegativ());
    }
    
    public void loginUsernameFocus() {
        loginUsername.requestFocus();
    }

    @FXML
    private void loginButtonPressed(ActionEvent event) {
        System.out.printf("%s.loginButtonPressed(%s) loginUsername=%s%n", this, event, loginUsername.getText());
        odiseeMaskottchenPositiv.setOpacity(0.0);
        odiseeMaskottchenPositiv.setVisible(true);
        FXHelper.fadeIn(odiseeMaskottchenPositiv, 500);
        /*
        odiseeMaskottchenNegativ.setOpacity(0.0);
        odiseeMaskottchenNegativ.setVisible(true);
        FXHelper.fadeIn(odiseeMaskottchenNegativ, 1500);
        */
        FXHelper.fadeOut(loginPane, 3000);
    }

}
