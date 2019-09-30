/*
 * Copyright (C) Olga Protsenko, Alexey Troshnev and Narek Akinyan 2019
 * Bauman Moscow State Technical University
 * IU3
 */
package sample;

/**
 * @authors Olga Protsenko, Alexey Troshnev and Narek Akinyan
 */

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("sample.fxml"));
        root.setId("pane");
        primaryStage.setTitle("Эквалайзер");
        root.getStylesheets().add(getClass().getResource("material.css").toString());
        Scene scene = new Scene(root, 1300, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
