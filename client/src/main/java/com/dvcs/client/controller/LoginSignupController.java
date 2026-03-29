package com.dvcs.client.controller;

import com.dvcs.client.auth.service.UserService;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
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
    private TextField loginPasswordVisibleField;

    @FXML
    private TextField signupUsernameField;

    @FXML
    private PasswordField signupPasswordField;

    @FXML
    private TextField signupPasswordVisibleField;

    @FXML
    private PasswordField signupConfirmPasswordField;

    @FXML
    private TextField signupConfirmPasswordVisibleField;

    @FXML
    private StackPane loginPasswordRow;

    @FXML
    private StackPane signupPasswordRow;

    @FXML
    private StackPane signupConfirmPasswordRow;

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

    @FXML
    private Label loginUsernameHintLabel;

    @FXML
    private Label loginPasswordHintLabel;

    @FXML
    private Label signupUsernameHintLabel;

    @FXML
    private Label signupPasswordHintLabel;

    @FXML
    private Label signupConfirmHintLabel;

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
        setupPasswordVisibility(loginPasswordField, loginPasswordVisibleField);
        setupPasswordVisibility(signupPasswordField, signupPasswordVisibleField);
        setupPasswordVisibility(signupConfirmPasswordField, signupConfirmPasswordVisibleField);

        setActiveForm(true);
        applyModeTexts(false, null);

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

        Node outgoingUser = loginMode ? loginUsernameField : signupUsernameField;
        Node outgoingPassword = loginMode ? loginPasswordRow : signupPasswordRow;
        Node outgoingConfirm = loginMode ? null : signupConfirmPasswordRow;
        Node outgoingPrimaryButton = loginMode ? loginButton : signupButton;

        // Prepare incoming form before the second phase starts.
        incoming.setVisible(true);
        incoming.setManaged(true);

        ParallelTransition phaseOut = new ParallelTransition();
        phaseOut.getChildren().addAll(
                createMoveFade(outgoingUser, 0, loginMode ? -24 : 24, 1.0, 0.0, 330),
                createMoveFade(outgoingPassword, 0, loginMode ? 24 : -24, 1.0, 0.0, 330),
                createMoveFade(outgoingPrimaryButton, 0, 14, 1.0, 0.0, 310),
                createMoveFade(switchPromptLabel, 0, 8, 1.0, 0.0, 270),
                createMoveFade(switchActionLabel, 0, 8, 1.0, 0.0, 270),
                createMoveFade(formTitleLabel, 0, -10, 1.0, 0.0, 270));

        if (outgoingConfirm != null) {
            phaseOut.getChildren().add(createMoveFade(outgoingConfirm, 0, 24, 1.0, 0.0, 330));
        }

        phaseOut.setOnFinished(e -> {
            resetNode(outgoingUser);
            resetNode(outgoingPassword);
            resetNode(outgoingPrimaryButton);
            if (outgoingConfirm != null) {
                resetNode(outgoingConfirm);
            }

            outgoing.setVisible(false);
            outgoing.setManaged(false);

            loginMode = !loginMode;
            applyModeTexts(true, () -> {
                isAnimating = false;
            });

            Node incomingUser = loginMode ? loginUsernameField : signupUsernameField;
            Node incomingPassword = loginMode ? loginPasswordRow : signupPasswordRow;
            Node incomingConfirm = loginMode ? null : signupConfirmPasswordRow;
            Node incomingPrimaryButton = loginMode ? loginButton : signupButton;

            primeIncomingNode(incomingUser, loginMode ? -24 : 24);
            primeIncomingNode(incomingPassword, loginMode ? 24 : -24);
            primeIncomingNode(incomingPrimaryButton, 14);
            if (incomingConfirm != null) {
                primeIncomingNode(incomingConfirm, 24);
            }
            primeIncomingNode(switchPromptLabel, 8);
            primeIncomingNode(switchActionLabel, 8);
            primeIncomingNode(formTitleLabel, -10);

            ParallelTransition phaseIn = new ParallelTransition();
            phaseIn.getChildren().addAll(
                    createMoveFade(incomingUser, incomingUser.getTranslateX(), 0, 0.0, 1.0, 420),
                    createMoveFade(incomingPassword, incomingPassword.getTranslateX(), 0, 0.0, 1.0, 420),
                    createMoveFade(incomingPrimaryButton, 14, 0, 0.0, 1.0, 390),
                    createMoveFade(switchPromptLabel, 8, 0, 0.0, 1.0, 370),
                    createMoveFade(switchActionLabel, 8, 0, 0.0, 1.0, 370),
                    createMoveFade(formTitleLabel, -10, 0, 0.0, 1.0, 370));

            if (incomingConfirm != null) {
                phaseIn.getChildren().add(createMoveFade(incomingConfirm, 24, 0, 0.0, 1.0, 420));
            }

            phaseIn.play();
        });

        phaseOut.play();
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

    private void applyModeTexts(boolean animated, Runnable onFinished) {
        if (loginMode) {
            if (animated) {
                startTypewriterSet(
                        "Login",
                        "Access your workspace securely.",
                        "Enter your username",
                        "Enter your password",
                        "Enter your username",
                        "Enter your password",
                        "Enter your confirm password",
                        "Login",
                        "Sign Up",
                        "Don't have an account?",
                        "Sign Up",
                        onFinished);
            } else {
                formTitleLabel.setText("Login");
                formSubtitleLabel.setText("Access your workspace securely.");
                loginUsernameHintLabel.setText("Enter your username");
                loginPasswordHintLabel.setText("Enter your password");
                signupUsernameHintLabel.setText("Enter your username");
                signupPasswordHintLabel.setText("Enter your password");
                signupConfirmHintLabel.setText("Enter your confirm password");
                loginButton.setText("Login");
                signupButton.setText("Sign Up");
                switchPromptLabel.setText("Don't have an account?");
                switchActionLabel.setText("Sign Up");
                if (onFinished != null) {
                    onFinished.run();
                }
            }
            setActiveForm(true);
        } else {
            if (animated) {
                startTypewriterSet(
                        "Sign Up",
                        "Create an account.",
                        "Enter your username",
                        "Enter your password",
                        "Enter your username",
                        "Enter your password",
                        "Enter your confirm password",
                        "Login",
                        "Sign Up",
                        "Already have an account?",
                        "Login",
                        onFinished);
            } else {
                formTitleLabel.setText("Sign Up");
                formSubtitleLabel.setText("Create an account.");
                loginUsernameHintLabel.setText("Enter your username");
                loginPasswordHintLabel.setText("Enter your password");
                signupUsernameHintLabel.setText("Enter your username");
                signupPasswordHintLabel.setText("Enter your password");
                signupConfirmHintLabel.setText("Enter your confirm password");
                loginButton.setText("Login");
                signupButton.setText("Sign Up");
                switchPromptLabel.setText("Already have an account?");
                switchActionLabel.setText("Login");
                if (onFinished != null) {
                    onFinished.run();
                }
            }
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

    @FXML
    private void onToggleLoginPassword() {
        togglePasswordVisibility(loginPasswordField, loginPasswordVisibleField);
    }

    @FXML
    private void onToggleSignupPassword() {
        togglePasswordVisibility(signupPasswordField, signupPasswordVisibleField);
    }

    @FXML
    private void onToggleSignupConfirmPassword() {
        togglePasswordVisibility(signupConfirmPasswordField, signupConfirmPasswordVisibleField);
    }

    private static void setupPasswordVisibility(PasswordField hiddenField, TextField visibleField) {
        visibleField.textProperty().bindBidirectional(hiddenField.textProperty());
        visibleField.setVisible(false);
        visibleField.setManaged(false);
    }

    private static void togglePasswordVisibility(PasswordField hiddenField, TextField visibleField) {
        boolean showPlainText = !visibleField.isVisible();
        visibleField.setVisible(showPlainText);
        visibleField.setManaged(showPlainText);
        hiddenField.setVisible(!showPlainText);
        hiddenField.setManaged(!showPlainText);
    }

    private static ParallelTransition createMoveFade(Node node, double fromX, double toX, double fromOpacity,
            double toOpacity, int millis) {
        node.setTranslateX(fromX);
        node.setOpacity(fromOpacity);
        FadeTransition fade = new FadeTransition(Duration.millis(millis), node);
        fade.setFromValue(fromOpacity);
        fade.setToValue(toOpacity);
        fade.setInterpolator(Interpolator.EASE_BOTH);

        Timeline move = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(node.translateXProperty(), fromX, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(millis),
                        new KeyValue(node.translateXProperty(), toX, Interpolator.EASE_BOTH)));
        return new ParallelTransition(fade, move);
    }

    private static void primeIncomingNode(Node node, double fromX) {
        node.setTranslateX(fromX);
        node.setOpacity(0.0);
    }

    private static void resetNode(Node node) {
        node.setTranslateX(0);
        node.setOpacity(1.0);
    }

    private void startTypewriterSet(
            String title,
            String subtitle,
            String loginUserHint,
            String loginPasswordHint,
            String signupUserHint,
            String signupPasswordHint,
            String signupConfirmHint,
            String loginButtonText,
            String signupButtonText,
            String prompt,
            String action,
            Runnable onFinished) {
        Timeline timeline = new Timeline();
        double maxEnd = 0;

        maxEnd = Math.max(maxEnd, addTypewriterKeyFrames(timeline, formTitleLabel, title, 0, 22));
        maxEnd = Math.max(maxEnd, addTypewriterKeyFrames(timeline, formSubtitleLabel, subtitle, 70, 18));
        maxEnd = Math.max(maxEnd, addTypewriterKeyFrames(timeline, loginUsernameHintLabel, loginUserHint, 110, 16));
        maxEnd = Math.max(maxEnd, addTypewriterKeyFrames(timeline, loginPasswordHintLabel, loginPasswordHint, 130, 16));
        maxEnd = Math.max(maxEnd, addTypewriterKeyFrames(timeline, signupUsernameHintLabel, signupUserHint, 150, 16));
        maxEnd = Math.max(maxEnd,
                addTypewriterKeyFrames(timeline, signupPasswordHintLabel, signupPasswordHint, 170, 16));
        maxEnd = Math.max(maxEnd, addTypewriterKeyFrames(timeline, signupConfirmHintLabel, signupConfirmHint, 190, 16));
        maxEnd = Math.max(maxEnd, addTypewriterKeyFrames(timeline, loginButton, loginButtonText, 220, 20));
        maxEnd = Math.max(maxEnd, addTypewriterKeyFrames(timeline, signupButton, signupButtonText, 240, 20));
        maxEnd = Math.max(maxEnd, addTypewriterKeyFrames(timeline, switchPromptLabel, prompt, 260, 18));
        maxEnd = Math.max(maxEnd, addTypewriterKeyFrames(timeline, switchActionLabel, action, 280, 22));

        if (onFinished != null) {
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(maxEnd + 20), e -> onFinished.run()));
        }
        timeline.play();
    }

    private static double addTypewriterKeyFrames(Timeline timeline, Labeled target, String text, double delayMs,
            double charMs) {
        String finalText = text == null ? "" : text;
        target.setText("");
        if (finalText.isEmpty()) {
            return delayMs;
        }

        for (int i = 1; i <= finalText.length(); i++) {
            final int end = i;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delayMs + (charMs * i)),
                    e -> target.setText(finalText.substring(0, end))));
        }
        return delayMs + (charMs * finalText.length());
    }
}
