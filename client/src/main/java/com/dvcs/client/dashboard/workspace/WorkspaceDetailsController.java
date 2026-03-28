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
        setDetails(details, null, null);
    }

    public void setDetails(WorkspaceDetails details, String selectedFolder, String selectedFile) {
        if (details == null) {
            return;
        }

        if (workspaceTitleLabel != null) {
            workspaceTitleLabel.setText(details.workspaceName() == null ? "Workspace" : details.workspaceName());
        }
        if (foldersListView != null) {
            foldersListView.getItems().setAll(details.folders());
            selectItemIfPresent(foldersListView, selectedFolder);
        }
        if (filesListView != null) {
            filesListView.getItems().setAll(details.files());
            selectItemIfPresent(filesListView, selectedFile);
        }
        if (collaboratorsListView != null) {
            collaboratorsListView.getItems().setAll(details.collaborators());
        }
    }

    private static void selectItemIfPresent(ListView<String> listView, String value) {
        if (listView == null || value == null || value.isBlank()) {
            return;
        }
        int index = listView.getItems().indexOf(value);
        if (index >= 0) {
            listView.getSelectionModel().select(index);
            listView.scrollTo(index);
        }
    }
}
