package com.dvcs.client.controller;

import com.dvcs.client.auth.db.MongoConnection;
import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.auth.service.UserService;
import com.dvcs.client.dashboard.MainLayoutController;
import com.mongodb.client.MongoDatabase;
import java.io.IOException;
import java.net.URL;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public class LandingController {

    private Stage authStage;

    @FXML
    private void handleLogin() {
        showAuthPopup(true);
    }

    @FXML
    private void handleSignUp() {
        showAuthPopup(false);
    }

    @FXML
    private void handleGetStarted() {
        showAuthPopup(false);
    }

    private void showAuthPopup(boolean loginMode) {
        try {
            if (authStage != null && authStage.isShowing()) {
                authStage.toFront();
                return;
            }

            URL fxmlUrl = getClass().getResource("/fxml/login_signup.fxml");
            if (fxmlUrl == null) {
                throw new IllegalStateException("FXML '/fxml/login_signup.fxml' not found on classpath.");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            LoginSignupController controller = loader.getController();
            controller.setUserService(createUserService());
            controller.setInitialMode(loginMode);
            controller.setOnAuthSuccess(username -> {
                if (authStage != null) {
                    authStage.close();
                }
                showMainLayout(username);
            });

            Stage owner = getPrimaryStage();
            authStage = new Stage();
            authStage.initStyle(StageStyle.TRANSPARENT);
            authStage.initModality(Modality.WINDOW_MODAL);
            if (owner != null) {
                authStage.initOwner(owner);
            }

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            authStage.setScene(scene);
            authStage.show();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to open auth popup", e);
        }
    }

    private void showMainLayout(String username) {
        try {
            URL fxmlUrl = getClass().getResource("/fxml/MainLayout.fxml");
            if (fxmlUrl == null) {
                throw new IllegalStateException("FXML '/fxml/MainLayout.fxml' not found on classpath.");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            MainLayoutController controller = loader.getController();
            controller.setUsername(username);

            Stage stage = getPrimaryStage();
            if (stage != null) {
                stage.setScene(new Scene(root));
                stage.setMaximized(true);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load main layout", e);
        }
    }

    private Stage getPrimaryStage() {
        if (authStage != null && authStage.getOwner() instanceof Stage ownerStage) {
            return ownerStage;
        }
        for (Window window : Window.getWindows()) {
            if (window instanceof Stage stage && stage.isShowing() && stage.getOwner() == null) {
                return stage;
            }
        }
        return null;
    }

    private static UserService createUserService() {
        String dbName = System.getenv("MONGODB_DB");
        if (dbName == null || dbName.isBlank()) {
            dbName = "DVCS";
        }
        MongoDatabase database = MongoConnection.getDatabase(dbName);
        return new UserService(new UserRepository(database));
    }
}
