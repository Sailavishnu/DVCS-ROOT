package com.dvcs.client.ui;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class PopupDialogs {

    public enum Theme {
        GREEN,
        BLUE
    }

    private PopupDialogs() {
    }

    public static Optional<String> showTextInput(
            Window owner,
            String title,
            String subtitle,
            String fieldLabel,
            String prompt,
            String actionText) {
        return showTextInput(owner, title, subtitle, fieldLabel, prompt, actionText, Theme.GREEN);
    }

    public static Optional<String> showTextInput(
            Window owner,
            String title,
            String subtitle,
            String fieldLabel,
            String prompt,
            String actionText,
            Theme theme) {
        AtomicReference<String> valueRef = new AtomicReference<>();

        Label titleLabel = new Label(defaultValue(title));
        titleLabel.getStyleClass().add("neon-popup-title");

        Label subtitleLabel = new Label(defaultValue(subtitle));
        subtitleLabel.getStyleClass().add("neon-popup-subtitle");
        subtitleLabel.setWrapText(true);

        Label fieldLabelNode = new Label(defaultValue(fieldLabel));
        fieldLabelNode.getStyleClass().add("neon-popup-field-label");

        TextField textField = new TextField();
        textField.setPromptText(defaultValue(prompt));
        textField.getStyleClass().add("neon-popup-input");

        Button primaryButton = new Button(defaultValue(actionText));
        primaryButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-primary");
        primaryButton.setDisable(true);
        textField.textProperty().addListener((obs, oldText, newText) -> {
            primaryButton.setDisable(newText == null || newText.trim().isEmpty());
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-secondary");

        VBox content = new VBox(10, fieldLabelNode, textField);
        content.getStyleClass().add("neon-popup-content");

        Stage stage = new Stage();
        configureStage(stage, owner);
        StackPane root = createRoot(stage, titleLabel, subtitleLabel, content, primaryButton, cancelButton, theme);

        primaryButton.setOnAction(event -> {
            String text = textField.getText() == null ? "" : textField.getText().trim();
            if (!text.isEmpty()) {
                valueRef.set(text);
                stage.close();
            }
        });

        cancelButton.setOnAction(event -> stage.close());

        stage.setOnShown(event -> textField.requestFocus());
        stage.setScene(createScene(root));
        stage.showAndWait();
        return Optional.ofNullable(valueRef.get());
    }

    public static Optional<String> showChoice(
            Window owner,
            String title,
            String subtitle,
            String fieldLabel,
            List<String> options,
            String defaultOption,
            String actionText) {
        if (options == null || options.isEmpty()) {
            return Optional.empty();
        }

        AtomicReference<String> valueRef = new AtomicReference<>();

        Label titleLabel = new Label(defaultValue(title));
        titleLabel.getStyleClass().add("neon-popup-title");

        Label subtitleLabel = new Label(defaultValue(subtitle));
        subtitleLabel.getStyleClass().add("neon-popup-subtitle");
        subtitleLabel.setWrapText(true);

        Label fieldLabelNode = new Label(defaultValue(fieldLabel));
        fieldLabelNode.getStyleClass().add("neon-popup-field-label");

        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().setAll(options);
        comboBox.getStyleClass().add("neon-popup-combo");
        comboBox.setMaxWidth(Double.MAX_VALUE);
        comboBox.setValue(options.contains(defaultOption) ? defaultOption : options.getFirst());

        Button primaryButton = new Button(defaultValue(actionText));
        primaryButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-primary");

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-secondary");

        VBox content = new VBox(10, fieldLabelNode, comboBox);
        content.getStyleClass().add("neon-popup-content");

        Stage stage = new Stage();
        configureStage(stage, owner);
        StackPane root = createRoot(stage, titleLabel, subtitleLabel, content, primaryButton, cancelButton,
                Theme.GREEN);

        primaryButton.setOnAction(event -> {
            String value = comboBox.getValue();
            if (value != null && !value.isBlank()) {
                valueRef.set(value);
                stage.close();
            }
        });

        cancelButton.setOnAction(event -> stage.close());

        stage.setScene(createScene(root));
        stage.showAndWait();
        return Optional.ofNullable(valueRef.get());
    }

    public static void showInfo(Window owner, String title, String message) {
        showInfo(owner, title, message, Theme.GREEN);
    }

    public static void showInfo(Window owner, String title, String message, Theme theme) {
        showMessage(owner, title, message, false, theme);
    }

    public static void showError(Window owner, String title, String message) {
        showMessage(owner, title, message, true, Theme.GREEN);
    }

    private static void showMessage(Window owner, String title, String message, boolean error, Theme theme) {
        Label titleLabel = new Label(defaultValue(title));
        titleLabel.getStyleClass().add("neon-popup-title");

        Label subtitleLabel = new Label("");
        subtitleLabel.getStyleClass().add("neon-popup-subtitle");

        Label messageLabel = new Label(defaultValue(message));
        messageLabel.setWrapText(true);
        messageLabel.getStyleClass().add(error ? "neon-popup-message-error" : "neon-popup-message");

        Button okButton = new Button("OK");
        okButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-primary");

        Stage stage = new Stage();
        configureStage(stage, owner);

        VBox content = new VBox(4, messageLabel);
        content.getStyleClass().add("neon-popup-content");

        Theme dialogTheme = theme == null ? Theme.GREEN : theme;
        StackPane root = createRoot(stage, titleLabel, subtitleLabel, content, okButton, null, dialogTheme);
        okButton.setOnAction(event -> stage.close());

        stage.setScene(createScene(root));
        stage.showAndWait();
    }

    private static StackPane createRoot(
            Stage stage,
            Label titleLabel,
            Label subtitleLabel,
            VBox content,
            Button primaryButton,
            Button secondaryButton,
            Theme theme) {
        Button closeButton = new Button("x");
        closeButton.getStyleClass().add("neon-popup-close-button");
        closeButton.setOnAction(event -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(spacer, closeButton);
        topBar.setAlignment(Pos.CENTER_RIGHT);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (secondaryButton != null) {
            actions.getChildren().add(secondaryButton);
        }
        if (primaryButton != null) {
            actions.getChildren().add(primaryButton);
        }

        VBox card = new VBox(16, topBar, titleLabel, subtitleLabel, content, actions);
        card.getStyleClass().add("neon-popup-card");
        if (theme == Theme.BLUE) {
            card.getStyleClass().add("neon-popup-theme-blue");
        }
        card.setMaxWidth(560);
        card.setPrefWidth(560);
        card.setPadding(new Insets(26, 30, 24, 30));

        StackPane overlay = new StackPane(card);
        overlay.getStyleClass().add("neon-popup-overlay");
        StackPane.setAlignment(card, Pos.CENTER);
        return overlay;
    }

    private static void configureStage(Stage stage, Window owner) {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
        if (owner != null) {
            stage.initOwner(owner);
        }
    }

    private static Scene createScene(StackPane root) {
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        URL cssUrl = PopupDialogs.class.getResource("/css/dashboard.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        return scene;
    }

    private static String defaultValue(String text) {
        return Objects.requireNonNullElse(text, "");
    }
}