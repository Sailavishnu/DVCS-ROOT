package com.dvcs.client.dashboard.navbar;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.TextField;

public class NavbarController {

    @FXML
    private TextField searchField;

    private Consumer<String> onSearchSubmitted;
    private Runnable onNotificationRequested;

    public void configureHandlers(Consumer<String> onSearchSubmitted, Runnable onNotificationRequested) {
        this.onSearchSubmitted = onSearchSubmitted;
        this.onNotificationRequested = onNotificationRequested;
    }

    @FXML
    public void setUsername(String username) {
        // Profile now uses avatar-only UI; method kept for compatibility with existing
        // calls.
    }

    @FXML
    private void onSearchSubmit() {
        if (onSearchSubmitted != null) {
            onSearchSubmitted.accept(getSearchQuery());
        }
    }

    @FXML
    private void onNotificationClick(MouseEvent event) {
        Objects.requireNonNull(event, "event");
        if (onNotificationRequested != null) {
            onNotificationRequested.run();
        }
    }

    public String getSearchQuery() {
        return searchField == null ? "" : (searchField.getText() == null ? "" : searchField.getText().trim());
    }

    public void setSearchQuery(String query) {
        if (searchField != null) {
            searchField.setText(query == null ? "" : query);
        }
    }

    @FXML
    private void onSearchIconClick(MouseEvent event) {
        Objects.requireNonNull(event, "event");
        onSearchSubmit();
    }
}
