package com.dvcs.client.dashboard.navbar;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;

public class NavbarController {

    @FXML
    private TextField searchField;

    @FXML
    public void setUsername(String username) {
        // Profile now uses avatar-only UI; method kept for compatibility with existing
        // calls.
    }

    public String getSearchQuery() {
        return searchField == null ? "" : (searchField.getText() == null ? "" : searchField.getText().trim());
    }
}
