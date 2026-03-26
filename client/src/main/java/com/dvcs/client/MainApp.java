package com.dvcs.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login_signup.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);
        primaryStage.setTitle("Document Version Control System");
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true); // full screen split layout
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
