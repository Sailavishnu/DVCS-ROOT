package com.dvcs.client.dashboard.workspace;

import com.dvcs.client.dashboard.data.WorkspaceDetails;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

public class WorkspaceDetailsController {

    @FXML
    private Label workspaceTitleLabel;

    @FXML
    private ListView<String> foldersListView;

    @FXML
    private ListView<String> filesListView;

    @FXML
    private ListView<String> collaboratorsListView;

    public void setDetails(WorkspaceDetails details) {
        if (details == null) {
            return;
        }

        if (workspaceTitleLabel != null) {
            workspaceTitleLabel.setText(details.workspaceName() == null ? "Workspace" : details.workspaceName());
        }
        if (foldersListView != null) {
            foldersListView.getItems().setAll(details.folders());
        }
        if (filesListView != null) {
            filesListView.getItems().setAll(details.files());
        }
        if (collaboratorsListView != null) {
            collaboratorsListView.getItems().setAll(details.collaborators());
        }
    }
}
