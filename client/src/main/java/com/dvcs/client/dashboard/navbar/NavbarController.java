package com.dvcs.client.dashboard.navbar;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class NavbarController {

    @FXML
    private TextField searchField;

    @FXML
    private Label usernameLabel;

    public void setUsername(String username) {
        if (usernameLabel != null) {
            usernameLabel.setText(username == null || username.isBlank() ? "Profile" : username);
        }
    }

    public String getSearchQuery() {
        return searchField == null ? "" : (searchField.getText() == null ? "" : searchField.getText().trim());
    }
}
