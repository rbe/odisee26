package eu.artofcoding.odisee.client.javafx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Load application main frame and panel
        FXMLLoader appMainFrameLoader = FXHelper.getLoader("AppMainFrame");
        AnchorPane appMainFrame = (AnchorPane) appMainFrameLoader.load();
        AppMainFrameController controller = (AppMainFrameController) appMainFrameLoader.getController();
        // Load login pane
        FXMLLoader loginPanelLoader = FXHelper.getLoader("LoginPane");
        AnchorPane loginPanel = (AnchorPane) loginPanelLoader.load();
        // Add login pane to application main frame
        controller.setContent(loginPanel);
        //
        stage.setScene(new Scene(appMainFrame));
        stage.centerOnScreen();
        stage.setTitle("Odisee(R) Client");
        stage.show();
        //
        LoginController loginController = (LoginController) loginPanelLoader.getController();
        loginController.loginUsernameFocus();
    }

    public static void main(String[] args) {
        launch(args);
    }

}
