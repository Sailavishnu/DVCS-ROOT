package com.dvcs.client.dashboard.workspace;

import java.net.URL;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class WorkspaceCardController {

    private static final String ICON_FOLDER_URL = "https://img.icons8.com/fluency-systems-filled/96/00ff88/folder-invoices.png";

    @FXML
    private Label titleLabel;

    @FXML
    private ImageView iconImageView;

    @FXML
    private void initialize() {
        setIcon(ICON_FOLDER_URL);
    }

    public void setTitle(String title) {
        if (titleLabel != null) {
            titleLabel.setText(title == null || title.isBlank() ? "Workspace" : title);
        }
    }

    public void setIcon(String resourcePath) {
        if (iconImageView == null || resourcePath == null || resourcePath.isBlank()) {
            return;
        }
        if (resourcePath.startsWith("http://") || resourcePath.startsWith("https://")) {
            iconImageView.setImage(new Image(resourcePath, true));
            return;
        }
        URL iconUrl = WorkspaceCardController.class.getResource(resourcePath);
        if (iconUrl != null) {
            iconImageView.setImage(new Image(iconUrl.toExternalForm(), true));
        }
    }
}
