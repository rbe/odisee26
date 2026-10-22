package eu.artofcoding.odisee.client.javafx;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;
import java.util.ResourceBundle;

public class AppMainFrameController implements Initializable {

    @FXML
    private ScrollPane mainFrameScrollPane;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        System.out.printf("%s.initialize(%s, %s)%n", this, url, resourceBundle);
    }

    public ScrollPane getMainFrameScrollPane() {
        return mainFrameScrollPane;
    }

    public void setContent(Node node) {
        mainFrameScrollPane.setContent(node);
    }
    
    @FXML
    private void myAction(ActionEvent event) {
        System.out.printf("%s.myAction(%s)%n", this, event);
    }

}
