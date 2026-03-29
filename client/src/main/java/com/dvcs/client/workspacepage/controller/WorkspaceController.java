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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
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
import javafx.stage.Window;
import org.bson.types.ObjectId;

public final class WorkspaceController {

    @FXML
    private BorderPane root;

    @FXML
    private Label workspaceNameHeader;

    @FXML
    private HBox actionBar;

    @FXML
    private HBox mainContainer;

    @FXML
    private VBox leftSection;

    @FXML
    private VBox rightPanel;

    @FXML
    private TableView<WorkspaceFileRow> fileTable;

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

    @FXML
    private Label overviewNameLabel;

    @FXML
    private Label overviewCommitLabel;

    @FXML
    private Label overviewModifiedLabel;

    private final FileSystemService fileSystemService = new FileSystemService();

    private WorkspaceService workspaceService;
    private FileService fileService;
    private ObjectId workspaceId;
    private ObjectId currentUserId;

    private WorkspacePageModel currentModel;
    private FileItemModel selectedFile;
    private Path selectedPath;
    private Path workspaceRoot;
    private Path currentBrowsePath;
    private final Map<String, FileItemModel> metadataByRelativePath = new HashMap<>();

    @FXML
    private void initialize() {
        HBox.setHgrow(actionBar, Priority.ALWAYS);
        HBox.setHgrow(leftSection, Priority.ALWAYS);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);
        VBox.setVgrow(fileTable, Priority.ALWAYS);

        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().displayName()));
        lastCommitColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().lastCommitMessage()));
        lastModifiedColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().lastModified()));

        mainContainer.widthProperty().addListener((obs, oldWidth, newWidth) -> applySplitRatios());
        applySplitRatios();

        collaboratorsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String username, boolean empty) {
                super.updateItem(username, empty);
                if (empty || username == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                Label avatar = new Label();
                avatar.getStyleClass().add("workspace-collaborator-avatar");

                Label name = new Label(username);
                name.getStyleClass().add("workspace-collaborator-name");

                HBox row = new HBox(8, avatar, name);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("workspace-collaborator-row");

                setText(null);
                setGraphic(row);
            }
        });

        fileTable.setRowFactory(table -> {
            TableRow<WorkspaceFileRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                WorkspaceFileRow item = row.getItem();
                if (item == null) {
                    return;
                }
                openEntry(item);
            });
            return row;
        });

        fileTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldItem, newItem) -> updateOverview(newItem));

        updateOverview((FileItemModel) null);
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
    private void onBackRequested() {
        if (currentBrowsePath != null && workspaceRoot != null && !currentBrowsePath.equals(workspaceRoot)) {
            Path parent = currentBrowsePath.getParent();
            if (parent != null && parent.startsWith(workspaceRoot)) {
                currentBrowsePath = parent;
                populateCurrentDirectoryTable();
                updateOverview((WorkspaceFileRow) null);
                return;
            }
        }

        Stage current = resolveOwnerStage();
        if (current == null) {
            for (Window window : Window.getWindows()) {
                if (window.isFocused() && window instanceof Stage focusedStage) {
                    current = focusedStage;
                    break;
                }
            }
        }
        if (current == null) {
            return;
        }

        Window owner = current.getOwner();
        current.close();
        if (owner instanceof Stage ownerStage) {
            ownerStage.toFront();
            ownerStage.requestFocus();
        }
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
                        Path baseFolder = currentBrowsePath == null ? workspaceRoot : currentBrowsePath;
                        Path createdFolder = fileSystemService.createFolder(baseFolder, folderName);
                        String relativeFolder = resolveRelativePath(createdFolder);
                        workspaceService.ensureFolderMetadata(workspaceId, currentUserId, relativeFolder);
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
                        Path baseFolder = currentBrowsePath == null ? workspaceRoot : currentBrowsePath;
                        Path createdFile = fileSystemService.createFile(baseFolder, fileName);
                        Path parent = createdFile.getParent();
                        String relativeFolder = (parent == null || parent.equals(workspaceRoot))
                                ? "root"
                                : resolveRelativePath(parent);
                        workspaceService.ensureFileMetadata(workspaceId, currentUserId, relativeFolder,
                                createdFile.getFileName().toString());
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
            Path baseFolder = currentBrowsePath == null ? workspaceRoot : currentBrowsePath;
            Path importedPath = fileSystemService.importFile(baseFolder, sourceFile.toPath());
            Path parent = importedPath.getParent();
            String relativeFolder = (parent == null || parent.equals(workspaceRoot))
                    ? "root"
                    : resolveRelativePath(parent);
            workspaceService.ensureFileMetadata(
                    workspaceId,
                    currentUserId,
                    relativeFolder,
                    importedPath.getFileName().toString());
            reloadWorkspace();
            showInfo("File imported into workspace");
        } catch (Exception e) {
            showError("Failed to import file: " + e.getMessage());
        }
    }

    @FXML
    private void onCommitRequested() {
        if (selectedFile == null || selectedPath == null) {
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
            String content = fileSystemService.readFile(selectedPath);
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
        buildMetadataIndex();
        if (currentBrowsePath == null || !isInsideWorkspace(currentBrowsePath)) {
            currentBrowsePath = workspaceRoot;
        }
        populateCurrentDirectoryTable();
        loadReadmeSection();
        if (selectedPath != null && !Files.exists(selectedPath)) {
            selectedPath = null;
            selectedFile = null;
        }
        updateOverview((WorkspaceFileRow) null);
    }

    private void populateCurrentDirectoryTable() {
        List<WorkspaceFileRow> rows = new ArrayList<>();
        if (currentBrowsePath == null) {
            fileTable.getItems().clear();
            return;
        }

        try (var stream = Files.list(currentBrowsePath)) {
            List<Path> entries = stream
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted(Comparator
                            .comparing((Path path) -> Files.isDirectory(path) ? 0 : 1)
                            .thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();

            for (Path path : entries) {
                boolean isFolder = Files.isDirectory(path);
                Instant modifiedAt = fileSystemService.getLastModified(path);
                String modifiedLabel = modifiedAt == null ? "-" : formatRelative(modifiedAt);
                String relative = resolveRelativePath(path);

                FileItemModel metadata = isFolder ? null : metadataByRelativePath.get(relative);
                String commitMessage = isFolder
                        ? "-"
                        : (metadata == null || metadata.latestCommitMessage() == null
                                || metadata.latestCommitMessage().isBlank())
                                        ? "No commits yet"
                                        : metadata.latestCommitMessage();

                rows.add(new WorkspaceFileRow(
                        path.getFileName().toString(),
                        commitMessage,
                        modifiedLabel,
                        metadata,
                        path,
                        isFolder));
            }
        } catch (IOException e) {
            showError("Failed to load folder contents: " + e.getMessage());
        }
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

        if (!normalizedFolder.isBlank() && !"root".equalsIgnoreCase(normalizedFolder)) {
            currentBrowsePath = workspaceRoot.resolve(normalizedFolder).normalize();
        } else {
            currentBrowsePath = workspaceRoot;
        }
        populateCurrentDirectoryTable();

        fileTable.getItems().stream()
                .filter(row -> !row.isFolder() && row.file() != null)
                .filter(row -> row.file().filename().equalsIgnoreCase(normalizedFile)
                        && (normalizedFolder.isBlank() || row.file().folderName().equalsIgnoreCase(normalizedFolder)))
                .findFirst()
                .ifPresent(row -> fileTable.getSelectionModel().select(row));
    }

    private void openEntry(WorkspaceFileRow row) {
        if (row == null) {
            return;
        }

        if (row.isFolder()) {
            currentBrowsePath = row.path();
            selectedFile = null;
            selectedPath = null;
            populateCurrentDirectoryTable();
            updateOverview(row);
            return;
        }

        selectedPath = row.path();
        selectedFile = row.file();
        updateOverview(row);
        openEditorWindow(row.path(), row.displayName());
    }

    private void openEditorWindow(Path filePath, String displayName) {
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
                populateCurrentDirectoryTable();
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
        stage.setTitle("Edit " + displayName);
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
        populateCurrentDirectoryTable();
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

    private void applySplitRatios() {
        double width = mainContainer.getWidth();
        if (width <= 0) {
            return;
        }

        double spacing = mainContainer.getSpacing();
        double available = Math.max(0, width - spacing);
        leftSection.setPrefWidth(available * 0.70);
        rightPanel.setPrefWidth(available * 0.30);
    }

    private void updateOverview(FileItemModel file) {
        if (file == null) {
            overviewNameLabel.setText("No file selected");
            overviewCommitLabel.setText("Commit: -");
            overviewModifiedLabel.setText("Modified: -");
            return;
        }

        overviewNameLabel.setText(file.filename());
        String message = file.latestCommitMessage() == null || file.latestCommitMessage().isBlank()
                ? "No commits yet"
                : file.latestCommitMessage();
        overviewCommitLabel.setText("Commit: " + message);

        Instant modifiedAt = fileSystemService.getLastModified(resolveFilePath(file));
        String modified = modifiedAt == null ? DateTimeUtils.formatInstant(file.latestCommitAt())
                : formatRelative(modifiedAt);
        overviewModifiedLabel.setText("Modified: " + modified);
    }

    private void updateOverview(WorkspaceFileRow row) {
        if (row == null) {
            updateOverview((FileItemModel) null);
            return;
        }
        if (row.isFolder()) {
            overviewNameLabel.setText(row.displayName());
            overviewCommitLabel.setText("Type: Folder");
            overviewModifiedLabel.setText("Modified: " + row.lastModified());
            return;
        }
        updateOverview(row.file());
    }

    private void buildMetadataIndex() {
        metadataByRelativePath.clear();
        for (FolderModel folder : currentModel.folders()) {
            String folderName = folder.folderName() == null ? "" : folder.folderName().trim();
            for (FileItemModel file : folder.files()) {
                String relative = folderName.isBlank() || "root".equalsIgnoreCase(folderName)
                        ? file.filename()
                        : folderName + "/" + file.filename();
                metadataByRelativePath.put(relative.toLowerCase(Locale.ROOT), file);
            }
        }
    }

    private String resolveRelativePath(Path path) {
        if (workspaceRoot == null || path == null) {
            return "";
        }
        String relative = workspaceRoot.relativize(path).toString().replace('\\', '/');
        return relative.toLowerCase(Locale.ROOT);
    }

    private boolean isInsideWorkspace(Path path) {
        return workspaceRoot != null && path != null && path.normalize().startsWith(workspaceRoot);
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
        Stage owner = resolveOwnerStage();
        if (owner != null) {
            alert.initOwner(owner);
            alert.initModality(Modality.WINDOW_MODAL);
        }
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Workspace");
        Stage owner = resolveOwnerStage();
        if (owner != null) {
            alert.initOwner(owner);
            alert.initModality(Modality.WINDOW_MODAL);
        }
        alert.showAndWait();
    }

    private record WorkspaceFileRow(
            String displayName,
            String lastCommitMessage,
            String lastModified,
            FileItemModel file,
            Path path,
            boolean isFolder) {
    }
}
