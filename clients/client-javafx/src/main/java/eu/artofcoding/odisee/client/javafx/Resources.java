package eu.artofcoding.odisee.client.javafx;

import javafx.scene.image.Image;

public class Resources {

    public static Image getOdiseeMaskottchenPositiv() {
        return new Image(Resources.class.getResourceAsStream("/images/Odisee_Maskottchen_positiv.png"));
    }

    public static Image getOdiseeMaskottchenNegativ() {
        return new Image(Resources.class.getResourceAsStream("/images/Odisee_Maskottchen_negativ.png"));
    }

}
