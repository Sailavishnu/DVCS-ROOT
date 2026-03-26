package com.dvcs.client;

import com.dvcs.client.auth.db.MongoConnection;
import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.auth.service.UserService;
import com.dvcs.client.controller.LandingController;
import com.dvcs.client.controller.LoginSignupController;
import com.mongodb.client.MongoDatabase;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class MainApp extends Application {

    private UserService userService;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.userService = createUserService();

        primaryStage.setTitle("Document Version Control System");
        primaryStage.setMaximized(true);
        showLogin(primaryStage);
        primaryStage.show();
    }

    @Override
    public void stop() {
        MongoConnection.closeClient();
    }

    private void showLogin(Stage stage) throws Exception {
        URL fxmlUrl = getClass().getResource("/fxml/login_signup.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException(
                    "FXML '/fxml/login_signup.fxml' not found on classpath. "
                            + "Include 'client/src/main/resources' (or copy resources into your output folder) when running.");
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        LoginSignupController controller = loader.getController();
        controller.setUserService(userService);
        controller.setOnAuthSuccess(username -> {
            try {
                showLanding(stage, username);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        stage.setScene(new Scene(root));
    }

    private void showLanding(Stage stage, String username) throws Exception {
        URL fxmlUrl = getClass().getResource("/fxml/landing.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException(
                    "FXML '/fxml/landing.fxml' not found on classpath. "
                            + "Include 'client/src/main/resources' (or copy resources into your output folder) when running.");
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        LandingController controller = loader.getController();
        controller.setWelcomeMessage("Welcome" + (username == null || username.isBlank() ? "" : ", " + username) + "");

        stage.setScene(new Scene(root));
    }

    private static UserService createUserService() {
        String dbName = System.getenv("MONGODB_DB");
        if (dbName == null || dbName.isBlank()) {
            dbName = "DVCS";
        }

        MongoDatabase database = MongoConnection.getDatabase(dbName);
        return new UserService(new UserRepository(database));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
