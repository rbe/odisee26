package eu.artofcoding.odisee.client.javafx;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ScrollPane;

import java.net.URL;
import java.util.ResourceBundle;

public class OdiseeDevController implements Initializable {

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        System.out.printf("%s.initialize(%s, %s)%n", this, url, resourceBundle);
    }

    @FXML
    private void myAction(ActionEvent event) {
        System.out.println(event);
    }

}
