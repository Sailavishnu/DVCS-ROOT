package com.dvcs.client.workspacepage.controller;

import com.dvcs.client.workspacepage.model.FileItemModel;
import com.dvcs.client.workspacepage.model.FolderModel;
import com.dvcs.client.workspacepage.model.UserModel;
import com.dvcs.client.workspacepage.model.WorkspacePageModel;
import com.dvcs.client.workspacepage.service.FileService;
import com.dvcs.client.workspacepage.service.WorkspaceService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.bson.types.ObjectId;

public final class WorkspaceController {

    @FXML
    private BorderPane root;

    @FXML
    private Label workspaceNameHeader;

    @FXML
    private HBox mainContainer;

    @FXML
    private VBox leftPanel;

    @FXML
    private TreeView<String> fileTreeView;

    @FXML
    private VBox readmeSection;

    @FXML
    private TextArea readmeTextArea;

    @FXML
    private VBox centerPanel;

    @FXML
    private Label fileNameLabel;

    @FXML
    private Label lastCommitMessageLabel;

    @FXML
    private Label lastCommitTimeLabel;

    @FXML
    private Button editButton;

    @FXML
    private TextArea fileEditor;

    @FXML
    private TextField commitMessageField;

    @FXML
    private Button commitButton;

    @FXML
    private VBox rightPanel;

    @FXML
    private Label totalCommitsLabel;

    @FXML
    private ListView<String> collaboratorsList;

    @FXML
    private Button settingsButton;

    private final FileController fileController = new FileController();

    private WorkspaceService workspaceService;
    private FileService fileService;
    private ObjectId workspaceId;
    private ObjectId currentUserId;

    private WorkspacePageModel currentModel;
    private FileItemModel selectedFile;
    private boolean editing;

    private final Map<TreeItem<String>, FileItemModel> fileByTreeItem = new HashMap<>();

    @FXML
    private void initialize() {
        HBox.setHgrow(leftPanel, Priority.ALWAYS);
        HBox.setHgrow(centerPanel, Priority.ALWAYS);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        mainContainer.widthProperty().addListener((obs, oldWidth, newWidth) -> applyLayoutRatios());
        applyLayoutRatios();

        setEditing(false);

        fileTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            FileItemModel file = fileByTreeItem.get(newItem);
            if (file != null) {
                openFile(file);
            }
        });
    }

    public void configure(
            WorkspaceService workspaceService,
            FileService fileService,
            ObjectId workspaceId,
            ObjectId currentUserId,
            String preselectedFolder,
            String preselectedFile) {
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService");
        this.fileService = Objects.requireNonNull(fileService, "fileService");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.currentUserId = Objects.requireNonNull(currentUserId, "currentUserId");

        reloadWorkspace();
        preselectFile(preselectedFolder, preselectedFile);
    }

    private void applyLayoutRatios() {
        double width = mainContainer.getWidth();
        if (width <= 0) {
            return;
        }
        leftPanel.setPrefWidth(width * 0.20);
        centerPanel.setPrefWidth(width * 0.60);
        rightPanel.setPrefWidth(width * 0.20);
    }

    private void reloadWorkspace() {
        currentModel = workspaceService.loadWorkspace(workspaceId);
        workspaceNameHeader.setText(currentModel.workspaceName());
        totalCommitsLabel.setText(String.valueOf(currentModel.totalCommits()));

        collaboratorsList.getItems().setAll(currentModel.collaborators().stream().map(UserModel::username).toList());

        String readme = currentModel.readmeContent();
        boolean hasReadme = readme != null && !readme.isBlank();
        readmeSection.setManaged(hasReadme);
        readmeSection.setVisible(hasReadme);
        readmeTextArea.setText(hasReadme ? readme : "");

        buildTree();

        if (selectedFile != null) {
            // rebind selected file metadata after commit/reload
            selectedFile = currentModel.folders().stream()
                    .flatMap(folder -> folder.files().stream())
                    .filter(file -> file.fileId().equals(selectedFile.fileId()))
                    .findFirst()
                    .orElse(null);
            if (selectedFile != null) {
                openFile(selectedFile);
            }
        }
    }

    private void buildTree() {
        fileByTreeItem.clear();

        TreeItem<String> rootItem = new TreeItem<>("Workspace");
        rootItem.setExpanded(true);

        for (FolderModel folder : currentModel.folders()) {
            TreeItem<String> folderItem = new TreeItem<>(folder.folderName());
            folderItem.setExpanded(true);
            for (FileItemModel file : folder.files()) {
                TreeItem<String> fileItem = new TreeItem<>(file.filename());
                fileByTreeItem.put(fileItem, file);
                folderItem.getChildren().add(fileItem);
            }
            rootItem.getChildren().add(folderItem);
        }

        fileTreeView.setRoot(rootItem);
        fileTreeView.setShowRoot(false);
    }

    private void preselectFile(String folderName, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        workspaceService.findFileByName(workspaceId, folderName, fileName)
                .ifPresent(this::openFile);
    }

    private void openFile(FileItemModel file) {
        selectedFile = file;
        fileController.renderFileHeader(file, fileNameLabel, lastCommitMessageLabel, lastCommitTimeLabel);
        fileEditor.setText(fileService.loadContent(file.fileId()));
        setEditing(false);
    }

    @FXML
    private void onEditToggle() {
        if (selectedFile == null) {
            showError("Select a file first");
            return;
        }
        setEditing(!editing);
    }

    @FXML
    private void onCommit() {
        if (selectedFile == null) {
            showError("Select a file first");
            return;
        }

        try {
            fileService.commit(selectedFile.fileId(), currentUserId, commitMessageField.getText(),
                    fileEditor.getText());
            commitMessageField.clear();
            reloadWorkspace();
            setEditing(false);
            showInfo("Committed successfully");
        } catch (Exception e) {
            showError("Commit failed: " + e.getMessage());
        }
    }

    @FXML
    private void onOpenSettings() {
        Scene ownerScene = resolveOwnerScene();
        if (ownerScene == null) {
            showError("Unable to open settings: scene is not ready yet.");
            return;
        }

        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        Stage owner = (Stage) ownerScene.getWindow();
        modal.initOwner(owner);
        modal.setTitle("Workspace Settings");

        BorderPane modalRoot = new BorderPane();
        modalRoot.getStyleClass().add("workspace-settings-root");

        HBox split = new HBox(16);
        split.setPadding(new Insets(18));

        VBox menu = new VBox(10);
        menu.getStyleClass().add("workspace-settings-menu");
        Button generalButton = new Button("General");
        Button collaborationButton = new Button("Collaboration");
        Button dangerButton = new Button("Danger Zone");
        menu.getChildren().addAll(generalButton, collaborationButton, dangerButton);

        StackPane contentPanel = new StackPane();
        contentPanel.getStyleClass().add("workspace-settings-content");
        HBox.setHgrow(contentPanel, Priority.ALWAYS);

        generalButton.setOnAction(e -> contentPanel.getChildren().setAll(buildGeneralPane()));
        collaborationButton.setOnAction(e -> contentPanel.getChildren().setAll(buildCollaborationPane()));
        dangerButton.setOnAction(e -> contentPanel.getChildren().setAll(buildDangerPane(modal)));

        contentPanel.getChildren().setAll(buildGeneralPane());
        split.getChildren().addAll(menu, contentPanel);
        modalRoot.setCenter(split);

        Scene scene = new Scene(modalRoot,
                Math.max(900, ownerScene.getWidth() * 0.70),
                Math.max(520, ownerScene.getHeight() * 0.70));
        scene.getStylesheets()
                .add(Objects.requireNonNull(getClass().getResource("/css/dashboard.css")).toExternalForm());
        modal.setScene(scene);
        modal.showAndWait();
    }

    private Scene resolveOwnerScene() {
        if (root != null && root.getScene() != null) {
            return root.getScene();
        }
        if (mainContainer != null && mainContainer.getScene() != null) {
            return mainContainer.getScene();
        }
        if (settingsButton != null && settingsButton.getScene() != null) {
            return settingsButton.getScene();
        }
        return null;
    }

    private VBox buildGeneralPane() {
        VBox pane = new VBox(10);
        pane.getStyleClass().add("workspace-settings-pane");

        Label title = new Label("General");
        title.getStyleClass().add("workspace-settings-title");

        TextField workspaceNameField = new TextField(currentModel.workspaceName());
        workspaceNameField.setPromptText("Workspace name");

        Button save = new Button("Save changes");
        save.setOnAction(e -> {
            try {
                workspaceService.updateWorkspaceName(workspaceId, workspaceNameField.getText());
                reloadWorkspace();
                showInfo("Workspace name updated");
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        });

        pane.getChildren().addAll(title, workspaceNameField, save);
        return pane;
    }

    private VBox buildCollaborationPane() {
        VBox pane = new VBox(10);
        pane.getStyleClass().add("workspace-settings-pane");

        Label title = new Label("Collaboration");
        title.getStyleClass().add("workspace-settings-title");

        ListView<String> current = new ListView<>();
        current.getItems().setAll(currentModel.collaborators().stream().map(UserModel::username).toList());

        ComboBox<UserModel> usersCombo = new ComboBox<>();
        usersCombo.getItems().setAll(workspaceService.loadAllUsers());
        usersCombo.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(UserModel item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.username());
            }
        });
        usersCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(UserModel item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select user" : item.username());
            }
        });

        Button add = new Button("Add collaborator");
        add.setOnAction(e -> {
            UserModel selected = usersCombo.getValue();
            if (selected == null) {
                showError("Select a user");
                return;
            }
            workspaceService.addCollaborator(workspaceId, selected.userId());
            reloadWorkspace();
            current.getItems().setAll(currentModel.collaborators().stream().map(UserModel::username).toList());
        });

        Button remove = new Button("Remove selected");
        remove.setOnAction(e -> {
            String selectedUsername = current.getSelectionModel().getSelectedItem();
            if (selectedUsername == null) {
                showError("Select a collaborator");
                return;
            }
            currentModel.collaborators().stream()
                    .filter(user -> user.username().equals(selectedUsername))
                    .findFirst()
                    .ifPresent(user -> workspaceService.removeCollaborator(workspaceId, user.userId()));

            reloadWorkspace();
            current.getItems().setAll(currentModel.collaborators().stream().map(UserModel::username).toList());
        });

        pane.getChildren().addAll(title, current, usersCombo, add, remove);
        return pane;
    }

    private VBox buildDangerPane(Stage modal) {
        VBox pane = new VBox(10);
        pane.getStyleClass().add("workspace-settings-pane");

        Label title = new Label("Danger Zone");
        title.getStyleClass().add("workspace-settings-title");

        Label note = new Label("Type YES to confirm workspace deletion");
        TextField confirmField = new TextField();
        Button delete = new Button("Delete workspace");
        delete.getStyleClass().add("workspace-danger-button");

        delete.setOnAction(e -> {
            if (!"YES".equals(confirmField.getText())) {
                showError("Type YES to confirm");
                return;
            }

            workspaceService.deleteWorkspace(workspaceId);
            modal.close();
            ((Stage) root.getScene().getWindow()).close();
        });

        pane.getChildren().addAll(title, note, confirmField, delete);
        return pane;
    }

    private void setEditing(boolean editing) {
        this.editing = editing;
        fileController.setEditorMode(fileEditor, editing);
        editButton.setText(editing ? "Cancel" : "Edit");
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Workspace");
        alert.showAndWait();
    }
}
