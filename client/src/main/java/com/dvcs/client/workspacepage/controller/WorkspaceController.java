package com.dvcs.client.workspacepage.controller;

import com.dvcs.client.workspacepage.model.FileItemModel;
import com.dvcs.client.workspacepage.model.FolderModel;
import com.dvcs.client.workspacepage.model.UserModel;
import com.dvcs.client.workspacepage.model.WorkspacePageModel;
import com.dvcs.client.workspacepage.service.FileService;
import com.dvcs.client.workspacepage.service.FileSystemService;
import com.dvcs.client.workspacepage.service.WorkspaceService;
import com.dvcs.client.workspacepage.utils.DateTimeUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.bson.types.ObjectId;

public final class WorkspaceController {

    @FXML
    private BorderPane root;

    @FXML
    private Label workspaceNameHeader;

    @FXML
    private HBox actionBar;

    @FXML
    private VBox mainContainer;

    @FXML
    private TableView<WorkspaceFileRow> fileTable;

    @FXML
    private TableColumn<WorkspaceFileRow, String> iconColumn;

    @FXML
    private TableColumn<WorkspaceFileRow, String> nameColumn;

    @FXML
    private TableColumn<WorkspaceFileRow, String> lastCommitColumn;

    @FXML
    private TableColumn<WorkspaceFileRow, String> lastModifiedColumn;

    @FXML
    private Label totalCommitsLabel;

    @FXML
    private ListView<String> collaboratorsList;

    @FXML
    private VBox readmeSection;

    @FXML
    private TextArea readmeTextArea;

    private final FileSystemService fileSystemService = new FileSystemService();

    private WorkspaceService workspaceService;
    private FileService fileService;
    private ObjectId workspaceId;
    private ObjectId currentUserId;

    private WorkspacePageModel currentModel;
    private FileItemModel selectedFile;
    private Path workspaceRoot;

    @FXML
    private void initialize() {
        HBox.setHgrow(actionBar, Priority.ALWAYS);
        VBox.setVgrow(fileTable, Priority.ALWAYS);

        iconColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().icon()));
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().displayName()));
        lastCommitColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().lastCommitMessage()));
        lastModifiedColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().lastModified()));

        fileTable.setRowFactory(table -> {
            TableRow<WorkspaceFileRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                WorkspaceFileRow item = row.getItem();
                if (item == null) {
                    return;
                }

                selectedFile = item.file();
                if (event.getClickCount() == 2) {
                    openEditorWindow(item.file());
                }
            });
            return row;
        });

        fileTable.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            selectedFile = newItem == null ? null : newItem.file();
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

    @FXML
    private void onCreateFolder() {
        if (!ensureWorkspaceReady()) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Folder");
        dialog.setHeaderText("Create folder in workspace");
        dialog.setContentText("Folder name:");
        dialog.initOwner(resolveOwnerStage());

        dialog.showAndWait()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .ifPresent(folderName -> {
                    try {
                        fileSystemService.createFolder(workspaceRoot, folderName);
                        workspaceService.ensureFolderMetadata(workspaceId, currentUserId, folderName);
                        reloadWorkspace();
                        showInfo("Folder created");
                    } catch (Exception e) {
                        showError("Failed to create folder: " + e.getMessage());
                    }
                });
    }

    @FXML
    private void onCreateFile() {
        if (!ensureWorkspaceReady()) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create File");
        dialog.setHeaderText("Create file in workspace root");
        dialog.setContentText("File name:");
        dialog.initOwner(resolveOwnerStage());

        dialog.showAndWait()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .ifPresent(fileName -> {
                    try {
                        fileSystemService.createFile(workspaceRoot, fileName);
                        workspaceService.ensureFileMetadata(workspaceId, currentUserId, "root", fileName);
                        reloadWorkspace();
                        showInfo("File created");
                    } catch (Exception e) {
                        showError("Failed to create file: " + e.getMessage());
                    }
                });
    }

    @FXML
    private void onImportFile() {
        if (!ensureWorkspaceReady()) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import File");
        File sourceFile = chooser.showOpenDialog(resolveOwnerStage());
        if (sourceFile == null) {
            return;
        }

        try {
            Path importedPath = fileSystemService.importFile(workspaceRoot, sourceFile.toPath());
            workspaceService.ensureFileMetadata(
                    workspaceId,
                    currentUserId,
                    "root",
                    importedPath.getFileName().toString());
            reloadWorkspace();
            showInfo("File imported into workspace");
        } catch (Exception e) {
            showError("Failed to import file: " + e.getMessage());
        }
    }

    @FXML
    private void onCommitRequested() {
        if (selectedFile == null) {
            showError("Select a file before committing");
            return;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Commit Changes");
        dialog.setHeaderText("Enter commit message");
        dialog.initModality(Modality.APPLICATION_MODAL);
        Stage owner = resolveOwnerStage();
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType confirmType = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmType, ButtonType.CANCEL);
        dialog.getDialogPane().getStyleClass().add("workspace-commit-dialog");

        TextField commitMessageField = new TextField();
        commitMessageField.setPromptText("Commit message");
        dialog.getDialogPane().setContent(commitMessageField);

        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(confirmType);
        confirmButton.disableProperty().bind(commitMessageField.textProperty().isEmpty());

        dialog.setResultConverter(buttonType -> {
            if (buttonType == confirmType) {
                return commitMessageField.getText().trim();
            }
            return null;
        });

        dialog.showAndWait()
                .filter(message -> message != null && !message.isBlank())
                .ifPresent(this::commitSelectedFile);
    }

    private void commitSelectedFile(String commitMessage) {
        try {
            Path filePath = resolveFilePath(selectedFile);
            String content = fileSystemService.readFile(filePath);
            fileService.commit(selectedFile.fileId(), currentUserId, commitMessage, content);
            reloadWorkspace();
            showInfo("Commit saved");
        } catch (Exception e) {
            showError("Commit failed: " + e.getMessage());
        }
    }

    private void reloadWorkspace() {
        currentModel = workspaceService.loadWorkspace(workspaceId);
        workspaceNameHeader.setText(currentModel.workspaceName());
        totalCommitsLabel.setText(String.valueOf(currentModel.totalCommits()));

        collaboratorsList.getItems().setAll(currentModel.collaborators().stream().map(UserModel::username).toList());

        workspaceRoot = fileSystemService.normalizeWorkspaceRoot(currentModel.workspaceRootPath());
        populateFilesTable();
        loadReadmeSection();
    }

    private void populateFilesTable() {
        List<WorkspaceFileRow> rows = new ArrayList<>();
        for (FolderModel folder : currentModel.folders()) {
            for (FileItemModel file : folder.files()) {
                Path filePath = resolveFilePath(file);
                Instant modifiedAt = fileSystemService.getLastModified(filePath);
                String modifiedLabel = modifiedAt == null
                        ? DateTimeUtils.formatInstant(file.latestCommitAt())
                        : formatRelative(modifiedAt);

                String displayName = "root".equalsIgnoreCase(folder.folderName())
                        ? file.filename()
                        : folder.folderName() + "/" + file.filename();

                rows.add(new WorkspaceFileRow(
                        "FILE",
                        displayName,
                        file.latestCommitMessage(),
                        modifiedLabel,
                        file));
            }
        }

        rows.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        fileTable.getItems().setAll(rows);
    }

    private void loadReadmeSection() {
        Path readmePath = workspaceRoot.resolve("README.md").normalize();
        String content = "";
        try {
            content = fileSystemService.readFile(readmePath);
        } catch (IOException ignored) {
            // fallback to DB-backed README content
        }

        if (content.isBlank()) {
            content = currentModel.readmeContent();
        }

        boolean hasReadme = content != null && !content.isBlank();
        readmeSection.setManaged(hasReadme);
        readmeSection.setVisible(hasReadme);
        readmeTextArea.setText(hasReadme ? content : "");
    }

    private void preselectFile(String folderName, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }

        String normalizedFolder = folderName == null ? "" : folderName.trim().toLowerCase(Locale.ROOT);
        String normalizedFile = fileName.trim().toLowerCase(Locale.ROOT);

        fileTable.getItems().stream()
                .filter(row -> row.file().filename().equalsIgnoreCase(normalizedFile)
                        && (normalizedFolder.isBlank() || row.file().folderName().equalsIgnoreCase(normalizedFolder)))
                .findFirst()
                .ifPresent(row -> fileTable.getSelectionModel().select(row));
    }

    private void openEditorWindow(FileItemModel file) {
        if (file == null) {
            return;
        }

        Path filePath = resolveFilePath(file);
        String initialContent;
        try {
            initialContent = fileSystemService.readFile(filePath);
        } catch (Exception e) {
            showError("Unable to load file: " + e.getMessage());
            return;
        }

        TextArea editor = new TextArea(initialContent);
        editor.setWrapText(false);

        Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> {
            try {
                fileSystemService.writeFile(filePath, editor.getText());
                populateFilesTable();
                showInfo("File saved to workspace");
            } catch (Exception e) {
                showError("Save failed: " + e.getMessage());
            }
        });

        BorderPane editorRoot = new BorderPane();
        editorRoot.getStyleClass().add("workspace-editor-root");
        editorRoot.setCenter(editor);

        HBox footer = new HBox(saveButton);
        footer.getStyleClass().add("workspace-editor-footer");
        editorRoot.setBottom(footer);

        Stage stage = new Stage();
        stage.setTitle("Edit " + file.filename());
        stage.initModality(Modality.APPLICATION_MODAL);
        Stage owner = resolveOwnerStage();
        if (owner != null) {
            stage.initOwner(owner);
        }

        Scene scene = new Scene(editorRoot, 900, 620);
        scene.getStylesheets()
                .add(Objects.requireNonNull(getClass().getResource("/css/dashboard.css")).toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    private Path resolveFilePath(FileItemModel file) {
        String folder = file.folderName() == null ? "" : file.folderName().trim();
        if (folder.isBlank() || "root".equalsIgnoreCase(folder)) {
            return workspaceRoot.resolve(file.filename()).normalize();
        }
        return workspaceRoot.resolve(folder).resolve(file.filename()).normalize();
    }

    private boolean ensureWorkspaceReady() {
        if (workspaceRoot != null) {
            return true;
        }
        try {
            workspaceRoot = fileSystemService
                    .normalizeWorkspaceRoot(workspaceService.resolveWorkspaceRootPath(workspaceId));
            return true;
        } catch (Exception e) {
            showError("Workspace path is unavailable: " + e.getMessage());
            return false;
        }
    }

    private Stage resolveOwnerStage() {
        if (root != null && root.getScene() != null && root.getScene().getWindow() instanceof Stage stage) {
            return stage;
        }
        return null;
    }

    private static String formatRelative(Instant instant) {
        if (instant == null) {
            return "Unknown";
        }
        Duration duration = Duration.between(instant, Instant.now());
        long minutes = Math.max(0, duration.toMinutes());
        if (minutes < 1) {
            return "Just now";
        }
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = duration.toHours();
        if (hours < 24) {
            return hours + " hours ago";
        }
        long days = duration.toDays();
        if (days < 30) {
            return days + " days ago";
        }
        return DateTimeUtils.formatInstant(instant);
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

    private record WorkspaceFileRow(
            String icon,
            String displayName,
            String lastCommitMessage,
            String lastModified,
            FileItemModel file) {
    }
}
