package com.dvcs.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class LandingController {

    @FXML
    private Label welcomeLabel;

    public void setWelcomeMessage(String message) {
        if (welcomeLabel != null) {
            welcomeLabel.setText(message == null || message.isBlank() ? "Welcome" : message);
        }
    }
}
