package com.dvcs.client.dashboard.navbar;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class NavbarController {

    @FXML
    private TextField searchField;

    @FXML
    private Label avatarInitialsLabel;

    private Consumer<String> onSearchSubmitted;
    private Runnable onNotificationRequested;
    private Runnable onProfileRequested;

    public void configureHandlers(
            Consumer<String> onSearchSubmitted,
            Runnable onNotificationRequested,
            Runnable onProfileRequested) {
        this.onSearchSubmitted = onSearchSubmitted;
        this.onNotificationRequested = onNotificationRequested;
        this.onProfileRequested = onProfileRequested;
    }

    @FXML
    public void setUsername(String username) {
        setProfileIdentity(null, username);
    }

    public void setProfileIdentity(String name, String username) {
        if (avatarInitialsLabel != null) {
            avatarInitialsLabel.setText(computeInitials(name, username));
        }
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

    @FXML
    private void onProfileClick(MouseEvent event) {
        Objects.requireNonNull(event, "event");
        if (onProfileRequested != null) {
            onProfileRequested.run();
        }
    }

    private static String computeInitials(String name, String username) {
        String source = (name != null && !name.isBlank()) ? name.trim() : (username == null ? "" : username.trim());
        if (source.isEmpty()) {
            return "U";
        }

        String[] parts = source.split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() == 2) {
                break;
            }
        }

        if (initials.isEmpty()) {
            initials.append(Character.toUpperCase(source.charAt(0)));
        }
        return initials.toString();
    }
}
