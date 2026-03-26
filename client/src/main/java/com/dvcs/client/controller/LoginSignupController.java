package com.dvcs.client.controller;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class LoginSignupController {

    @FXML
    private StackPane rootPane;

    @FXML
    private HBox mainContainer;

    @FXML
    private StackPane formPane;

    @FXML
    private StackPane infoPane;

    @FXML
    private VBox loginForm;

    @FXML
    private VBox signupForm;

    @FXML
    private StackPane formStack;

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
    private Label switchPromptLabel;

    @FXML
    private Label switchActionLabel;

    @FXML
    private Label formTitleLabel;

    @FXML
    private Label formSubtitleLabel;

    @FXML
    private Label infoTitleLabel;

    @FXML
    private Label infoDescriptionLabel;

    private boolean loginMode = true; // true = login, false = signup
    private boolean isAnimating = false;

    @FXML
    private void initialize() {
        setActiveForm(loginMode);
        updateModeTexts();

        // Clip the main container so panels never draw outside its bounds
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(mainContainer.widthProperty());
        clip.heightProperty().bind(mainContainer.heightProperty());
        mainContainer.setClip(clip);
    }

    @FXML
    private void onSwitchMode(MouseEvent event) {
        if (!isAnimating) {
            toggleModeWithAnimation();
        }
    }

    @FXML
    private void onLogin() {
        clearError();
        String username = loginUsernameField.getText() != null ? loginUsernameField.getText().trim() : "";
        String password = loginPasswordField.getText() != null ? loginPasswordField.getText() : "";

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }

        // TODO: integrate with AuthService / MongoDB later
        System.out.println("Login with username=" + username);
    }

    @FXML
    private void onSignup() {
        clearError();
        String username = signupUsernameField.getText() != null ? signupUsernameField.getText().trim() : "";
        String password = signupPasswordField.getText() != null ? signupPasswordField.getText() : "";
        String confirm = signupConfirmPasswordField.getText() != null ? signupConfirmPasswordField.getText() : "";

        if (username.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            showError("All fields are required.");
            return;
        }

        if (!password.equals(confirm)) {
            showError("Passwords do not match.");
            return;
        }

        // TODO: integrate with AuthService / MongoDB later
        System.out.println("Signup with username=" + username);
    }

    @FXML
    private void onForgotPassword() {
        // Placeholder - could open a dialog later
        System.out.println("Forgot password clicked");
    }

    private void toggleModeWithAnimation() {
        isAnimating = true;

        // Prepare the next form before animation starts to avoid layout shift
        boolean nextModeIsLogin = !loginMode;
        setActiveForm(nextModeIsLogin);

        // Distance between the two panels = panel width + spacing
        double cardWidth = formPane.getBoundsInParent().getWidth();
        if (cardWidth <= 0) {
            cardWidth = 420; // fallback to design width
        }
        double offset = cardWidth + mainContainer.getSpacing();

        // Slide animations (single smooth phase)
        TranslateTransition formSlide = new TranslateTransition(Duration.millis(500), formPane);
        TranslateTransition infoSlide = new TranslateTransition(Duration.millis(500), infoPane);
        formSlide.setInterpolator(Interpolator.EASE_BOTH);
        infoSlide.setInterpolator(Interpolator.EASE_BOTH);

        // Soft fade for subtle depth effect
        FadeTransition formFade = new FadeTransition(Duration.millis(500), formPane);
        formFade.setFromValue(1.0);
        formFade.setToValue(0.85);
        formFade.setAutoReverse(true);
        formFade.setCycleCount(2);

        FadeTransition infoFade = new FadeTransition(Duration.millis(500), infoPane);
        infoFade.setFromValue(1.0);
        infoFade.setToValue(0.85);
        infoFade.setAutoReverse(true);
        infoFade.setCycleCount(2);

        if (loginMode) {
            // Login -> Signup
            formSlide.setFromX(0);
            formSlide.setToX(offset);
            infoSlide.setFromX(0);
            infoSlide.setToX(-offset);
        } else {
            // Signup -> Login
            formSlide.setFromX(0);
            formSlide.setToX(-offset);
            infoSlide.setFromX(0);
            infoSlide.setToX(offset);
        }

        ParallelTransition parallel = new ParallelTransition(formSlide, infoSlide, formFade, infoFade);
        parallel.setOnFinished(e -> {
            // Reset translations
            formPane.setTranslateX(0);
            infoPane.setTranslateX(0);

            // Swap panes in HBox so sides are actually swapped
            if (loginMode) {
                mainContainer.getChildren().setAll(infoPane, formPane);
            } else {
                mainContainer.getChildren().setAll(formPane, infoPane);
            }

            // Toggle mode and update UI content
            loginMode = !loginMode;
            updateModeTexts();
            isAnimating = false;
        });

        parallel.play();
    }

    private void updateModeTexts() {
        clearError();
        if (loginMode) {
            formTitleLabel.setText("Welcome back");
            formSubtitleLabel.setText("Please enter your credentials to access the workspace.");

            switchPromptLabel.setText("Don't have an account?");
            switchActionLabel.setText("Sign Up");

            infoTitleLabel.setText("Secure, focused workspace.");
            infoDescriptionLabel.setText(
                    "Pick up where you left off, review recent edits, " +
                            "and stay in sync across your projects.");
        } else {
            formTitleLabel.setText("Join the Atelier");
            formSubtitleLabel
                    .setText("Begin your journey in a space designed for creative excellence and meticulous curation.");

            switchPromptLabel.setText("Already have an account?");
            switchActionLabel.setText("Sign In");

            infoTitleLabel.setText("Built for creative teams.");
            infoDescriptionLabel.setText(
                    "Create your account, invite collaborators, and version every document " +
                            "with reliable history and instant rollback.");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
    }

    private void clearError() {
        errorLabel.setText("");
    }

    private void setActiveForm(boolean loginActive) {
        if (loginActive) {
            loginForm.setVisible(true);
            loginForm.setManaged(true);
            signupForm.setVisible(false);
            signupForm.setManaged(false);
        } else {
            loginForm.setVisible(false);
            loginForm.setManaged(false);
            signupForm.setVisible(true);
            signupForm.setManaged(true);
        }
    }
}
