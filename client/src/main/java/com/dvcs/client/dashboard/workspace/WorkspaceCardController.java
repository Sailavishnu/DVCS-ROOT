package com.dvcs.client.dashboard.workspace;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class WorkspaceCardController {

    @FXML
    private Label titleLabel;

    public void setTitle(String title) {
        if (titleLabel != null) {
            titleLabel.setText(title == null || title.isBlank() ? "Workspace" : title);
        }
    }
}
