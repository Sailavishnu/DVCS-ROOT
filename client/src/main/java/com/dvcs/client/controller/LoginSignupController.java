package com.dvcs.client.controller;

import com.dvcs.client.auth.service.UserService;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class LoginSignupController {

    @FXML
    private StackPane rootPane;

    @FXML
    private StackPane glassCard;

    @FXML
    private VBox loginForm;

    @FXML
    private VBox signupForm;

    @FXML
    private TextField loginUsernameField;

    @FXML
    private PasswordField loginPasswordField;

    @FXML
    private TextField signupUsernameField;

    @FXML
    private PasswordField signupPasswordField;

    @FXML
    private PasswordField signupConfirmPasswordField;

    @FXML
    private Button loginButton;

    @FXML
    private Button signupButton;

    @FXML
    private Label errorLabel;

    @FXML
    private Label switchActionLabel;

    @FXML
    private Label switchPromptLabel;

    @FXML
    private Label formTitleLabel;

    @FXML
    private Label formSubtitleLabel;

    private boolean loginMode = true;
    private boolean isAnimating = false;

    private UserService userService;
    private Consumer<String> onAuthSuccess;

    public void setUserService(UserService userService) {
        this.userService = Objects.requireNonNull(userService, "userService");
    }

    public void setOnAuthSuccess(Consumer<String> onAuthSuccess) {
        this.onAuthSuccess = onAuthSuccess;
    }

    @FXML
    private void initialize() {
        setActiveForm(true);
        updateModeTexts();

        // Slow, subtle card entrance for premium feel.
        glassCard.setOpacity(0.0);
        glassCard.setTranslateY(26);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(950), glassCard);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        Timeline liftIn = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(glassCard.translateYProperty(), 26, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1050),
                        new KeyValue(glassCard.translateYProperty(), 0, Interpolator.EASE_BOTH)));

        fadeIn.play();
        liftIn.play();
    }

    @FXML
    private void onSwitchMode(MouseEvent event) {
        if (isAnimating) {
            return;
        }
        isAnimating = true;
        clearError();

        VBox outgoing = loginMode ? loginForm : signupForm;
        VBox incoming = loginMode ? signupForm : loginForm;

        FadeTransition fadeOut = new FadeTransition(Duration.millis(280), outgoing);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setInterpolator(Interpolator.EASE_BOTH);

        fadeOut.setOnFinished(e -> {
            outgoing.setVisible(false);
            outgoing.setManaged(false);
            incoming.setVisible(true);
            incoming.setManaged(true);
            incoming.setOpacity(0.0);

            loginMode = !loginMode;
            updateModeTexts();

            FadeTransition fadeIn = new FadeTransition(Duration.millis(420), incoming);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setInterpolator(Interpolator.EASE_BOTH);
            fadeIn.setOnFinished(done -> isAnimating = false);
            fadeIn.play();
        });

        fadeOut.play();
    }

    @FXML
    private void onLogin() {
        clearError();
        String username = loginUsernameField.getText() == null ? "" : loginUsernameField.getText().trim();
        String password = loginPasswordField.getText() == null ? "" : loginPasswordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }
        if (userService == null) {
            showError("Auth service is not configured.");
            return;
        }

        setBusyState(true);

        Task<UserService.AuthResult> task = new Task<>() {
            @Override
            protected UserService.AuthResult call() {
                return userService.login(username, password);
            }
        };

        task.setOnSucceeded(e -> {
            setBusyState(false);
            UserService.AuthResult result = task.getValue();
            if (result != null && result.success()) {
                if (onAuthSuccess != null) {
                    onAuthSuccess.accept(username);
                }
                return;
            }
            showError(result == null ? "Login failed" : result.message());
        });

        task.setOnFailed(e -> {
            setBusyState(false);
            showError("Login failed");
        });

        Thread thread = new Thread(task, "login-task");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onSignup() {
        clearError();
        String username = signupUsernameField.getText() == null ? "" : signupUsernameField.getText().trim();
        String password = signupPasswordField.getText() == null ? "" : signupPasswordField.getText();
        String confirm = signupConfirmPasswordField.getText() == null ? "" : signupConfirmPasswordField.getText();

        if (username.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            showError("All fields are required.");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Passwords do not match.");
            return;
        }
        if (userService == null) {
            showError("Auth service is not configured.");
            return;
        }

        setBusyState(true);

        Task<UserService.AuthResult> task = new Task<>() {
            @Override
            protected UserService.AuthResult call() {
                return userService.signup(username, password);
            }
        };

        task.setOnSucceeded(e -> {
            setBusyState(false);
            UserService.AuthResult result = task.getValue();
            if (result != null && result.success()) {
                if (onAuthSuccess != null) {
                    onAuthSuccess.accept(username);
                }
                return;
            }
            showError(result == null ? "Sign up failed" : result.message());
        });

        task.setOnFailed(e -> {
            setBusyState(false);
            showError("Sign up failed");
        });

        Thread thread = new Thread(task, "signup-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void setBusyState(boolean busy) {
        loginButton.setDisable(busy);
        signupButton.setDisable(busy);
        switchActionLabel.setDisable(busy || isAnimating);
        if (switchPromptLabel != null) {
            switchPromptLabel.setDisable(busy || isAnimating);
        }
    }

    private void updateModeTexts() {
        if (loginMode) {
            formTitleLabel.setText("Login");
            formSubtitleLabel.setText("Access your workspace securely.");
            switchPromptLabel.setText("Don't have an account?");
            switchActionLabel.setText("Sign Up");
            setActiveForm(true);
        } else {
            formTitleLabel.setText("Sign Up");
            formSubtitleLabel.setText("Create your account to continue.");
            switchPromptLabel.setText("Already have an account?");
            switchActionLabel.setText("Login");
            setActiveForm(false);
        }
    }

    private void setActiveForm(boolean loginActive) {
        loginForm.setVisible(loginActive);
        loginForm.setManaged(loginActive);
        signupForm.setVisible(!loginActive);
        signupForm.setManaged(!loginActive);
        loginForm.setOpacity(loginActive ? 1.0 : 0.0);
        signupForm.setOpacity(loginActive ? 0.0 : 1.0);
    }

    private void showError(String message) {
        errorLabel.setText(message == null ? "" : message);
    }

    private void clearError() {
        errorLabel.setText("");
    }
}
