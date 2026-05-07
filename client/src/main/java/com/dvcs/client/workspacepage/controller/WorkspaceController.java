package com.dvcs.client.workspacepage.controller;

import com.dvcs.client.auth.db.MongoConnection;
import com.dvcs.client.workspacepage.model.FileItemModel;
import com.dvcs.client.workspacepage.model.FolderModel;
import com.dvcs.client.workspacepage.model.UserModel;
import com.dvcs.client.workspacepage.model.WorkspacePageModel;
import com.dvcs.client.core.model.Branch;
import com.dvcs.client.core.model.Tag;
import com.dvcs.client.ui.PopupDialogs;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;
import com.dvcs.client.workspacepage.dao.FileDAO;
import com.dvcs.client.workspacepage.model.FileCommentModel;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import com.dvcs.client.workspacepage.model.WorkspaceCommentModel;

public final class WorkspaceController {

    @FXML
    private BorderPane root;

    @FXML
    private Label workspaceNameHeader;

    @FXML
    private HBox mainContainer;

    @FXML
    private VBox leftSection;

    @FXML
    private HBox breadcrumbBar;

    @FXML
    private VBox rightPanel;

    @FXML
    private Label languageProjectTitleLabel;

    @FXML
    private Label contributorsCountLabel;

    @FXML
    private VBox contributorsListBox;

    @FXML
    private HBox languageBarContainer;

    @FXML
    private VBox languageLegendBox;

    @FXML
    private TableView<WorkspaceFileRow> fileTable;

    @FXML
    private TableColumn<WorkspaceFileRow, String> nameColumn;

    @FXML
    private TableColumn<WorkspaceFileRow, String> lastCommitColumn;

    @FXML
    private TableColumn<WorkspaceFileRow, String> lastModifiedColumn;

    @FXML
    private TableColumn<WorkspaceFileRow, String> actionsColumn;

    @FXML
    private Label currentPathLabel;

    @FXML
    private VBox readmeSection;

    @FXML
    private TextArea readmeWebView;

    @FXML
    private javafx.scene.control.Button editReadmeButton;

    @FXML
    private ComboBox<String> branchSelector;

    @FXML
    private HBox tagsContainer;


    private final FileSystemService fileSystemService = new FileSystemService();

    private WorkspaceService workspaceService;
    private FileService fileService;
    private ObjectId workspaceId;
    private ObjectId currentUserId;
    private boolean isOwner = false;
    private boolean canWrite = true;

    private WorkspacePageModel currentModel;
    private FileItemModel selectedFile;
    private Path selectedPath;
    private Path workspaceRoot;
    private Path currentBrowsePath;
    private final Map<String, FileItemModel> metadataByRelativePath = new HashMap<>();
    private final Map<ObjectId, String> usernameById = new HashMap<>();
    private boolean userDirectoryLoaded;

    // Branch state
    private ObjectId currentBranchId;
    private String currentBranchName = "main";
    private boolean currentBranchIsDefault = true;

    @FXML
    private void initialize() {
        HBox.setHgrow(leftSection, Priority.ALWAYS);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);
        VBox.setVgrow(fileTable, Priority.ALWAYS);

        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().displayName()));
        lastCommitColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().lastCommitMessage()));
        lastModifiedColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().lastModified()));

        // Actions column with simple three-dot button.
        actionsColumn.setCellFactory(column -> new TableCell<WorkspaceFileRow, String>() {
            private final Button actionsBtn = new Button("...");
            {
                actionsBtn.setStyle("-fx-font-size: 16; -fx-font-weight: 700; -fx-padding: 2 10; "
                        + "-fx-background-color: transparent; -fx-text-fill: #e8fff2;");
                actionsBtn.setOnAction(event -> {
                    WorkspaceFileRow row = getTableRow().getItem();
                    if (row != null) {
                        onFileActionsRequested(row);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                WorkspaceFileRow row = getTableRow().getItem();
                if (empty || row == null || row.isFolder()) {
                    setGraphic(null);
                } else {
                    setGraphic(actionsBtn);
                }
            }
        });

        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        mainContainer.widthProperty().addListener((obs, oldWidth, newWidth) -> applySplitRatios());
        applySplitRatios();

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
                .addListener((obs, oldItem, newItem) -> {
                    selectedPath = newItem == null ? null : newItem.path();
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

        // Determine ownership and permission
        try {
            org.bson.Document ws = workspaceService.findWorkspaceDocById(workspaceId).orElse(null);
            if (ws != null) {
                org.bson.types.ObjectId createdBy = ws.getObjectId("createdBy");
                this.isOwner = currentUserId.equals(createdBy);
            }
        } catch (Exception ignored) {}

        if (!isOwner) {
            try {
                String perm = workspaceService.getCollaboratorPermission(workspaceId, currentUserId);
                this.canWrite = !"read".equalsIgnoreCase(perm);
            } catch (Exception ignored) {}
        }

        // Ensure "main" branch exists and set as current
        try {
            com.dvcs.client.core.model.Branch defaultBranch = workspaceService.ensureDefaultBranch(workspaceId, currentUserId);
            currentBranchId = defaultBranch.id();
            currentBranchName = defaultBranch.branchName();
            currentBranchIsDefault = defaultBranch.isDefault();
        } catch (Exception ignored) {}

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

        Stage owner = resolveOwnerStage();
        String folderName = PopupDialogs.showTextInput(
                owner,
                "Create Folder",
                "Create a folder in the current workspace path.",
                "Folder name",
                "e.g. src, docs, assets/images",
                "Create Folder")
                .orElse(null);
        if (folderName == null || folderName.isBlank()) {
            return;
        }

        // Validate folder name
        String validationError = validateFileName(folderName.trim());
        if (validationError != null) {
            showGlassMorphicError("Invalid Folder Name", validationError);
            return;
        }

        try {
            String relativeFolder = folderName.trim();
            try {
                Path baseFolder = currentBrowsePath == null ? workspaceRoot : currentBrowsePath;
                Path createdFolder = fileSystemService.createFolder(baseFolder, folderName.trim());
                relativeFolder = resolveRelativePath(createdFolder);
            } catch (Exception ignored) {
                // Collaborator: filesystem inaccessible, derive from input
            }
            workspaceService.ensureFolderMetadata(workspaceId, currentUserId, relativeFolder);
            reloadWorkspace();
            showGlassMorphicSuccess("Folder Created", "Folder created successfully");
        } catch (Exception e) {
            showGlassMorphicError("Folder Creation Failed", "Failed to create folder: " + e.getMessage());
        }
    }

    @FXML
    private void onRefreshWorkspace() {
        reloadWorkspace();
    }

    @FXML
    private void onCreateFile() {
        if (!canWrite) { showGlassMorphicError("Read-Only", "You have read-only access to this workspace."); return; }
        if (!ensureWorkspaceReady()) {
            return;
        }

        Stage owner = resolveOwnerStage();
        String fileName = PopupDialogs.showTextInput(
                owner,
                "Create File",
                "Nested paths are supported in this workspace.",
                "File path",
                "e.g. src/main/App.java",
                "Create File")
                .orElse(null);
        if (fileName == null || fileName.isBlank()) {
            return;
        }

        // Validate file name
        String validationError = validateFilePath(fileName.trim());
        if (validationError != null) {
            showGlassMorphicError("Invalid File Name", validationError);
            return;
        }

        try {
            Path baseFolder = currentBrowsePath == null ? workspaceRoot : currentBrowsePath;
            String folderRelative = "root";
            String filenamePart = Path.of(fileName.trim()).getFileName().toString();

            // Try filesystem creation; skip silently if path is inaccessible (collaborator)
            try {
                Path createdFile = fileSystemService.createFile(baseFolder, fileName.trim());
                Path parent = createdFile.getParent();
                folderRelative = (parent == null || parent.equals(workspaceRoot))
                        ? "root" : resolveRelativePath(parent);
                filenamePart = createdFile.getFileName().toString();
            } catch (Exception ignored) {
                // Derive folder from user input when filesystem is unavailable
                Path p = Path.of(fileName.trim());
                folderRelative = p.getParent() == null ? "root" : p.getParent().toString().replace('\\', '/');
            }

            workspaceService.ensureFileMetadata(workspaceId, currentUserId, folderRelative,
                    filenamePart, currentBranchId);
            reloadWorkspace();
            showGlassMorphicSuccess("File Created", "File created in branch: " + currentBranchName);
        } catch (Exception e) {
            showGlassMorphicError("File Creation Failed", "Failed to create file: " + e.getMessage());
        }
    }

    @FXML
    private void onImportFile() {
        if (!canWrite) { showGlassMorphicError("Read-Only", "You have read-only access to this workspace."); return; }
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
            Path sourcePath = sourceFile.toPath();
            String filename = sourcePath.getFileName().toString();
            String folderRelative = "root";

            // Try filesystem copy; skip silently if inaccessible (collaborator)
            try {
                Path baseFolder = currentBrowsePath == null ? workspaceRoot : currentBrowsePath;
                Path importedPath = fileSystemService.importFile(baseFolder, sourcePath);
                Path parent = importedPath.getParent();
                folderRelative = (parent == null || parent.equals(workspaceRoot))
                        ? "root" : resolveRelativePath(parent);
                filename = importedPath.getFileName().toString();
            } catch (Exception ignored) {}

            workspaceService.ensureFileMetadata(workspaceId, currentUserId, folderRelative, filename, currentBranchId);

            // Auto-commit the file content so it's immediately accessible from DB
            String content = "";
            try { content = java.nio.file.Files.readString(sourcePath); } catch (Exception ignored) {}
            if (!content.isBlank()) {
                reloadWorkspace();
                com.dvcs.client.workspacepage.model.FileItemModel imported =
                        workspaceService.findFileByName(workspaceId, folderRelative, filename).orElse(null);
                if (imported != null) {
                    fileService.commit(imported.fileId(), currentUserId, "Import " + filename, content, workspaceId, currentBranchId);
                }
            }

            reloadWorkspace();
            showGlassMorphicSuccess("File Imported", "File imported successfully");
        } catch (Exception e) {
            showGlassMorphicError("Import Failed", "Failed to import file: " + e.getMessage());
        }
    }

    private void onFileActionsRequested(WorkspaceFileRow row) {
        if (row.isFolder()) {
            return;
        }

        Stage owner = resolveOwnerStage();
        Stage dialog = new Stage();
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        Label titleLabel = new Label("File Options");
        titleLabel.getStyleClass().add("neon-popup-title");

        Label subtitleLabel = new Label(row.displayName());
        subtitleLabel.getStyleClass().add("neon-popup-subtitle");

        Button renameButton = new Button("Rename File");
        renameButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-secondary");
        renameButton.setVisible(isOwner);
        renameButton.setManaged(isOwner);

        Button deleteButton = new Button("Delete File");
        deleteButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-secondary");
        deleteButton.setVisible(isOwner);
        deleteButton.setManaged(isOwner);

        HBox actionButtons = new HBox(10, renameButton, deleteButton);
        actionButtons.setAlignment(Pos.CENTER);

        Label renameLabel = new Label("New File Name");
        renameLabel.getStyleClass().add("neon-popup-field-label");
        renameLabel.setVisible(false);
        renameLabel.setManaged(false);

        TextField renameField = new TextField(row.displayName());
        renameField.getStyleClass().add("neon-popup-input");
        renameField.setPromptText("Enter new file name");
        renameField.setVisible(false);
        renameField.setManaged(false);

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-primary");
        saveButton.setVisible(false);
        saveButton.setManaged(false);

        HBox saveRow = new HBox(saveButton);
        saveRow.setAlignment(Pos.CENTER);
        saveRow.setVisible(false);
        saveRow.setManaged(false);

        Label deleteConfirmLabel = new Label("Are you sure you want to delete this file?");
        deleteConfirmLabel.getStyleClass().add("neon-popup-subtitle");
        deleteConfirmLabel.setVisible(false);
        deleteConfirmLabel.setManaged(false);

        Button yesDeleteButton = new Button("Yes");
        yesDeleteButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-primary");

        Button noDeleteButton = new Button("No");
        noDeleteButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-secondary");

        HBox deleteConfirmRow = new HBox(10, yesDeleteButton, noDeleteButton);
        deleteConfirmRow.setAlignment(Pos.CENTER);
        deleteConfirmRow.setVisible(false);
        deleteConfirmRow.setManaged(false);

        VBox content = new VBox(10, actionButtons, renameLabel, renameField, saveRow, deleteConfirmLabel,
                deleteConfirmRow);
        content.getStyleClass().add("neon-popup-content");

        renameButton.setOnAction(event -> {
            renameLabel.setVisible(true);
            renameLabel.setManaged(true);
            renameField.setVisible(true);
            renameField.setManaged(true);
            saveButton.setVisible(true);
            saveButton.setManaged(true);
            saveRow.setVisible(true);
            saveRow.setManaged(true);

            deleteConfirmLabel.setVisible(false);
            deleteConfirmLabel.setManaged(false);
            deleteConfirmRow.setVisible(false);
            deleteConfirmRow.setManaged(false);

            dialog.sizeToScene();
            Platform.runLater(renameField::requestFocus);
        });

        saveButton.setOnAction(event -> {
            String newName = renameField.getText() == null ? "" : renameField.getText().trim();
            if (newName.isEmpty()) {
                showGlassMorphicError("Invalid File Name", "File name cannot be empty");
                return;
            }
            
            // Validate file name
            String validationError = validateFileName(newName);
            if (validationError != null) {
                showGlassMorphicError("Invalid File Name", validationError);
                return;
            }
            
            try {
                // Filesystem rename: best-effort (collaborators may not have access)
                try {
                    Path target = row.path().resolveSibling(newName);
                    if (Files.exists(target)) {
                        showGlassMorphicError("File Already Exists", "A file with this name already exists");
                        return;
                    }
                    Files.move(row.path(), target);
                    if (selectedPath != null && selectedPath.equals(row.path())) {
                        selectedPath = target;
                    }
                } catch (Exception ignored) {}

                if (row.file() != null) {
                    workspaceService.renameFileMetadata(row.file(), newName, currentUserId);
                }
                dialog.close();
                reloadWorkspace();
                showGlassMorphicSuccess("File Renamed", "File renamed successfully");
            } catch (Exception e) {
                showGlassMorphicError("Rename Failed", "Failed to rename file: " + e.getMessage());
            }
        });

        deleteButton.setOnAction(event -> {
            renameLabel.setVisible(false);
            renameLabel.setManaged(false);
            renameField.setVisible(false);
            renameField.setManaged(false);
            saveButton.setVisible(false);
            saveButton.setManaged(false);
            saveRow.setVisible(false);
            saveRow.setManaged(false);

            deleteConfirmLabel.setVisible(true);
            deleteConfirmLabel.setManaged(true);
            deleteConfirmRow.setVisible(true);
            deleteConfirmRow.setManaged(true);
            dialog.sizeToScene();
        });

        yesDeleteButton.setOnAction(event -> {
            Path pathToDelete = row.path();
            FileItemModel fileToDelete = row.file();
            boolean wasSelected = selectedPath != null && selectedPath.equals(pathToDelete);

            dialog.close();

            Thread deleteWorker = new Thread(() -> {
                String errorMessage = null;
                try {
                    Files.deleteIfExists(pathToDelete);
                    if (fileToDelete != null) {
                        workspaceService.deleteFileMetadata(fileToDelete, currentUserId);
                    }
                } catch (Exception ex) {
                    errorMessage = ex.getMessage();
                }

                String finalErrorMessage = errorMessage;
                Platform.runLater(() -> {
                    if (finalErrorMessage != null && !finalErrorMessage.isBlank()) {
                        showGlassMorphicError("Delete Failed", "Failed to delete file: " + finalErrorMessage);
                        return;
                    }
                    try {
                        if (wasSelected) {
                            selectedPath = null;
                            selectedFile = null;
                        }
                        reloadWorkspace();
                        showGlassMorphicSuccess("File Deleted", "File deleted successfully");
                    } catch (Exception refreshError) {
                        showGlassMorphicError("Refresh Failed", "File deleted, but refresh failed: " + refreshError.getMessage());
                    }
                });
            }, "workspace-file-delete-worker");
            deleteWorker.setDaemon(true);
            deleteWorker.start();
        });

        noDeleteButton.setOnAction(event -> {
            deleteConfirmLabel.setVisible(false);
            deleteConfirmLabel.setManaged(false);
            deleteConfirmRow.setVisible(false);
            deleteConfirmRow.setManaged(false);
            dialog.sizeToScene();
        });

        Button closeButton = new Button("✕");
        closeButton.getStyleClass().add("neon-popup-close-button");
        closeButton.setStyle("-fx-font-size: 14; -fx-font-weight: 700; -fx-text-fill: #e8fff2;");
        closeButton.setOnAction(event -> dialog.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(spacer, closeButton);
        topRow.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(10, topRow, titleLabel, subtitleLabel, content);
        card.getStyleClass().addAll("neon-popup-card");
        card.setMaxWidth(560);
        card.setMinHeight(340);
        card.setPadding(new Insets(20));

        StackPane rootPane = new StackPane(card);
        rootPane.getStyleClass().add("neon-popup-overlay");

        Scene scene = new Scene(rootPane);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets()
                .add(Objects.requireNonNull(getClass().getResource("/css/homepage.css")).toExternalForm());

        dialog.setScene(scene);
        dialog.showAndWait();
    }

    @FXML
    private void onSettingsRequested() {
        Stage owner = resolveOwnerStage();
        Stage settingsStage = new Stage();
        settingsStage.setTitle("Workspace Settings");
        settingsStage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            settingsStage.initOwner(owner);
        }

        // Main background panel
        BorderPane settingsRoot = new BorderPane();
        settingsRoot.setStyle("-fx-background-color: #050505;");

        // Horizontal Navigation Menu
        HBox navBar = new HBox();
        navBar.setStyle(
                "-fx-padding: 0; -fx-background-color: #080808; -fx-border-color: rgba(0,255,136,0.28); -fx-border-width: 0 0 2 0;");
        navBar.setSpacing(0);

        // Content panel - StackPane to hold multiple views
        StackPane contentPane = new StackPane();
        contentPane.setStyle("-fx-padding: 16;");

        // Create content panels for each section
        VBox commitHistoryContent = createCommitHistoryPanel();
        VBox fileComparisonContent = createFileComparisonPanel();
        VBox collaboratorsContent = createCollaboratorsPanel();
        VBox workspaceSettingsContent = createWorkspaceSettingsPanel();

        // Hide all content initially
        commitHistoryContent.setVisible(true);
        fileComparisonContent.setVisible(false);
        collaboratorsContent.setVisible(false);
        workspaceSettingsContent.setVisible(false);

        contentPane.getChildren().addAll(commitHistoryContent, fileComparisonContent, collaboratorsContent,
                workspaceSettingsContent);

        // Menu buttons — collaborators cannot invite
        String[] menuItems = isOwner
                ? new String[]{ "Commit History", "File Comparison", "Invite Collaborators", "Settings" }
                : new String[]{ "Commit History", "File Comparison" };
        VBox[] contentPanels = isOwner
                ? new VBox[]{ commitHistoryContent, fileComparisonContent, collaboratorsContent, workspaceSettingsContent }
                : new VBox[]{ commitHistoryContent, fileComparisonContent };
        Button[] menuButtons = new Button[menuItems.length];

        for (int i = 0; i < menuItems.length; i++) {
            final int index = i;
            Button btn = new Button(menuItems[i]);
            menuButtons[i] = btn;
            btn.setStyle("-fx-padding: 12 20; -fx-font-size: 12; -fx-text-fill: #9ab7a8; "
                    + "-fx-background-color: transparent; -fx-border-width: 0 0 3 0; -fx-border-color: transparent;");

            btn.setOnAction(e -> {
                // Hide all content panels
                for (VBox panel : contentPanels) {
                    panel.setVisible(false);
                }
                // Show selected panel
                contentPanels[index].setVisible(true);

                // Update button styles
                for (Button button : menuButtons) {
                    button.setStyle("-fx-padding: 12 20; -fx-font-size: 12; -fx-text-fill: #9ab7a8; "
                            + "-fx-background-color: transparent; -fx-border-width: 0 0 3 0; -fx-border-color: transparent;");
                }
                // Highlight active button
                btn.setStyle("-fx-padding: 12 20; -fx-font-size: 12; -fx-text-fill: #00ff88; "
                        + "-fx-background-color: rgba(0,255,136,0.1); -fx-border-width: 0 0 3 0; -fx-border-color: #00ff88;");
            });

            navBar.getChildren().add(btn);

            // Set first button as active
            if (i == 0) {
                btn.setStyle("-fx-padding: 12 20; -fx-font-size: 12; -fx-text-fill: #00ff88; "
                        + "-fx-background-color: rgba(0,255,136,0.1); -fx-border-width: 0 0 3 0; -fx-border-color: #00ff88;");
            }
        }

        settingsRoot.setTop(navBar);
        settingsRoot.setCenter(contentPane);

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double width = Math.max(1000, bounds.getWidth() * 0.8);
        double height = Math.max(700, bounds.getHeight() * 0.8);

        Scene scene = new Scene(settingsRoot, width, height);
        scene.getStylesheets()
                .add(Objects.requireNonNull(getClass().getResource("/css/homepage.css")).toExternalForm());
        settingsStage.setScene(scene);

        settingsStage.setOnShown(event -> {
            if (owner != null) {
                settingsStage.setX(owner.getX() + ((owner.getWidth() - settingsStage.getWidth()) / 2.0));
                settingsStage.setY(owner.getY() + ((owner.getHeight() - settingsStage.getHeight()) / 2.0));
            }
        });

        settingsStage.showAndWait();
    }

    @FXML
    private void onCommentsRequested() {
        if (workspaceService == null || workspaceId == null || currentModel == null) {
            showGlassMorphicError("Comments Unavailable", "Workspace comments are not ready yet.");
            return;
        }

        ensureUserDirectoryLoaded();

        Stage owner = resolveOwnerStage();
        Stage dialog = new Stage();
        dialog.setTitle("Workspace Comments");
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        Label titleLabel = new Label("Comments");
        titleLabel.getStyleClass().add("workspace-comment-title");

        Label subtitleLabel = new Label("Workspace discussion and file notes for " + currentModel.workspaceName());
        subtitleLabel.getStyleClass().add("workspace-comment-subtitle");
        subtitleLabel.setWrapText(true);

        ToggleButton workspaceTab = new ToggleButton("Workspace");
        ToggleButton fileTab = new ToggleButton("File");
        workspaceTab.getStyleClass().add("workspace-comment-tab");
        fileTab.getStyleClass().add("workspace-comment-tab");
        ToggleGroup toggleGroup = new ToggleGroup();
        workspaceTab.setToggleGroup(toggleGroup);
        fileTab.setToggleGroup(toggleGroup);
        workspaceTab.setSelected(true);

        HBox tabs = new HBox(8, workspaceTab, fileTab);
        tabs.getStyleClass().add("workspace-comment-tabs");

        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("workspace-comment-meta");
        statusLabel.setWrapText(true);

        VBox feedContent = new VBox(14);
        ScrollPane scrollPane = new ScrollPane(feedContent);
        scrollPane.getStyleClass().add("workspace-comment-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Map<ObjectId, String> fileDisplayNames = buildFileDisplayNames();

        Runnable renderWorkspaceView = () -> {
            renderWorkspaceComments(feedContent);
        };

        Runnable renderFileView = () -> {
            renderFileComments(feedContent, fileDisplayNames);
        };

        toggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == fileTab) {
                renderFileView.run();
            } else {
                renderWorkspaceView.run();
            }
        });

        Button leaveCommentButton = new Button("Leave a Comment");
        leaveCommentButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-primary");
        leaveCommentButton.setOnAction(event -> {
            boolean posted = showCommentComposerDialog(dialog, fileDisplayNames, fileTab.isSelected());
            if (posted) {
                if (fileTab.isSelected()) {
                    renderFileView.run();
                } else {
                    renderWorkspaceView.run();
                }
                statusLabel.setText("Comment saved to MongoDB.");
            }
        });

        renderWorkspaceView.run();

        Button closeButton = new Button("Close");
        closeButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-secondary");
        closeButton.setOnAction(event -> dialog.close());

        HBox footerActions = new HBox(10, leaveCommentButton, closeButton);
        footerActions.setAlignment(Pos.CENTER_RIGHT);

        VBox shell = new VBox(14, titleLabel, subtitleLabel, tabs, scrollPane, statusLabel, footerActions);
        shell.getStyleClass().addAll("workspace-comment-window", "workspace-comment-shell");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Scene scene = new Scene(shell);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/workspace.css")).toExternalForm());
        dialog.setScene(scene);

        if (owner != null) {
            double width = Math.max(660, owner.getWidth() * 0.48);
            double height = Math.max(540, owner.getHeight() * 0.62);
            dialog.setWidth(width);
            dialog.setHeight(height);
            dialog.setX(owner.getX() + ((owner.getWidth() - width) / 2.0));
            dialog.setY(owner.getY() + ((owner.getHeight() - height) / 2.0));
        } else {
            dialog.setWidth(720);
            dialog.setHeight(580);
        }

        dialog.showAndWait();
    }

    private boolean showCommentComposerDialog(Window owner, Map<ObjectId, String> fileDisplayNames, boolean defaultFileMode) {
        Stage dialog = new Stage();
        dialog.setTitle("Leave a Comment");
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        Label titleLabel = new Label("Leave a Comment");
        titleLabel.getStyleClass().add("workspace-comment-title");

        Label subtitleLabel = new Label("Anyone viewing this workspace can post a workspace note or a file note.");
        subtitleLabel.getStyleClass().add("workspace-comment-subtitle");
        subtitleLabel.setWrapText(true);

        ToggleButton workspaceTab = new ToggleButton("Workspace");
        ToggleButton fileTab = new ToggleButton("File");
        workspaceTab.getStyleClass().add("workspace-comment-tab");
        fileTab.getStyleClass().add("workspace-comment-tab");
        ToggleGroup toggleGroup = new ToggleGroup();
        workspaceTab.setToggleGroup(toggleGroup);
        fileTab.setToggleGroup(toggleGroup);
        if (defaultFileMode) {
            fileTab.setSelected(true);
        } else {
            workspaceTab.setSelected(true);
        }

        HBox tabs = new HBox(8, workspaceTab, fileTab);
        tabs.getStyleClass().add("workspace-comment-tabs");

        Label composerMeta = new Label();
        composerMeta.getStyleClass().add("workspace-comment-meta");
        composerMeta.setWrapText(true);

        ComboBox<FileCommentOption> filePicker = new ComboBox<>();
        filePicker.getStyleClass().add("workspace-comment-file-picker");
        filePicker.setPromptText("Choose a file");
        filePicker.setMaxWidth(Double.MAX_VALUE);
        ObservableList<FileCommentOption> fileOptions = FXCollections.observableArrayList(buildFileCommentOptions(fileDisplayNames));
        filePicker.setItems(fileOptions);
        if (!fileOptions.isEmpty()) {
            filePicker.getSelectionModel().selectFirst();
        }

        TextArea commentInput = new TextArea();
        commentInput.getStyleClass().add("workspace-comment-input");
        commentInput.setWrapText(true);
        commentInput.setPrefRowCount(5);

        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("workspace-comment-meta");
        statusLabel.setWrapText(true);

        Runnable updateMode = () -> {
            boolean fileMode = fileTab.isSelected();
            filePicker.setVisible(fileMode);
            filePicker.setManaged(fileMode);
            if (fileMode) {
                composerMeta.setText("Select the file you want to comment on, then post your note.");
                commentInput.setPromptText("Write a comment about the selected file...");
            } else {
                composerMeta.setText("Post a comment for the whole workspace.");
                commentInput.setPromptText("Share a workspace update...");
            }
        };
        toggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> updateMode.run());
        updateMode.run();

        Button postButton = new Button("Post Comment");
        postButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-primary");

        AtomicReference<Boolean> posted = new AtomicReference<>(false);
        postButton.setOnAction(event -> {
            String message = commentInput.getText() == null ? "" : commentInput.getText().trim();
            if (message.isEmpty()) {
                statusLabel.setText("Write a comment before posting.");
                return;
            }
            try {
                if (fileTab.isSelected()) {
                    FileCommentOption selected = filePicker.getValue();
                    if (selected == null || selected.fileId() == null) {
                        statusLabel.setText("Choose a file first.");
                        return;
                    }
                    workspaceService.addFileComment(workspaceId, selected.fileId(), currentUserId, message);
                } else {
                    workspaceService.addWorkspaceComment(workspaceId, currentUserId, message);
                }
                posted.set(true);
                dialog.close();
            } catch (Exception ex) {
                statusLabel.setText("Could not save comment: " + ex.getMessage());
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-secondary");
        cancelButton.setOnAction(event -> dialog.close());

        HBox footerActions = new HBox(10, postButton, cancelButton);
        footerActions.setAlignment(Pos.CENTER_RIGHT);

        VBox shell = new VBox(14, titleLabel, subtitleLabel, tabs, composerMeta, filePicker, commentInput, statusLabel, footerActions);
        shell.getStyleClass().addAll("workspace-comment-window", "workspace-comment-shell", "workspace-comment-composer");

        Scene scene = new Scene(shell, 560, 430);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/workspace.css")).toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
        return Boolean.TRUE.equals(posted.get());
    }

    private VBox createCommitHistoryPanel() {
        VBox content = new VBox(12);
        content.setStyle("-fx-padding: 16;");
        VBox.setVgrow(content, Priority.ALWAYS);

        // Header card
        VBox headerCard = createGlassMorphicCard(
                new Label("Commit History"),
                new Label("Track all changes made to your workspace files"));

        // Controls section
        HBox controlsBox = new HBox(12);
        controlsBox.setStyle("-fx-padding: 12; -fx-background-color: #0b0b0b; "
                + "-fx-border-color: rgba(0,255,136,0.25); -fx-border-radius: 8; -fx-background-radius: 8;");
        controlsBox.setAlignment(Pos.CENTER_LEFT);

        Label refreshLabel = new Label("Refresh:");
        refreshLabel.setStyle("-fx-text-fill: #9ab7a8;");
        Button refreshBtn = new Button("Load Commits");
        refreshBtn.setStyle("-fx-padding: 6 16; -fx-font-size: 11; -fx-text-fill: #032312; "
                + "-fx-background-color: #00df7a;");
        Label summaryLabel = new Label("Loading...");
        summaryLabel.setStyle("-fx-text-fill: #9ab7a8;");
        Region controlsSpacer = new Region();
        HBox.setHgrow(controlsSpacer, Priority.ALWAYS);

        controlsBox.getChildren().addAll(refreshLabel, refreshBtn, controlsSpacer, summaryLabel);

        // Table for commits
        TableView<WorkspaceCommitRow> commitTable = new TableView<>();
        commitTable
                .setStyle("-fx-background-color: #0b0b0b; -fx-control-inner-background: #0b0b0b;"
                        + "-fx-table-cell-border-color: rgba(0,255,136,0.12); -fx-text-background-color: #e8fff2;");
        VBox.setVgrow(commitTable, Priority.ALWAYS);
        Label placeholderLabel = new Label("No commits found for this workspace yet.");
        placeholderLabel.setStyle("-fx-text-fill: #9ab7a8;");
        commitTable.setPlaceholder(placeholderLabel);

        TableColumn<WorkspaceCommitRow, String> fileCol = new TableColumn<>("File");
        fileCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fileName()));
        fileCol.setPrefWidth(150);

        TableColumn<WorkspaceCommitRow, String> msgCol = new TableColumn<>("Message");
        msgCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().message()));
        msgCol.setPrefWidth(250);

        TableColumn<WorkspaceCommitRow, String> dateCol = new TableColumn<>("Timestamp");
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().committedAt()));
        dateCol.setPrefWidth(150);

        TableColumn<WorkspaceCommitRow, String> byCol = new TableColumn<>("Committed By");
        byCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().committedByUser()));
        byCol.setPrefWidth(130);

        TableColumn<WorkspaceCommitRow, String> versionCol = new TableColumn<>("Version");
        versionCol.setCellValueFactory(data -> new SimpleStringProperty("#" + data.getValue().snapshotId()));
        versionCol.setPrefWidth(80);

        TableColumn<WorkspaceCommitRow, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(170);
        actionCol.setCellFactory(col -> new TableCell<WorkspaceCommitRow, Void>() {
            private final Button viewBtn = new Button("View");
            private final Button restoreBtn = new Button("Restore");
            private final HBox box = new HBox(6, viewBtn, restoreBtn);

            {
                viewBtn.setStyle("-fx-font-size: 11; -fx-padding: 4 10;");
                restoreBtn.setStyle("-fx-font-size: 11; -fx-padding: 4 10;");

                viewBtn.setOnAction(event -> {
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                        return;
                    }
                    WorkspaceCommitRow row = getTableView().getItems().get(getIndex());
                    final Window modalOwner = getTableView() != null
                            && getTableView().getScene() != null
                                    ? getTableView().getScene().getWindow()
                                    : resolveOwnerStage();

                    viewBtn.setDisable(true);
                    runBackground("workspace-snapshot-view-worker", () -> {
                        try {
                            String snapshotContent = fileService.loadSnapshotContent(row.fileId(), row.snapshotId());
                            Platform.runLater(() -> {
                                viewBtn.setDisable(false);
                                showSnapshotViewer(modalOwner, row.fileName(), row.snapshotId(), snapshotContent);
                            });
                        } catch (Exception ex) {
                            Platform.runLater(() -> {
                                viewBtn.setDisable(false);
                                showGlassMorphicError("Load Failed", "Failed to load snapshot: " + ex.getMessage());
                            });
                        }
                    });
                });

                restoreBtn.setOnAction(event -> {
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                        return;
                    }
                    WorkspaceCommitRow row = getTableView().getItems().get(getIndex());
                    
                    // Capture data needed for background thread
                    final ObjectId fileId = row.fileId();
                    final int snapshotId = row.snapshotId();
                    final String fileName = row.fileName();
                    
                    // Resolve relative path on FX thread
                    final String relPath = metadataByRelativePath.entrySet().stream()
                            .filter(e -> e.getValue().fileId().equals(fileId))
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElse(null);
                    
                    final Path wsRoot = workspaceRoot;
                    
                    // Disable UI immediately
                    restoreBtn.setDisable(true);
                    viewBtn.setDisable(true);
                    commitTable.setDisable(true);
                    summaryLabel.setText("Restoring snapshot #" + snapshotId + "...");
                    
                    // Do everything in background
                    runBackground("workspace-snapshot-restore-worker", () -> {
                        String errorMsg = null;
                        
                        try {
                            // Load snapshot content
                            String snapshotContent = fileService.loadSnapshotContent(fileId, snapshotId);
                            String content = snapshotContent == null ? "" : snapshotContent;
                            
                            // Restore in database
                            fileService.restoreSnapshot(fileId, snapshotId, content, currentUserId, currentBranchId);
                            
                            // Write to file system (best-effort; collaborators may not have the path)
                            if (relPath != null && wsRoot != null) {
                                try {
                                    Path targetFile = wsRoot.resolve(relPath).normalize();
                                    fileSystemService.writeFile(targetFile, content);
                                } catch (Exception ignored) {}
                            }
                            
                            // Small delay for file system sync
                            Thread.sleep(200);
                            
                        } catch (Exception ex) {
                            errorMsg = ex.getMessage();
                        }
                        
                        final String finalError = errorMsg;
                        
                        // Update UI on JavaFX thread
                        Platform.runLater(() -> {
                            if (finalError != null) {
                                restoreBtn.setDisable(false);
                                viewBtn.setDisable(false);
                                commitTable.setDisable(false);
                                summaryLabel.setText("✗ Restore failed: " + finalError);
                            } else {
                                summaryLabel.setText("Reloading workspace...");
                                try {
                                    reloadWorkspace();
                                    summaryLabel.setText("Reloading commit history...");
                                    loadWorkspaceCommitHistory(commitTable, summaryLabel);
                                    restoreBtn.setDisable(false);
                                    viewBtn.setDisable(false);
                                    summaryLabel.setText("✓ Snapshot #" + snapshotId + " restored for " + fileName);
                                    showGlassMorphicSuccess("Snapshot Restored",
                                        "Successfully restored snapshot #" + snapshotId + " for " + fileName);
                                } catch (Exception reloadEx) {
                                    restoreBtn.setDisable(false);
                                    viewBtn.setDisable(false);
                                    commitTable.setDisable(false);
                                    summaryLabel.setText("✓ Restored, but refresh failed: " + reloadEx.getMessage());
                                }
                            }
                        });
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        commitTable.getColumns().addAll(fileCol, msgCol, dateCol, byCol, versionCol, actionCol);

        // Load commits on button click
        refreshBtn.setOnAction(e -> loadWorkspaceCommitHistory(commitTable, summaryLabel));

        // Auto-load on panel creation
        loadWorkspaceCommitHistory(commitTable, summaryLabel);

        content.getChildren().addAll(headerCard, controlsBox, commitTable);
        return content;
    }

    private VBox createFileComparisonPanel() {
        VBox content = new VBox(12);
        content.setStyle("-fx-padding: 16;");
        VBox.setVgrow(content, Priority.ALWAYS);

        // Header card
        VBox headerCard = createGlassMorphicCard(
                new Label("File Comparison"),
                new Label("Compare different versions of your files"));

        // File selector
        HBox selectorBox = new HBox(12);
        selectorBox.setStyle("-fx-padding: 12; -fx-background-color: #0b0b0b; "
                + "-fx-border-color: rgba(0,255,136,0.25); -fx-border-radius: 8; -fx-background-radius: 8;");
        selectorBox.setAlignment(Pos.CENTER_LEFT);

        Label fileLabel = new Label("Select File:");
        fileLabel.setStyle("-fx-text-fill: #9ab7a8;");
        ComboBox<WorkspaceFileSelection> fileCombo = new ComboBox<>();
        fileCombo.setStyle("-fx-padding: 6; -fx-background-color: #000000; -fx-text-fill: #ffffff; "
                + "-fx-border-color: rgba(100,240,160,0.4); -fx-border-radius: 4; -fx-background-radius: 4;");
        fileCombo.setPrefWidth(320);

        Button compareBtn = new Button("Compare");
        compareBtn.setStyle("-fx-padding: 6 16; -fx-font-size: 11; -fx-text-fill: #032312; "
                + "-fx-background-color: #00df7a;");

        Button refreshBtn = new Button("↺ Refresh");
        refreshBtn.setStyle("-fx-padding: 6 12; -fx-font-size: 11; -fx-text-fill: #9ab7a8; "
                + "-fx-background-color: rgba(100,240,160,0.15); -fx-border-color: rgba(100,240,160,0.3); "
                + "-fx-border-radius: 4; -fx-background-radius: 4;");

        Region selectorSpacer = new Region();
        HBox.setHgrow(selectorSpacer, Priority.ALWAYS);
        selectorBox.getChildren().addAll(fileLabel, fileCombo, selectorSpacer, refreshBtn, compareBtn);

        HBox snapshotPickerRow = new HBox(16);
        snapshotPickerRow.setAlignment(Pos.CENTER_LEFT);
        snapshotPickerRow.setStyle("-fx-padding: 10 12; -fx-background-color: rgba(10,32,20,0.35); "
                + "-fx-border-color: rgba(100,240,160,0.22); -fx-border-radius: 8; -fx-background-radius: 8;");

        Label oldSnapLabel = new Label("Old Version:");
        oldSnapLabel.setStyle("-fx-text-fill: #9ab7a8;");
        ComboBox<WorkspaceSnapshotSelection> leftSnapshotCombo = new ComboBox<>();
        leftSnapshotCombo.setPrefWidth(300);
        leftSnapshotCombo.setStyle("-fx-padding: 6; -fx-background-color: #000000; -fx-text-fill: #ffffff; "
                + "-fx-border-color: rgba(100,240,160,0.4); -fx-border-radius: 4; -fx-background-radius: 4;");

        Label newSnapLabel = new Label("New Version:");
        newSnapLabel.setStyle("-fx-text-fill: #9ab7a8;");
        ComboBox<WorkspaceSnapshotSelection> rightSnapshotCombo = new ComboBox<>();
        rightSnapshotCombo.setPrefWidth(300);
        rightSnapshotCombo.setStyle("-fx-padding: 6; -fx-background-color: #000000; -fx-text-fill: #ffffff; "
                + "-fx-border-color: rgba(100,240,160,0.4); -fx-border-radius: 4; -fx-background-radius: 4;");

        snapshotPickerRow.getChildren().addAll(oldSnapLabel, leftSnapshotCombo, newSnapLabel, rightSnapshotCombo);

        Label statusLabel = new Label("Loading files...");
        statusLabel.setStyle("-fx-text-fill: #9ab7a8;");

        ScrollPane diffScrollPane = new ScrollPane();
        diffScrollPane.setFitToWidth(true);
        diffScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        diffScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        diffScrollPane.setStyle("-fx-background-color: #0d1117; -fx-background: #0d1117;");
        VBox.setVgrow(diffScrollPane, Priority.ALWAYS);
        diffScrollPane.setMinHeight(350);

        Label diffPlaceholder = new Label("Select file and versions, then click Compare");
        diffPlaceholder.setStyle("-fx-text-fill: #57606a; -fx-padding: 20; -fx-font-size: 13;");
        diffScrollPane.setContent(diffPlaceholder);

        fileCombo.setDisable(true);
        leftSnapshotCombo.setDisable(true);
        rightSnapshotCombo.setDisable(true);
        compareBtn.setDisable(true);

        runBackground("workspace-comparison-files-worker", () -> {
            try {
                FileDAO fileDAO = createWorkspaceFileDao();
                List<WorkspaceFileSelection> files = loadWorkspaceFilesForComparison(fileDAO);
                Platform.runLater(() -> {
                    ObservableList<WorkspaceFileSelection> fileItems = FXCollections.observableArrayList(files);
                    fileCombo.setItems(fileItems);
                    fileCombo.setDisable(false);
                    if (!fileItems.isEmpty()) {
                        fileCombo.getSelectionModel().selectFirst();
                        loadComparisonSnapshotsForFile(
                                fileItems.get(0),
                                leftSnapshotCombo,
                                rightSnapshotCombo,
                                statusLabel,
                                compareBtn);
                    } else {
                        statusLabel.setText("No files available in this workspace.");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    fileCombo.setDisable(false);
                    statusLabel.setText("Failed to load files.");
                    showGlassMorphicError("Load Failed", "Failed to load files: " + ex.getMessage());
                });
            }
        });

        fileCombo.setOnAction(e -> {
            WorkspaceFileSelection selectedFile = fileCombo.getValue();
            if (selectedFile == null) {
                leftSnapshotCombo.getItems().clear();
                rightSnapshotCombo.getItems().clear();
                leftSnapshotCombo.setDisable(true);
                rightSnapshotCombo.setDisable(true);
                compareBtn.setDisable(true);
                statusLabel.setText("Select a file to load versions.");
                return;
            }
            loadComparisonSnapshotsForFile(selectedFile, leftSnapshotCombo, rightSnapshotCombo, statusLabel,
                    compareBtn);
        });

        refreshBtn.setOnAction(e -> {
            WorkspaceFileSelection currentFile = fileCombo.getValue();
            if (currentFile == null) {
                statusLabel.setText("Select a file first, then refresh.");
                return;
            }
            loadComparisonSnapshotsForFile(currentFile, leftSnapshotCombo, rightSnapshotCombo, statusLabel, compareBtn);
        });

        compareBtn.setOnAction(e -> {
            WorkspaceFileSelection selectedFile = fileCombo.getValue();
            WorkspaceSnapshotSelection leftSnapshot = leftSnapshotCombo.getValue();
            WorkspaceSnapshotSelection rightSnapshot = rightSnapshotCombo.getValue();
            if (selectedFile == null || leftSnapshot == null || rightSnapshot == null) {
                showGlassMorphicError("Selection Required", "Select file and both snapshot versions before comparing.");
                return;
            }
            if (leftSnapshot.snapshotId() == rightSnapshot.snapshotId()) {
                showGlassMorphicError("Same Version Selected", "Select two different versions to compare.");
                return;
            }
            compareBtn.setDisable(true);
            statusLabel.setText("Loading selected versions...");
            runBackground("workspace-comparison-content-worker", () -> {
                try {
                    String leftContent = fileService.loadSnapshotContent(selectedFile.fileId(),
                            leftSnapshot.snapshotId());
                    String rightContent = fileService.loadSnapshotContent(selectedFile.fileId(),
                            rightSnapshot.snapshotId());
                    String safeLeft = leftContent == null ? "" : leftContent;
                    String safeRight = rightContent == null ? "" : rightContent;
                    List<DiffHunk> hunks = computeDiff(safeLeft.split("\n", -1), safeRight.split("\n", -1));
                    Platform.runLater(() -> {
                        compareBtn.setDisable(false);
                        diffScrollPane.setContent(buildSplitDiffView(hunks));
                        diffScrollPane.setVvalue(0);
                        statusLabel.setText("Comparing " + selectedFile.displayName() + " — "
                                + leftSnapshot.label() + " vs " + rightSnapshot.label());
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        compareBtn.setDisable(false);
                        statusLabel.setText("Failed to compare snapshots.");
                        showGlassMorphicError("Comparison Failed", "Failed to load comparison: " + ex.getMessage());
                    });
                }
            });
        });

        content.getChildren().addAll(headerCard, selectorBox, snapshotPickerRow, diffScrollPane, statusLabel);
        return content;
    }

    private void loadWorkspaceCommitHistory(TableView<WorkspaceCommitRow> commitTable, Label summaryLabel) {
        summaryLabel.setText("Loading commits...");
        commitTable.setDisable(true);

        runBackground("workspace-commit-history-worker", () -> {
            try {
                FileDAO fileDAO = createWorkspaceFileDao();
                ensureUserDirectoryLoaded();

                List<Document> commitDocs = currentBranchId != null
                        ? workspaceService.loadCommitsByBranch(workspaceId, currentBranchId, currentBranchIsDefault)
                        : fileDAO.findCommitsByWorkspace(workspaceId);
                List<WorkspaceCommitRow> rows = new ArrayList<>();

                for (Document commit : commitDocs) {
                    ObjectId fileId = commit.getObjectId("fileId");
                    if (fileId == null) {
                        continue;
                    }

                    Document fileDoc = fileDAO.findFileById(fileId).orElse(null);
                    if (fileDoc == null) {
                        continue;
                    }

                    java.util.Date committedAt = commit.getDate("committedAt");
                    long committedAtEpoch = committedAt == null ? Long.MIN_VALUE : committedAt.getTime();
                    ObjectId committedBy = commit.getObjectId("committedBy");

                    rows.add(new WorkspaceCommitRow(
                            fileId,
                            fileDoc.getString("filename"),
                            commit.getString("message") == null ? "" : commit.getString("message"),
                            resolveUsername(committedBy),
                            formatDate(committedAt),
                            commit.getInteger("snapshotId", 0),
                            committedAtEpoch));
                }

                rows.sort(Comparator.comparingLong(WorkspaceCommitRow::committedAtEpoch).reversed());
                Platform.runLater(() -> {
                    commitTable.setItems(FXCollections.observableArrayList(rows));
                    summaryLabel.setText(rows.isEmpty() ? "No commits found" : rows.size() + " commits loaded");
                    commitTable.setDisable(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    summaryLabel.setText("Failed to load commits");
                    commitTable.setDisable(false);
                    showGlassMorphicError("Load Failed", "Failed to load commits: " + ex.getMessage());
                });
            }
        });
    }

    private void showSnapshotViewer(Window modalOwner, String fileName, int snapshotId, String content) {
        Window owner = modalOwner != null ? modalOwner : resolveOwnerStage();
        Stage dialog = new Stage();
        dialog.setTitle("Snapshot Viewer");
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        } else {
            dialog.initModality(Modality.APPLICATION_MODAL);
        }

        SwingNode swingNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            RSyntaxTextArea editor = new RSyntaxTextArea(content == null ? "" : content);
            editor.setEditable(false);
            editor.setCodeFoldingEnabled(true);
            editor.setAntiAliasingEnabled(true);
            editor.setSyntaxEditingStyle(detectSyntaxStyle(fileName));
            applyEditorTheme(editor, fileName);

            RTextScrollPane scrollPane = new RTextScrollPane(editor);
            scrollPane.setLineNumbersEnabled(true);
            swingNode.setContent(scrollPane);
        });

        Label titleLabel = new Label(fileName + " - Snapshot #" + snapshotId);
        titleLabel.setStyle("-fx-text-fill: #e8fff2; -fx-font-weight: bold; -fx-font-size: 14;");

        VBox rootPane = new VBox(10, titleLabel, swingNode);
        rootPane.setPadding(new Insets(12));
        rootPane.setStyle("-fx-background-color: #0d2016;");
        VBox.setVgrow(swingNode, Priority.ALWAYS);

        Scene scene = new Scene(rootPane, 900, 620);
        scene.getStylesheets()
                .add(Objects.requireNonNull(getClass().getResource("/css/homepage.css")).toExternalForm());
        dialog.setScene(scene);
        dialog.show();
        dialog.toFront();
    }

    private void ensureUserDirectoryLoaded() {
        if (userDirectoryLoaded || workspaceService == null) {
            return;
        }
        for (UserModel user : workspaceService.loadAllUsers()) {
            if (user.userId() != null && user.username() != null && !user.username().isBlank()) {
                usernameById.put(user.userId(), user.username());
            }
        }
        userDirectoryLoaded = true;
    }

    private String resolveUsername(ObjectId userId) {
        if (userId == null) {
            return "System";
        }
        String existing = usernameById.get(userId);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        ensureUserDirectoryLoaded();
        return usernameById.getOrDefault(userId, "Unknown");
    }

    private List<WorkspaceFileSelection> loadWorkspaceFilesForComparison(FileDAO fileDAO) {
        List<WorkspaceFileSelection> options = new ArrayList<>();
        List<Document> fileDocs = fileDAO.findFilesByWorkspace(workspaceId);

        Map<ObjectId, String> displayByFileId = new HashMap<>();
        if (currentModel != null) {
            for (FolderModel folder : currentModel.folders()) {
                String folderName = folder.folderName() == null ? "root" : folder.folderName();
                for (FileItemModel file : folder.files()) {
                    String displayName = (folderName.isBlank() || "root".equalsIgnoreCase(folderName))
                            ? file.filename()
                            : folderName + "/" + file.filename();
                    displayByFileId.put(file.fileId(), displayName);
                }
            }
        }

        for (Document fileDoc : fileDocs) {
            ObjectId fileId = fileDoc.getObjectId("_id");
            String filename = fileDoc.getString("filename");
            if (fileId == null || filename == null || filename.isBlank()) {
                continue;
            }

            String displayName = displayByFileId.getOrDefault(fileId, filename);
            options.add(new WorkspaceFileSelection(fileId, displayName, filename));
        }

        options.sort(Comparator.comparing(WorkspaceFileSelection::displayName, String.CASE_INSENSITIVE_ORDER));
        return options;
    }

    private void loadComparisonSnapshotsForFile(
            WorkspaceFileSelection selectedFile,
            ComboBox<WorkspaceSnapshotSelection> leftSnapshotCombo,
            ComboBox<WorkspaceSnapshotSelection> rightSnapshotCombo,
            Label statusLabel,
            Button compareBtn) {
        leftSnapshotCombo.setDisable(true);
        rightSnapshotCombo.setDisable(true);
        compareBtn.setDisable(true);
        leftSnapshotCombo.getItems().clear();
        rightSnapshotCombo.getItems().clear();
        statusLabel.setText("Loading versions for " + selectedFile.displayName() + "...");

        runBackground("workspace-comparison-snapshots-worker", () -> {
            try {
                FileDAO fileDAO = createWorkspaceFileDao();
                List<WorkspaceSnapshotSelection> snapshots = loadSnapshotSelections(fileDAO, selectedFile.fileId());
                Platform.runLater(() -> {
                    ObservableList<WorkspaceSnapshotSelection> data = FXCollections.observableArrayList(snapshots);
                    leftSnapshotCombo.setItems(data);
                    rightSnapshotCombo.setItems(FXCollections.observableArrayList(data));

                    if (!data.isEmpty()) {
                        leftSnapshotCombo.getSelectionModel().selectFirst();
                        rightSnapshotCombo.getSelectionModel().selectLast();
                        if (rightSnapshotCombo.getValue() == null) {
                            rightSnapshotCombo.getSelectionModel().selectFirst();
                        }
                        leftSnapshotCombo.setDisable(false);
                        rightSnapshotCombo.setDisable(false);
                        compareBtn.setDisable(data.size() < 2);
                        if (data.size() < 2) {
                            statusLabel.setText("Only one version is available for " + selectedFile.displayName()
                                    + ". Commit one more version to compare.");
                        } else {
                            statusLabel.setText("Select two versions (shown by commit message) to compare.");
                        }
                    } else {
                        statusLabel.setText("No versions found for " + selectedFile.displayName());
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("Failed to load versions.");
                    showGlassMorphicError("Load Failed", "Failed to load versions: " + ex.getMessage());
                });
            }
        });
    }

    private List<WorkspaceSnapshotSelection> loadSnapshotSelections(FileDAO fileDAO, ObjectId fileId) {
        // Use commits as source of truth — one commit = one version entry, deduped by snapshotId
        Map<Integer, String> commitMessageBySnapshotId = new LinkedHashMap<>();
        for (Document commit : fileDAO.findCommitsByFileId(fileId)) {
            Integer snapshotId = commit.get("snapshotId", Integer.class);
            if (snapshotId == null) continue;
            String message = commit.getString("message");
            String normalized = (message == null || message.isBlank()) ? "No commit message" : message.trim();
            commitMessageBySnapshotId.putIfAbsent(snapshotId, normalized);
        }

        List<WorkspaceSnapshotSelection> snapshots = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : commitMessageBySnapshotId.entrySet()) {
            String msg = entry.getValue();
            if (msg.length() > 72) msg = msg.substring(0, 69) + "...";
            snapshots.add(new WorkspaceSnapshotSelection(entry.getKey(), "v" + entry.getKey() + " - " + msg));
        }
        snapshots.sort(Comparator.comparingInt(WorkspaceSnapshotSelection::snapshotId));
        return snapshots;
    }

    private VBox createCollaboratorsPanel() {
        VBox content = new VBox(12);
        content.setStyle("-fx-padding: 16;");

        // Glass morphic card for invite
        VBox inviteCard = createGlassMorphicCard(
                new Label("Invite Collaborators"),
                new Label("Add team members to work together"));

        HBox inviteForm = new HBox(8);
        inviteForm.setStyle("-fx-padding: 16; -fx-background-color: rgba(15,60,30,0.6); "
                + "-fx-border-color: rgba(100,240,160,0.3); -fx-border-radius: 12; -fx-background-radius: 12;");

        TextField collaboratorField = new TextField();
        collaboratorField.setPromptText("Enter collaborator username");
        collaboratorField.setStyle("-fx-padding: 8; -fx-font-size: 12; "
                + "-fx-background-color: #000000; -fx-text-fill: #ffffff; "
                + "-fx-border-color: rgba(100,240,160,0.4); -fx-border-radius: 4; -fx-background-radius: 4; "
                + "-fx-prompt-text-fill: rgba(255,255,255,0.6);");

        javafx.scene.control.ComboBox<String> permBox = new javafx.scene.control.ComboBox<>(
            javafx.collections.FXCollections.observableArrayList("read", "write"));
        permBox.setValue("write");
        permBox.setStyle("-fx-padding: 4 8; -fx-font-size: 12; -fx-background-color: #000000; "
            + "-fx-text-fill: #ffffff; -fx-border-color: rgba(100,240,160,0.4); "
            + "-fx-border-radius: 4; -fx-background-radius: 4;");
        permBox.setPrefWidth(90);

        Button inviteBtn = new Button("Invite");
        inviteBtn.setStyle("-fx-padding: 8 16; -fx-font-size: 12; -fx-text-fill: #032312; "
                + "-fx-background-color: #00df7a;");
        inviteBtn.setOnAction(e -> {
            String username = collaboratorField.getText() == null ? "" : collaboratorField.getText().trim();
            if (username.isEmpty()) {
                showGlassMorphicError("Username Required", "Enter a username to invite.");
                return;
            }
            try {
                Optional<UserModel> targetUser = workspaceService.loadAllUsers().stream()
                        .filter(user -> user.username() != null && user.username().equalsIgnoreCase(username))
                        .findFirst();

                if (targetUser.isEmpty() || targetUser.get().userId() == null) {
                    showGlassMorphicError("User Not Found", "User not found: " + username);
                    return;
                }

                if (targetUser.get().userId().equals(currentUserId)) {
                    showGlassMorphicError("Invalid User", "Owner is already in the workspace.");
                    return;
                }

                String perm = permBox.getValue() == null ? "write" : permBox.getValue();
                workspaceService.sendCollaboratorInvite(currentUserId, workspaceId, targetUser.get().userId(), perm);
                showGlassMorphicSuccess("Invite Sent", "Invitation sent to " + targetUser.get().username() + " (" + perm + ")");
                collaboratorField.clear();
            } catch (Exception ex) {
                showGlassMorphicError("Invite Failed", "Failed to send invitation: " + ex.getMessage());
            }
        });

        HBox.setHgrow(collaboratorField, Priority.ALWAYS);
        inviteForm.getChildren().addAll(collaboratorField, permBox, inviteBtn);

        // Current collaborators list
        VBox membersCard = createGlassMorphicCard(
                new Label("Current Members"),
                new Label("People with access to this workspace"));

        VBox membersList = new VBox(8);
        membersList.setStyle("-fx-padding: 12;");

        if (currentModel != null) {
            for (UserModel collab : currentModel.collaborators()) {
                String perm = "write";
                try { perm = workspaceService.getCollaboratorPermission(workspaceId, collab.userId()); } catch (Exception ignored) {}
                final String initialPerm = perm;

                Label nameLabel = new Label("👤  " + collab.username());
                nameLabel.setStyle("-fx-text-fill: #e8fff2; -fx-font-size: 13px;");

                Label permBadge = new Label(perm.toUpperCase());
                permBadge.setStyle("-fx-text-fill: " + ("read".equals(perm) ? "#f59e0b" : "#00ff88")
                    + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 6; "
                    + "-fx-background-color: rgba(0,255,136,0.08); -fx-background-radius: 4;");

                // Inline edit controls (hidden by default)
                javafx.scene.control.ComboBox<String> permCombo = new javafx.scene.control.ComboBox<>(
                    javafx.collections.FXCollections.observableArrayList("read", "write"));
                permCombo.setValue(initialPerm);
                permCombo.setStyle("-fx-font-size: 12; -fx-background-color: #000; "
                    + "-fx-border-color: rgba(100,240,160,0.4); -fx-border-radius: 4; -fx-background-radius: 4;");
                permCombo.setPrefWidth(95);
                permCombo.setVisible(false);
                permCombo.setManaged(false);

                Button saveBtn = new Button("Save");
                saveBtn.setStyle("-fx-padding: 4 12; -fx-font-size: 11; -fx-text-fill: #032312; "
                    + "-fx-background-color: #00df7a; -fx-background-radius: 4;");
                saveBtn.setVisible(false);
                saveBtn.setManaged(false);

                Button cancelBtn = new Button("✕");
                cancelBtn.setStyle("-fx-padding: 4 8; -fx-font-size: 11; -fx-text-fill: #9ab7a8; "
                    + "-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 4;");
                cancelBtn.setVisible(false);
                cancelBtn.setManaged(false);

                Button configureBtn = new Button("Configure");
                configureBtn.setStyle("-fx-padding: 4 12; -fx-font-size: 11; -fx-text-fill: #00ff88; "
                    + "-fx-background-color: rgba(0,255,136,0.1); -fx-border-color: rgba(0,255,136,0.3); "
                    + "-fx-border-radius: 4; -fx-background-radius: 4;");

                Button removeBtn = new Button("Remove");
                removeBtn.setStyle("-fx-padding: 4 10; -fx-font-size: 11; -fx-text-fill: #ef4444; "
                    + "-fx-background-color: rgba(239,68,68,0.15); -fx-border-color: rgba(239,68,68,0.3); "
                    + "-fx-border-radius: 4; -fx-background-radius: 4;");

                // Configure: show inline edit
                configureBtn.setOnAction(ev -> {
                    permBadge.setVisible(false); permBadge.setManaged(false);
                    configureBtn.setVisible(false); configureBtn.setManaged(false);
                    permCombo.setVisible(true); permCombo.setManaged(true);
                    saveBtn.setVisible(true); saveBtn.setManaged(true);
                    cancelBtn.setVisible(true); cancelBtn.setManaged(true);
                });

                // Cancel: restore view
                cancelBtn.setOnAction(ev -> {
                    permCombo.setVisible(false); permCombo.setManaged(false);
                    saveBtn.setVisible(false); saveBtn.setManaged(false);
                    cancelBtn.setVisible(false); cancelBtn.setManaged(false);
                    permBadge.setVisible(true); permBadge.setManaged(true);
                    configureBtn.setVisible(true); configureBtn.setManaged(true);
                });

                // Save: persist and refresh badge
                saveBtn.setOnAction(ev -> {
                    String selected = permCombo.getValue() == null ? "write" : permCombo.getValue();
                    try {
                        workspaceService.updateCollaboratorPermission(workspaceId, collab.userId(), selected);
                        permBadge.setText(selected.toUpperCase());
                        permBadge.setStyle("-fx-text-fill: " + ("read".equals(selected) ? "#f59e0b" : "#00ff88")
                            + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 6; "
                            + "-fx-background-color: rgba(0,255,136,0.08); -fx-background-radius: 4;");
                        showGlassMorphicSuccess("Permission Updated",
                            collab.username() + " is now " + selected.toUpperCase());
                    } catch (Exception ex) {
                        showGlassMorphicError("Update Failed", ex.getMessage());
                    }
                    permCombo.setVisible(false); permCombo.setManaged(false);
                    saveBtn.setVisible(false); saveBtn.setManaged(false);
                    cancelBtn.setVisible(false); cancelBtn.setManaged(false);
                    permBadge.setVisible(true); permBadge.setManaged(true);
                    configureBtn.setVisible(true); configureBtn.setManaged(true);
                });

                removeBtn.setOnAction(ev -> {
                    workspaceService.removeCollaborator(workspaceId, collab.userId());
                    reloadWorkspace();
                });

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(10, nameLabel, permBadge, permCombo, saveBtn, cancelBtn,
                                    spacer, configureBtn, removeBtn);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                row.setStyle("-fx-padding: 8 12; -fx-background-color: rgba(15,60,30,0.4); "
                    + "-fx-border-color: rgba(100,240,160,0.15); -fx-border-radius: 8; -fx-background-radius: 8;");
                membersList.getChildren().add(row);
            }
            if (currentModel.collaborators().isEmpty()) {
                Label none = new Label("No collaborators yet");
                none.setStyle("-fx-text-fill: rgba(148,163,184,0.5); -fx-font-size: 12px; -fx-padding: 8;");
                membersList.getChildren().add(none);
            }
        }

        content.getChildren().addAll(inviteCard, inviteForm, membersCard, membersList);
        return content;
    }

    private VBox createWorkspaceSettingsPanel() {
        VBox content = new VBox(16);
        content.setStyle("-fx-padding: 16;");

        // Rename workspace
        VBox renameCard = createGlassMorphicCard(
                new Label("Rename Workspace"),
                new Label("Change the name of your workspace"));

        HBox renameForm = new HBox(8);
        renameForm.setStyle("-fx-padding: 16; -fx-background-color: rgba(15,60,30,0.6); "
                + "-fx-border-color: rgba(100,240,160,0.3); -fx-border-radius: 12; -fx-background-radius: 12;");

        TextField nameField = new TextField();
        nameField.setPromptText("New workspace name");
        nameField.setText(workspaceNameHeader.getText());
        nameField.setStyle("-fx-padding: 8; -fx-font-size: 12; "
                + "-fx-background-color: #000000; -fx-text-fill: #ffffff; "
                + "-fx-border-color: rgba(100,240,160,0.4); -fx-border-radius: 4; -fx-background-radius: 4; "
                + "-fx-prompt-text-fill: rgba(255,255,255,0.6);");

        Button updateBtn = new Button("Update");
        updateBtn.setStyle("-fx-padding: 8 16; -fx-font-size: 12; -fx-text-fill: #032312; "
                + "-fx-background-color: #00df7a;");
        updateBtn.setOnAction(e -> {
            String updatedName = nameField.getText() == null ? "" : nameField.getText().trim();
            if (updatedName.isEmpty()) {
                showGlassMorphicError("Invalid Workspace Name", "Workspace name cannot be empty");
                return;
            }
            
            // Validate workspace name
            String validationError = validateWorkspaceName(updatedName);
            if (validationError != null) {
                showGlassMorphicError("Invalid Workspace Name", validationError);
                return;
            }
            
            try {
                workspaceService.updateWorkspaceName(workspaceId, updatedName);
                reloadWorkspace();
                nameField.setText(currentModel.workspaceName());
                showGlassMorphicSuccess("Workspace Renamed", "Workspace renamed successfully");
            } catch (Exception ex) {
                showGlassMorphicError("Rename Failed", "Failed to rename workspace: " + ex.getMessage());
            }
        });

        HBox.setHgrow(nameField, Priority.ALWAYS);
        renameForm.getChildren().addAll(nameField, updateBtn);

        // Delete workspace
        VBox deleteCard = createGlassMorphicCard(
                new Label("Delete Workspace"),
                new Label("Permanently delete this workspace and all its contents"));

        Button deleteBtn = new Button("Delete Workspace");
        deleteBtn.setStyle("-fx-padding: 8 16; -fx-font-size: 12; -fx-text-fill: white; "
                + "-fx-background-color: linear-gradient(to bottom, #ef4444, #dc2626);");
        deleteBtn.setOnAction(e -> {
            String expectedName = currentModel != null ? currentModel.workspaceName() : "";
            Optional<String> input = PopupDialogs.showTextInput(
                    resolveDialogOwner(),
                    "Delete Workspace",
                    "Type the workspace name to confirm. This cannot be undone.",
                    "Workspace name",
                    expectedName,
                    "Delete");
            if (input.isEmpty()) {
                return;
            }
            if (!input.get().equals(expectedName)) {
                showGlassMorphicError("Name Mismatch",
                        "The name you entered does not match. Deletion cancelled.");
                return;
            }
            try {
                workspaceService.deleteWorkspace(workspaceId);
                if (deleteBtn.getScene() != null
                        && deleteBtn.getScene().getWindow() instanceof Stage settingsStage) {
                    settingsStage.close();
                }
                Stage workspaceStage = resolveOwnerStage();
                if (workspaceStage != null) {
                    workspaceStage.close();
                }
            } catch (Exception ex) {
                showGlassMorphicError("Delete Failed", "Failed to delete workspace: " + ex.getMessage());
            }
        });

        // Manage collaborator permissions
        VBox permCard = createGlassMorphicCard(
                new Label("Collaborator Permissions"),
                new Label("Change read/write access for each member"));

        VBox permList = new VBox(8);
        permList.setStyle("-fx-padding: 12;");

        if (currentModel != null && !currentModel.collaborators().isEmpty()) {
            for (UserModel collab : currentModel.collaborators()) {
                String curPerm = "write";
                try { curPerm = workspaceService.getCollaboratorPermission(workspaceId, collab.userId()); } catch (Exception ignored) {}

                Label nameLabel = new Label("👤  " + collab.username());
                nameLabel.setStyle("-fx-text-fill: #e8fff2; -fx-font-size: 13px;");

                final String finalCurPerm = curPerm;
                javafx.scene.control.ComboBox<String> permCombo = new javafx.scene.control.ComboBox<>(
                    javafx.collections.FXCollections.observableArrayList("read", "write"));
                permCombo.setValue(finalCurPerm);
                permCombo.setStyle("-fx-font-size: 12; -fx-background-color: #000000; "
                    + "-fx-border-color: rgba(100,240,160,0.4); -fx-border-radius: 4; -fx-background-radius: 4;");
                permCombo.setPrefWidth(100);

                Button applyBtn = new Button("Apply");
                applyBtn.setStyle("-fx-padding: 4 12; -fx-font-size: 11; -fx-text-fill: #032312; "
                    + "-fx-background-color: #00df7a; -fx-background-radius: 4;");
                applyBtn.setOnAction(ev -> {
                    String selected = permCombo.getValue() == null ? "write" : permCombo.getValue();
                    try {
                        workspaceService.updateCollaboratorPermission(workspaceId, collab.userId(), selected);
                        showGlassMorphicSuccess("Permission Updated",
                            collab.username() + " is now " + selected.toUpperCase());
                    } catch (Exception ex) {
                        showGlassMorphicError("Update Failed", ex.getMessage());
                    }
                });

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(10, nameLabel, spacer, permCombo, applyBtn);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                row.setStyle("-fx-padding: 8 12; -fx-background-color: rgba(15,60,30,0.4); "
                    + "-fx-border-color: rgba(100,240,160,0.15); -fx-border-radius: 8; -fx-background-radius: 8;");
                permList.getChildren().add(row);
            }
        } else {
            Label none = new Label("No collaborators to configure");
            none.setStyle("-fx-text-fill: rgba(148,163,184,0.5); -fx-font-size: 12px; -fx-padding: 8;");
            permList.getChildren().add(none);
        }

        content.getChildren().addAll(renameCard, renameForm, permCard, permList, deleteCard, deleteBtn);
        return content;
    }

    private VBox createGlassMorphicCard(Label title, Label subtitle) {
        VBox card = new VBox(6);
        card.setStyle("-fx-padding: 16; -fx-background-color: rgba(15,60,30,0.5); "
                + "-fx-border-color: rgba(100,240,160,0.25); -fx-border-radius: 12; "
                + "-fx-background-radius: 12;");

        title.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #00ff88;");
        subtitle.setStyle("-fx-font-size: 12; -fx-text-fill: #9ab7a8;");

        card.getChildren().addAll(title, subtitle);
        return card;
    }

    @FXML
    private void onCommitRequested() {
        if (!canWrite) { showGlassMorphicError("Read-Only", "You have read-only access to this workspace."); return; }
        if (selectedFile == null || selectedPath == null) {
            showGlassMorphicError("No File Selected", "Select a file before committing");
            return;
        }

        String commitMessage = PopupDialogs.showTextInput(
                resolveOwnerStage(),
                "Commit Changes",
                "Save this file content as a new commit.",
                "Commit message",
                "Describe your changes",
                "Commit")
                .orElse(null);

        if (commitMessage != null && !commitMessage.isBlank()) {
            commitSelectedFile(commitMessage);
        }
    }

    private void commitSelectedFile(String commitMessage) {
        try {
            String content;
            try {
                content = fileSystemService.readFile(selectedPath);
            } catch (Exception e) {
                if (selectedFile != null) {
                    content = fileService.loadContent(selectedFile.fileId());
                    if (content == null) content = "";
                } else {
                    showGlassMorphicError("Commit Failed", "Unable to read file: " + e.getMessage());
                    return;
                }
            }
            fileService.commit(selectedFile.fileId(), currentUserId, commitMessage, content, workspaceId, currentBranchId);
            reloadWorkspace();
            showGlassMorphicSuccess("Commit Saved", "Commit saved successfully");
        } catch (Exception e) {
            showGlassMorphicError("Commit Failed", "Commit failed: " + e.getMessage());
        }
    }

    private void reloadWorkspace() {
        currentModel = workspaceService.loadWorkspace(workspaceId, currentBranchId, currentBranchIsDefault);
        workspaceNameHeader.setText(currentModel.workspaceName());

        workspaceRoot = fileSystemService.normalizeWorkspaceRoot(currentModel.workspaceRootPath());
        buildMetadataIndex();
        if (currentBrowsePath == null || !isInsideWorkspace(currentBrowsePath)) {
            currentBrowsePath = workspaceRoot;
        }
        populateCurrentDirectoryTable();
        loadReadmeSection();
        updateContributorsPanel();
        updateLanguagePanel();
        updateBranchSelector();
        updateTagsPanel();
        
        // Keep selectedFile if it still exists in the model (don't rely on filesystem — collaborators won't have the path)
        if (selectedFile != null) {
            final org.bson.types.ObjectId sid = selectedFile.fileId();
            boolean stillExists = currentModel.folders().stream()
                    .flatMap(f -> f.files().stream())
                    .anyMatch(f -> sid.equals(f.fileId()));
            if (!stillExists) {
                selectedFile = null;
                selectedPath = null;
            }
        }
    }

    private void populateCurrentDirectoryTable() {
        List<WorkspaceFileRow> rows = new ArrayList<>();
        if (currentBrowsePath == null) {
            fileTable.getItems().clear();
            updateBreadcrumbPath();
            updateCurrentPathLabel();
            return;
        }

        // MongoDB is always the source of truth for which files belong to the current branch.
        // The filesystem is only consulted for "last modified" timestamps.
        if (currentModel != null) {
            String currentFolderName = null;
            if (workspaceRoot != null && !currentBrowsePath.normalize().equals(workspaceRoot)) {
                try {
                    currentFolderName = workspaceRoot.relativize(currentBrowsePath.normalize()).toString().replace('\\', '/');
                } catch (IllegalArgumentException ignored) {}
            }

            if (currentFolderName == null) {
                // Root view: inline files from "root" folder, show other folders as entries
                for (FolderModel folder : currentModel.folders()) {
                    Path folderPath = workspaceRoot == null
                            ? Path.of(folder.folderName())
                            : workspaceRoot.resolve(folder.folderName());
                    if (folder.folderName().equalsIgnoreCase("root")) {
                        for (FileItemModel file : folder.files()) {
                            // Path directly under workspaceRoot so resolveRelativePath gives "filename" not "root/filename"
                            Path filePath = workspaceRoot != null
                                    ? workspaceRoot.resolve(file.filename())
                                    : Path.of(file.filename());
                            String msg = file.latestCommitMessage() == null || file.latestCommitMessage().isBlank()
                                    ? "No commits yet" : file.latestCommitMessage();
                            String modifiedLabel = "-";
                            try { Instant m = fileSystemService.getLastModified(filePath); if (m != null) modifiedLabel = formatRelative(m); } catch (Exception ignored) {}
                            rows.add(new WorkspaceFileRow(file.filename(), msg, modifiedLabel, file, filePath, false));
                        }
                    } else {
                        String modifiedLabel = "-";
                        try { Instant m = fileSystemService.getLastModified(folderPath); if (m != null) modifiedLabel = formatRelative(m); } catch (Exception ignored) {}
                        rows.add(new WorkspaceFileRow(folder.folderName(), "-", modifiedLabel, null, folderPath, true));
                    }
                }
            } else {
                // Inside a folder: show only files belonging to this branch's folder
                final String targetFolder = currentFolderName;
                for (FolderModel folder : currentModel.folders()) {
                    if (folder.folderName().equalsIgnoreCase(targetFolder)) {
                        Path folderPath = workspaceRoot == null
                                ? Path.of(folder.folderName())
                                : workspaceRoot.resolve(folder.folderName());
                        for (FileItemModel file : folder.files()) {
                            Path filePath = folderPath.resolve(file.filename());
                            String msg = file.latestCommitMessage() == null || file.latestCommitMessage().isBlank()
                                    ? "No commits yet" : file.latestCommitMessage();
                            String modifiedLabel = "-";
                            try { Instant m = fileSystemService.getLastModified(filePath); if (m != null) modifiedLabel = formatRelative(m); } catch (Exception ignored) {}
                            rows.add(new WorkspaceFileRow(file.filename(), msg, modifiedLabel, file, filePath, false));
                        }
                        break;
                    }
                }
            }
        }
        fileTable.getItems().setAll(rows);
        updateBreadcrumbPath();
        updateCurrentPathLabel();
    }

    private void updateCurrentPathLabel() {
        if (currentPathLabel == null || workspaceRoot == null || currentBrowsePath == null) {
            return;
        }

        Path normalized = currentBrowsePath.normalize();
        String value;
        if (normalized.equals(workspaceRoot)) {
            value = "/root";
        } else if (normalized.startsWith(workspaceRoot)) {
            String relative = workspaceRoot.relativize(normalized).toString().replace('\\', '/');
            value = "/root/" + relative;
        } else {
            value = normalized.toString().replace('\\', '/');
        }
        currentPathLabel.setText(value);
    }

    private void updateBreadcrumbPath() {
        if (breadcrumbBar == null) {
            return;
        }

        breadcrumbBar.getChildren().clear();
        if (workspaceRoot == null || currentBrowsePath == null) {
            return;
        }

        List<Path> segmentPaths = new ArrayList<>();
        List<String> segmentNames = new ArrayList<>();

        segmentPaths.add(workspaceRoot);
        segmentNames.add("root");

        Path normalizedBrowse = currentBrowsePath.normalize();
        if (normalizedBrowse.startsWith(workspaceRoot)) {
            Path relative = workspaceRoot.relativize(normalizedBrowse);
            Path running = workspaceRoot;
            for (Path part : relative) {
                running = running.resolve(part);
                segmentPaths.add(running);
                segmentNames.add(part.toString());
            }
        }

        for (int i = 0; i < segmentNames.size(); i++) {
            boolean isLast = i == segmentNames.size() - 1;
            Label segment = new Label(segmentNames.get(i));
            segment.getStyleClass().add("workspace-breadcrumb-segment");

            if (isLast) {
                segment.getStyleClass().add("workspace-breadcrumb-current");
            } else {
                Path target = segmentPaths.get(i);
                segment.setOnMouseClicked(event -> {
                    currentBrowsePath = target;
                    selectedFile = null;
                    selectedPath = null;
                    populateCurrentDirectoryTable();
                });
            }
            breadcrumbBar.getChildren().add(segment);

            if (!isLast) {
                Label separator = new Label("/");
                separator.getStyleClass().add("workspace-breadcrumb-separator");
                breadcrumbBar.getChildren().add(separator);
            }
        }
    }

    private void loadReadmeSection() {
        // Check if README.md exists in DB (any branch — README is workspace-level)
        boolean readmeExists = currentModel.folders().stream()
                .anyMatch(f -> f.folderName().equalsIgnoreCase("root") &&
                        f.files().stream().anyMatch(file -> file.filename().equalsIgnoreCase("README.md")));

        if (!readmeExists) {
            readmeSection.setManaged(false);
            readmeSection.setVisible(false);
            return;
        }

        // DB is source of truth; filesystem only as fallback when DB empty
        String content = currentModel.readmeContent();
        if (content == null || content.isBlank()) {
            if (workspaceRoot != null) {
                Path readmePath = workspaceRoot.resolve("README.md").normalize();
                try {
                    if (java.nio.file.Files.exists(readmePath)) {
                        content = fileSystemService.readFile(readmePath);
                    }
                } catch (IOException ignored) {}
            }
        }

        readmeSection.setManaged(true);
        readmeSection.setVisible(true);

        if (readmeWebView != null) {
            String display = (content == null || content.isBlank())
                    ? "No content yet. Open README.md and commit some content."
                    : stripMarkdown(content);
            readmeWebView.setText(display);
        }
    }

    @FXML
    private void onEditReadme() {
        if (currentModel == null) return;
        FileItemModel readmeFile = currentModel.folders().stream()
                .filter(f -> f.folderName().equalsIgnoreCase("root"))
                .flatMap(f -> f.files().stream())
                .filter(file -> file.filename().equalsIgnoreCase("README.md"))
                .findFirst().orElse(null);
        if (readmeFile == null) return;

        String existing = fileService.loadContent(readmeFile.fileId());

        Stage dialog = new Stage();
        dialog.setTitle("Edit README.md");
        dialog.initModality(Modality.APPLICATION_MODAL);
        Stage owner = resolveOwnerStage();
        if (owner != null) dialog.initOwner(owner);

        TextArea area = new TextArea(existing);
        area.setWrapText(true);
        area.setPrefHeight(400);
        area.setStyle("-fx-control-inner-background:#0d0d0d; -fx-text-fill:#e8fff2; -fx-font-family:'Courier New'; -fx-font-size:13px;");

        Button saveBtn = new Button("Save");
        saveBtn.setStyle("-fx-background-color:#00ff88; -fx-text-fill:#000; -fx-font-weight:bold; -fx-background-radius:8; -fx-padding:6 20 6 20;");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color:transparent; -fx-border-color:rgba(0,255,136,0.4); -fx-border-radius:8; -fx-background-radius:8; -fx-text-fill:#99b8a8; -fx-padding:6 20 6 20;");

        HBox buttons = new HBox(10, cancelBtn, saveBtn);
        buttons.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        buttons.setPadding(new javafx.geometry.Insets(8, 0, 0, 0));

        VBox root = new VBox(10, area, buttons);
        root.setPadding(new javafx.geometry.Insets(16));
        root.setStyle("-fx-background-color:#0b0b0b;");

        dialog.setScene(new Scene(root, 700, 460));

        cancelBtn.setOnAction(e -> dialog.close());
        saveBtn.setOnAction(e -> {
            String content = area.getText();
            try {
                if (workspaceRoot != null) {
                    try { fileSystemService.writeFile(workspaceRoot.resolve("README.md"), content); } catch (Exception ignored) {}
                }
                fileService.commit(readmeFile.fileId(), currentUserId, "Update README.md", content, workspaceId, currentBranchId);
                dialog.close();
                reloadWorkspace();
                showGlassMorphicSuccess("README Saved", "README.md updated successfully");
            } catch (Exception ex) {
                showGlassMorphicError("Save Failed", ex.getMessage());
            }
        });

        dialog.show();
    }

    private static String stripMarkdown(String text) {
        if (text == null) return "";
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            // Strip heading markers
            String stripped = line.replaceAll("^#{1,6}\\s*", "");
            // Strip bold/italic markers
            stripped = stripped.replaceAll("\\*{1,3}([^*]+)\\*{1,3}", "$1");
            // Strip inline code
            stripped = stripped.replaceAll("`([^`]*)`", "$1");
            // Strip list markers
            stripped = stripped.replaceAll("^[-*+]\\s+", "  • ");
            // Strip HTML tags
            stripped = stripped.replaceAll("<[^>]+>", "");
            sb.append(stripped).append("\n");
        }
        return sb.toString().trim();
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
            return;
        }

        selectedPath = row.path();
        selectedFile = row.file();
        openEditorWindow(row.path(), row.displayName());
    }

    private void openEditorWindow(Path filePath, String displayName) {
        String initialContent;
        boolean fileOnDisk = filePath != null && Files.exists(filePath);
        if (fileOnDisk) {
            try {
                initialContent = fileSystemService.readFile(filePath);
            } catch (Exception e) {
                initialContent = "";
            }
        } else if (selectedFile != null) {
            // Collaborator on different machine — load from MongoDB snapshot
            initialContent = fileService.loadContent(selectedFile.fileId());
            if (initialContent == null) initialContent = "";
        } else {
            showGlassMorphicError("File Load Failed", "File not found on disk and no metadata available.");
            return;
        }

        final String editorInitialContent = initialContent;

        SwingNode swingNode = new SwingNode();
        AtomicReference<RSyntaxTextArea> editorRef = new AtomicReference<>();

        Button saveButton = new Button("Commit Changes");
        saveButton.getStyleClass().add("workspace-editor-commit-button");
        saveButton.setDisable(true);
        if (!canWrite) {
            saveButton.setDisable(true);
            saveButton.setText("Read-Only");
            saveButton.setStyle("-fx-opacity: 0.4;");
        }

        SwingUtilities.invokeLater(() -> {
            RSyntaxTextArea editor = new RSyntaxTextArea(editorInitialContent);
            editor.setCodeFoldingEnabled(true);
            editor.setAntiAliasingEnabled(true);
            editor.setSyntaxEditingStyle(detectSyntaxStyle(displayName));
            applyEditorTheme(editor, displayName);

            editor.getDocument().addDocumentListener(new DocumentListener() {
                private void onTextChanged() {
                    if (!canWrite) return;
                    boolean changed = !editor.getText().equals(editorInitialContent);
                    Platform.runLater(() -> saveButton.setDisable(!changed));
                }

                @Override
                public void insertUpdate(DocumentEvent e) {
                    onTextChanged();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    onTextChanged();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    onTextChanged();
                }
            });

            RTextScrollPane scrollPane = new RTextScrollPane(editor);
            scrollPane.setLineNumbersEnabled(true);
            editorRef.set(editor);
            swingNode.setContent(scrollPane);
        });

        Stage stage = new Stage();
        stage.setTitle("Edit " + displayName);
        stage.initModality(Modality.APPLICATION_MODAL);
        Stage owner = resolveOwnerStage();
        if (owner != null) {
            stage.initOwner(owner);
        }

        saveButton.setOnAction(event -> {
            try {
                RSyntaxTextArea editor = editorRef.get();
                if (editor == null) {
                    showGlassMorphicError("Editor Not Ready", "Editor is still loading. Please try again.");
                    return;
                }

                String commitMessage = PopupDialogs.showTextInput(
                        stage,
                        "Commit Changes",
                        "Save editor updates to version history.",
                        "Commit message",
                        "Describe your changes",
                        "Commit")
                        .orElse(null);
                if (commitMessage == null) {
                    return;
                }

                String content = editor.getText();
                try { fileSystemService.writeFile(filePath, content); } catch (Exception ignored) {}

                // Use selectedFile directly if available (works for collaborators whose workspaceRoot differs)
                FileItemModel fileMetadata = selectedFile;
                if (fileMetadata == null) {
                    Path parent = filePath.getParent();
                    String relativeFolder = (parent == null || parent.equals(workspaceRoot))
                            ? "root"
                            : resolveRelativePath(parent);
                    workspaceService.ensureFileMetadata(
                            workspaceId, currentUserId, relativeFolder,
                            filePath.getFileName().toString(), currentBranchId);

                    String relativeFilePath = resolveRelativePath(filePath);
                    fileMetadata = metadataByRelativePath.get(relativeFilePath);
                    if (fileMetadata == null) {
                        reloadWorkspace();
                        fileMetadata = metadataByRelativePath.get(relativeFilePath);
                    }
                }
                if (fileMetadata == null) {
                    showGlassMorphicError("Metadata Not Found", "Unable to locate file metadata for commit");
                    return;
                }

                fileService.commit(fileMetadata.fileId(), currentUserId, commitMessage, content, workspaceId, currentBranchId);
                reloadWorkspace();
                stage.close();
                populateCurrentDirectoryTable();
            } catch (Exception e) {
                showGlassMorphicError("Save Failed", "Save failed: " + e.getMessage());
            }
        });

        BorderPane editorRoot = new BorderPane();
        editorRoot.getStyleClass().add("workspace-editor-root");
        editorRoot.setCenter(swingNode);

        HBox footer = new HBox(saveButton);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getStyleClass().add("workspace-editor-footer");
        editorRoot.setBottom(footer);

        Scene scene = new Scene(editorRoot, 900, 620);
        scene.getStylesheets()
                .add(Objects.requireNonNull(getClass().getResource("/css/homepage.css")).toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
        populateCurrentDirectoryTable();
    }

    private static String detectSyntaxStyle(String filename) {
        if (filename == null || filename.isBlank()) {
            return SyntaxConstants.SYNTAX_STYLE_NONE;
        }

        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return SyntaxConstants.SYNTAX_STYLE_NONE;
        }

        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "js" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            case "ts" -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT;
            case "py" -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
            case "cpp", "cc", "cxx", "h", "hpp" -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS;
            case "c" -> SyntaxConstants.SYNTAX_STYLE_C;
            case "cs" -> SyntaxConstants.SYNTAX_STYLE_CSHARP;
            case "xml" -> SyntaxConstants.SYNTAX_STYLE_XML;
            case "html", "htm" -> SyntaxConstants.SYNTAX_STYLE_HTML;
            case "css" -> SyntaxConstants.SYNTAX_STYLE_CSS;
            case "json" -> SyntaxConstants.SYNTAX_STYLE_JSON;
            case "md" -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
            case "sql" -> SyntaxConstants.SYNTAX_STYLE_SQL;
            case "kt" -> SyntaxConstants.SYNTAX_STYLE_KOTLIN;
            case "go" -> SyntaxConstants.SYNTAX_STYLE_GO;
            case "php" -> SyntaxConstants.SYNTAX_STYLE_PHP;
            case "rb" -> SyntaxConstants.SYNTAX_STYLE_RUBY;
            case "sh", "bash" -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
            case "yml", "yaml" -> SyntaxConstants.SYNTAX_STYLE_YAML;
            case "txt" -> SyntaxConstants.SYNTAX_STYLE_NONE;
            default -> SyntaxConstants.SYNTAX_STYLE_NONE;
        };
    }

    private static void applyEditorTheme(RSyntaxTextArea editor, String filename) {
        try {
            Theme theme = Theme.load(WorkspaceController.class
                    .getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
            theme.apply(editor);
        } catch (Exception ignored) {
            // Dark theme fallback
            editor.setBackground(java.awt.Color.decode("#1e1e1e"));
            editor.setForeground(java.awt.Color.decode("#d4d4d4"));
            editor.setCaretColor(java.awt.Color.decode("#ffffff"));
            editor.setCurrentLineHighlightColor(java.awt.Color.decode("#2a2a2a"));
            editor.setSelectionColor(java.awt.Color.decode("#264f78"));
        }

        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        }
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
            showGlassMorphicError("Workspace Error", "Workspace path is unavailable: " + e.getMessage());
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

    private void updateLanguagePanel() {
        if (languageProjectTitleLabel != null) {
            languageProjectTitleLabel.setText(currentModel == null ? "Project" : currentModel.workspaceName());
        }
        if (languageBarContainer == null || languageLegendBox == null || currentModel == null) {
            return;
        }

        Map<String, Integer> counts = new HashMap<>();
        for (FolderModel folder : currentModel.folders()) {
            for (FileItemModel file : folder.files()) {
                String language = detectLanguage(file.filename());
                counts.put(language, counts.getOrDefault(language, 0) + 1);
            }
        }

        languageBarContainer.getChildren().clear();
        languageLegendBox.getChildren().clear();

        if (counts.isEmpty()) {
            Label empty = new Label("No files yet");
            empty.getStyleClass().add("workspace-meta-label-dark");
            languageLegendBox.getChildren().add(empty);
            return;
        }

        List<Map.Entry<String, Integer>> sorted = counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .toList();

        int total = sorted.stream().mapToInt(Map.Entry::getValue).sum();
        String[] colors = new String[] {
                "#c58a1b", "#7a43b6", "#2f9bdf", "#39c274", "#f06767", "#4bb4a6", "#d8a837"
        };

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            double percentage = (entry.getValue() * 100.0) / total;
            String color = colors[i % colors.length];

            Region segment = new Region();
            segment.getStyleClass().add("workspace-language-segment");
            segment.setStyle("-fx-background-color: " + color + ";");
            segment.prefWidthProperty().bind(languageBarContainer.widthProperty().multiply(percentage / 100.0));
            HBox.setHgrow(segment, Priority.ALWAYS);
            languageBarContainer.getChildren().add(segment);

            Region dot = new Region();
            dot.getStyleClass().add("workspace-language-dot");
            dot.setStyle("-fx-background-color: " + color + ";");

            Label label = new Label(entry.getKey() + " " + String.format(Locale.ROOT, "%.1f%%", percentage));
            label.getStyleClass().add("workspace-language-legend-label");

            HBox legendRow = new HBox(8, dot, label);
            legendRow.setAlignment(Pos.CENTER_LEFT);
            languageLegendBox.getChildren().add(legendRow);
        }
    }

    private void updateContributorsPanel() {
        if (contributorsListBox == null || contributorsCountLabel == null || currentModel == null) {
            return;
        }

        for (UserModel user : currentModel.collaborators()) {
            if (user.userId() != null && user.username() != null && !user.username().isBlank()) {
                usernameById.put(user.userId(), user.username());
            }
        }

        if (!usernameById.containsKey(currentUserId)) {
            for (UserModel user : workspaceService.loadAllUsers()) {
                if (user.userId() != null && user.username() != null && !user.username().isBlank()) {
                    usernameById.put(user.userId(), user.username());
                }
            }
            userDirectoryLoaded = true;
        }

        LinkedHashSet<String> names = new LinkedHashSet<>();
        String ownerName = usernameById.getOrDefault(currentUserId, "Owner");
        names.add(ownerName + " (owner)");
        for (UserModel collaborator : currentModel.collaborators()) {
            if (collaborator.userId() != null && collaborator.userId().equals(currentUserId)) {
                continue;
            }
            String name = collaborator.username() == null || collaborator.username().isBlank()
                    ? "Collaborator"
                    : collaborator.username();
            names.add(name);
        }

        contributorsListBox.getChildren().clear();
        for (String name : names) {
            Label label = new Label(name);
            label.getStyleClass().add("workspace-contributor-name");
            contributorsListBox.getChildren().add(label);
        }
        contributorsCountLabel.setText(String.valueOf(names.size()));
    }

    private static String detectLanguage(String filename) {
        if (filename == null || filename.isBlank()) {
            return "Other";
        }
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "Other";
        }

        String ext = filename.substring(index + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "txt" -> "TXT";
            case "py" -> "Python";
            case "java" -> "Java";
            case "cpp", "cc", "cxx" -> "C++";
            case "c" -> "C";
            case "js" -> "JavaScript";
            case "ts" -> "TypeScript";
            case "css" -> "CSS";
            case "html", "htm" -> "HTML";
            case "md" -> "Markdown";
            case "json" -> "JSON";
            case "xml" -> "XML";
            case "sql" -> "SQL";
            default -> ext.toUpperCase(Locale.ROOT);
        };
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

    private void runBackground(String threadName, Runnable work) {
        Thread worker = new Thread(work, threadName);
        worker.setDaemon(true);
        worker.start();
    }

    private Stage resolveOwnerStage() {
        if (root != null && root.getScene() != null && root.getScene().getWindow() instanceof Stage stage) {
            return stage;
        }
        return null;
    }

    private Window resolveDialogOwner() {
        for (Window window : Window.getWindows()) {
            if (window != null && window.isShowing() && window.isFocused()) {
                return window;
            }
        }

        Stage owner = resolveOwnerStage();
        if (owner != null && owner.isShowing()) {
            return owner;
        }

        for (Window window : Window.getWindows()) {
            if (window != null && window.isShowing()) {
                return window;
            }
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
        showInfo(message, PopupDialogs.Theme.GREEN);
    }

    private void showInfo(String message, PopupDialogs.Theme theme) {
        PopupDialogs.showInfo(resolveDialogOwner(), "Workspace", message, theme);
    }

    private void showError(String message) {
        PopupDialogs.showError(resolveDialogOwner(), "Workspace", message);
    }

    private void showGlassMorphicError(String title, String message) {
        Window owner = resolveDialogOwner();
        Stage dialog = new Stage();
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        // Close button
        Button closeButton = new Button("✕");
        closeButton.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #ffffff; "
                + "-fx-background-color: transparent; -fx-border-color: transparent; "
                + "-fx-padding: 5; -fx-cursor: hand;");
        closeButton.setOnAction(e -> dialog.close());
        closeButton.setOnMouseEntered(e -> closeButton.setStyle("-fx-font-size: 16; -fx-font-weight: bold; "
                + "-fx-text-fill: #ff6b6b; -fx-background-color: rgba(255,255,255,0.1); "
                + "-fx-border-color: transparent; -fx-padding: 5; -fx-cursor: hand; -fx-background-radius: 3;"));
        closeButton.setOnMouseExited(e -> closeButton.setStyle("-fx-font-size: 16; -fx-font-weight: bold; "
                + "-fx-text-fill: #ffffff; -fx-background-color: transparent; "
                + "-fx-border-color: transparent; -fx-padding: 5; -fx-cursor: hand;"));

        // Header with close button
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(spacer, closeButton);
        header.setAlignment(Pos.CENTER_RIGHT);
        header.setPadding(new Insets(5, 5, 0, 5));

        // Title label
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #ffffff; "
                + "-fx-padding: 10 20 5 20;");

        // Message label
        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #e8fff2; "
                + "-fx-padding: 5 20 20 20; -fx-line-spacing: 2;");

        // Main content
        VBox content = new VBox(0, header, titleLabel, messageLabel);
        content.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, "
                + "rgba(0, 255, 136, 0.15), "
                + "rgba(0, 200, 100, 0.12), "
                + "rgba(0, 150, 80, 0.1)); "
                + "-fx-background-radius: 15; "
                + "-fx-border-color: rgba(0, 255, 136, 0.3); "
                + "-fx-border-width: 1; "
                + "-fx-border-radius: 15; "
                + "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.4), 20, 0.3, 0, 5);");
        content.setMaxWidth(400);
        content.setMinWidth(300);

        // Backdrop blur effect
        StackPane backdrop = new StackPane();
        backdrop.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6);");
        backdrop.setOnMouseClicked(e -> {
            if (e.getTarget() == backdrop) {
                dialog.close();
            }
        });

        StackPane root = new StackPane(backdrop, content);
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets()
                .add(Objects.requireNonNull(getClass().getResource("/css/homepage.css")).toExternalForm());

        dialog.setScene(scene);
        
        // Center on owner
        if (owner != null) {
            dialog.setOnShown(event -> {
                dialog.setX(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2);
                dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2);
            });
        }
        
        dialog.showAndWait();
    }

    private void showGlassMorphicSuccess(String title, String message) {
        Window owner = resolveDialogOwner();
        Stage dialog = new Stage();
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        // Close button
        Button closeButton = new Button("✕");
        closeButton.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #ffffff; "
                + "-fx-background-color: transparent; -fx-border-color: transparent; "
                + "-fx-padding: 5; -fx-cursor: hand;");
        closeButton.setOnAction(e -> dialog.close());
        closeButton.setOnMouseEntered(e -> closeButton.setStyle("-fx-font-size: 16; -fx-font-weight: bold; "
                + "-fx-text-fill: #ff6b6b; -fx-background-color: rgba(255,255,255,0.1); "
                + "-fx-border-color: transparent; -fx-padding: 5; -fx-cursor: hand; -fx-background-radius: 3;"));
        closeButton.setOnMouseExited(e -> closeButton.setStyle("-fx-font-size: 16; -fx-font-weight: bold; "
                + "-fx-text-fill: #ffffff; -fx-background-color: transparent; "
                + "-fx-border-color: transparent; -fx-padding: 5; -fx-cursor: hand;"));

        // Header with close button
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(spacer, closeButton);
        header.setAlignment(Pos.CENTER_RIGHT);
        header.setPadding(new Insets(5, 5, 0, 5));

        // Title label
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #ffffff; "
                + "-fx-padding: 10 20 5 20;");

        // Message label
        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #e8fff2; "
                + "-fx-padding: 5 20 20 20; -fx-line-spacing: 2;");

        // Main content with success styling
        VBox content = new VBox(0, header, titleLabel, messageLabel);
        content.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, "
                + "rgba(0, 255, 136, 0.2), "
                + "rgba(0, 220, 110, 0.18), "
                + "rgba(0, 180, 90, 0.15)); "
                + "-fx-background-radius: 15; "
                + "-fx-border-color: rgba(0, 255, 136, 0.4); "
                + "-fx-border-width: 1; "
                + "-fx-border-radius: 15; "
                + "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.4), 20, 0.3, 0, 5);");
        content.setMaxWidth(400);
        content.setMinWidth(300);

        // Backdrop blur effect
        StackPane backdrop = new StackPane();
        backdrop.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6);");
        backdrop.setOnMouseClicked(e -> {
            if (e.getTarget() == backdrop) {
                dialog.close();
            }
        });

        StackPane root = new StackPane(backdrop, content);
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets()
                .add(Objects.requireNonNull(getClass().getResource("/css/homepage.css")).toExternalForm());

        dialog.setScene(scene);
        
        // Center on owner
        if (owner != null) {
            dialog.setOnShown(event -> {
                dialog.setX(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2);
                dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2);
            });
        }
        
        dialog.showAndWait();
    }

    private String validateFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Name cannot be empty";
        }
        
        String trimmed = name.trim();
        
        // Check for invalid characters
        String invalidChars = "<>:\"|?*";
        for (char c : invalidChars.toCharArray()) {
            if (trimmed.indexOf(c) >= 0) {
                return "Name cannot contain special characters: " + invalidChars;
            }
        }
        
        // Check for path separators in simple file names
        if (trimmed.contains("/") || trimmed.contains("\\")) {
            return "File name cannot contain path separators (/ or \\)";
        }
        
        // Check for reserved names (Windows)
        String upperName = trimmed.toUpperCase();
        String[] reserved = {"CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", 
                           "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", 
                           "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};
        for (String res : reserved) {
            if (upperName.equals(res) || upperName.startsWith(res + ".")) {
                return "Name cannot be a reserved system name: " + res;
            }
        }
        
        // Check length
        if (trimmed.length() > 255) {
            return "Name is too long (maximum 255 characters)";
        }
        
        // Check for leading/trailing dots or spaces
        if (trimmed.startsWith(".") || trimmed.endsWith(".") || 
            trimmed.startsWith(" ") || trimmed.endsWith(" ")) {
            return "Name cannot start or end with dots or spaces";
        }
        
        return null; // Valid
    }
    
    private String validateFilePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "File path cannot be empty";
        }
        
        String trimmed = path.trim();
        
        // Split path into segments
        String[] segments = trimmed.split("[/\\\\]+");
        
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue; // Skip empty segments
            }
            
            String error = validateFileName(segment);
            if (error != null) {
                return "Invalid path segment '" + segment + "': " + error;
            }
        }
        
        // Check overall path length
        if (trimmed.length() > 1000) {
            return "File path is too long (maximum 1000 characters)";
        }
        
        return null; // Valid
    }
    
    private String validateWorkspaceName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Workspace name cannot be empty";
        }
        
        String trimmed = name.trim();
        
        // Check for invalid characters (more lenient for workspace names)
        String invalidChars = "<>:\"|?*";
        for (char c : invalidChars.toCharArray()) {
            if (trimmed.indexOf(c) >= 0) {
                return "Name cannot contain special characters: " + invalidChars;
            }
        }
        
        // Check length
        if (trimmed.length() > 100) {
            return "Workspace name is too long (maximum 100 characters)";
        }
        
        if (trimmed.length() < 2) {
            return "Workspace name must be at least 2 characters long";
        }
        
        return null; // Valid
    }

    private record WorkspaceFileRow(
            String displayName,
            String lastCommitMessage,
            String lastModified,
            FileItemModel file,
            Path path,
            boolean isFolder) {
    }

    private record WorkspaceCommitRow(
            ObjectId fileId,
            String fileName,
            String message,
            String committedByUser,
            String committedAt,
            int snapshotId,
            long committedAtEpoch) {
    }

    private enum DiffOp { EQUAL, DELETE, INSERT }

    private record DiffHunk(DiffOp op, String line) {}

    private static List<DiffHunk> computeDiff(String[] oldLines, String[] newLines) {
        int m = oldLines.length, n = newLines.length;
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--)
            for (int j = n - 1; j >= 0; j--)
                dp[i][j] = oldLines[i].equals(newLines[j])
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);

        List<DiffHunk> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < m || j < n) {
            if (i < m && j < n && oldLines[i].equals(newLines[j])) {
                result.add(new DiffHunk(DiffOp.EQUAL, oldLines[i]));
                i++; j++;
            } else if (j < n && (i >= m || dp[i][j + 1] >= dp[i + 1][j])) {
                result.add(new DiffHunk(DiffOp.INSERT, newLines[j]));
                j++;
            } else {
                result.add(new DiffHunk(DiffOp.DELETE, oldLines[i]));
                i++;
            }
        }
        return result;
    }

    private Node buildSplitDiffView(List<DiffHunk> hunks) {
        // Dark theme palette
        String DARK_BG     = "#0d1117";
        String DARK_GUT    = "#161b22";
        String DARK_TEXT   = "#c9d1d9";
        String DARK_BORDER = "#30363d";

        String RED_BG   = "#2d1515"; String RED_GUT   = "#5a1a1a"; String RED_TEXT   = "#ff8080"; String RED_SYM   = "#ff4444";
        String GREEN_BG = "#152d15"; String GREEN_GUT = "#1a5a1a"; String GREEN_TEXT = "#80ff80"; String GREEN_SYM = "#39d353";
        String BLUE_BG  = "#14172d"; String BLUE_GUT  = "#1a1f5a"; String BLUE_TEXT  = "#80aaff"; String BLUE_SYM  = "#4488ff";
        // Empty sides: single flat color — no gutter stripe on blank cells
        String EMPTY_BG = DARK_BG; String EMPTY_GUT = DARK_BG;

        String monoFont = "-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 12;";

        // Header
        HBox header = new HBox(0);
        header.setStyle("-fx-background-color: " + DARK_GUT + "; -fx-border-color: " + DARK_BORDER
                + "; -fx-border-width: 0 0 1 0; -fx-padding: 6 12;");
        Label lhOld = new Label("Old Version");
        lhOld.setStyle("-fx-text-fill: #8b949e; -fx-font-weight: bold; -fx-font-size: 12;");
        HBox.setHgrow(lhOld, Priority.ALWAYS);
        Region headerDiv = new Region();
        headerDiv.setMinWidth(1); headerDiv.setPrefWidth(1);
        headerDiv.setStyle("-fx-background-color: " + DARK_BORDER + ";");
        Label lhNew = new Label("New Version");
        lhNew.setStyle("-fx-text-fill: #8b949e; -fx-font-weight: bold; -fx-font-size: 12; -fx-padding: 0 0 0 12;");
        HBox.setHgrow(lhNew, Priority.ALWAYS);
        header.getChildren().addAll(lhOld, headerDiv, lhNew);

        VBox rows = new VBox(0);
        rows.setStyle("-fx-background-color: " + DARK_BG + ";");
        rows.getChildren().add(header);

        int oldLine = 1, newLine = 1, h = 0;
        while (h < hunks.size()) {
            DiffHunk hunk = hunks.get(h);
            if (hunk.op() == DiffOp.EQUAL) {
                rows.getChildren().add(buildDiffRow(
                        String.valueOf(oldLine), " ", hunk.line(), DARK_GUT, DARK_BG, DARK_TEXT, "",
                        String.valueOf(newLine), " ", hunk.line(), DARK_GUT, DARK_BG, DARK_TEXT, "",
                        monoFont, DARK_BORDER));
                oldLine++; newLine++; h++;
                continue;
            }
            int blockStart = h;
            int deleteCount = 0;
            while (h < hunks.size() && hunks.get(h).op() == DiffOp.DELETE) { deleteCount++; h++; }
            int insertCount = 0;
            while (h < hunks.size() && hunks.get(h).op() == DiffOp.INSERT) { insertCount++; h++; }
            List<DiffHunk> dels = hunks.subList(blockStart, blockStart + deleteCount);
            List<DiffHunk> ins  = hunks.subList(blockStart + deleteCount, blockStart + deleteCount + insertCount);
            int modCount = Math.min(deleteCount, insertCount);
            // BLUE — modified (paired delete+insert)
            for (int k = 0; k < modCount; k++) {
                rows.getChildren().add(buildDiffRow(
                        String.valueOf(oldLine + k), "~", dels.get(k).line(), BLUE_GUT, BLUE_BG, BLUE_TEXT, BLUE_SYM,
                        String.valueOf(newLine + k), "~", ins.get(k).line(),  BLUE_GUT, BLUE_BG, BLUE_TEXT, BLUE_SYM,
                        monoFont, DARK_BORDER));
            }
            // RED — pure deletion
            for (int k = modCount; k < deleteCount; k++) {
                rows.getChildren().add(buildDiffRow(
                        String.valueOf(oldLine + k), "-", dels.get(k).line(), RED_GUT, RED_BG, RED_TEXT, RED_SYM,
                        "", " ", "", EMPTY_GUT, EMPTY_BG, DARK_TEXT, "",
                        monoFont, DARK_BORDER));
            }
            // GREEN — pure insertion
            for (int k = modCount; k < insertCount; k++) {
                rows.getChildren().add(buildDiffRow(
                        "", " ", "", EMPTY_GUT, EMPTY_BG, DARK_TEXT, "",
                        String.valueOf(newLine + k), "+", ins.get(k).line(), GREEN_GUT, GREEN_BG, GREEN_TEXT, GREEN_SYM,
                        monoFont, DARK_BORDER));
            }
            oldLine += deleteCount;
            newLine += insertCount;
        }
        return rows;
    }

    private HBox buildDiffRow(
            String oldNum, String oldGutter, String oldContent, String oldGutterBg, String oldBg, String oldTextFill, String oldSymColor,
            String newNum, String newGutter, String newContent, String newGutterBg, String newBg, String newTextFill, String newSymColor,
            String monoFont, String borderColor) {
        final double ROW_H = 22;
        final double NUM_W = 44;
        final double GUT_W = 20;

        // Use GridPane so columns are guaranteed identical on both sides
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.setMinHeight(ROW_H); grid.setPrefHeight(ROW_H); grid.setMaxHeight(ROW_H);
        grid.setStyle("-fx-background-color: " + oldBg + ";");

        // Column constraints — col 0..2 = old side, col 3 = divider, col 4..6 = new side
        javafx.scene.layout.ColumnConstraints cNum1 = new javafx.scene.layout.ColumnConstraints(NUM_W, NUM_W, NUM_W);
        javafx.scene.layout.ColumnConstraints cGut1 = new javafx.scene.layout.ColumnConstraints(GUT_W, GUT_W, GUT_W);
        javafx.scene.layout.ColumnConstraints cCnt1 = new javafx.scene.layout.ColumnConstraints(0, 200, Double.MAX_VALUE);
        cCnt1.setHgrow(Priority.ALWAYS); cCnt1.setFillWidth(true);
        javafx.scene.layout.ColumnConstraints cDiv  = new javafx.scene.layout.ColumnConstraints(1, 1, 1);
        javafx.scene.layout.ColumnConstraints cNum2 = new javafx.scene.layout.ColumnConstraints(NUM_W, NUM_W, NUM_W);
        javafx.scene.layout.ColumnConstraints cGut2 = new javafx.scene.layout.ColumnConstraints(GUT_W, GUT_W, GUT_W);
        javafx.scene.layout.ColumnConstraints cCnt2 = new javafx.scene.layout.ColumnConstraints(0, 200, Double.MAX_VALUE);
        cCnt2.setHgrow(Priority.ALWAYS); cCnt2.setFillWidth(true);
        grid.getColumnConstraints().addAll(cNum1, cGut1, cCnt1, cDiv, cNum2, cGut2, cCnt2);

        // Row constraint
        javafx.scene.layout.RowConstraints rRow = new javafx.scene.layout.RowConstraints(ROW_H, ROW_H, ROW_H);
        rRow.setValignment(javafx.geometry.VPos.CENTER);
        grid.getRowConstraints().add(rRow);

        String oldSymFill = oldSymColor.isEmpty() ? "#6e7681" : oldSymColor;
        String newSymFill = newSymColor.isEmpty() ? "#6e7681" : newSymColor;

        // ── Old line number (col 0) ────────────────────────────
        Label oldNumLbl = new Label(oldNum);
        oldNumLbl.setMaxWidth(Double.MAX_VALUE); oldNumLbl.setMaxHeight(Double.MAX_VALUE);
        oldNumLbl.setAlignment(Pos.CENTER_RIGHT);
        oldNumLbl.setStyle("-fx-padding: 0 6 0 4; -fx-text-fill: #6e7681; -fx-background-color: "
                + oldGutterBg + "; " + monoFont);
        grid.add(oldNumLbl, 0, 0);

        // ── Old gutter symbol (col 1) ──────────────────────────
        Label oldGutLbl = new Label(oldGutter.isBlank() ? "" : oldGutter);
        oldGutLbl.setMaxWidth(Double.MAX_VALUE); oldGutLbl.setMaxHeight(Double.MAX_VALUE);
        oldGutLbl.setAlignment(Pos.CENTER);
        oldGutLbl.setStyle("-fx-text-fill: " + oldSymFill + "; -fx-font-weight: bold; -fx-background-color: "
                + oldGutterBg + "; " + monoFont);
        grid.add(oldGutLbl, 1, 0);

        // ── Old content (col 2) ────────────────────────────────
        Label oldCntLbl = new Label(oldContent);
        oldCntLbl.setMaxWidth(Double.MAX_VALUE); oldCntLbl.setMaxHeight(Double.MAX_VALUE);
        oldCntLbl.setAlignment(Pos.CENTER_LEFT);
        oldCntLbl.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        oldCntLbl.setStyle("-fx-padding: 0 8; -fx-background-color: " + oldBg
                + "; " + monoFont + " -fx-text-fill: " + oldTextFill + ";");
        grid.add(oldCntLbl, 2, 0);

        // ── Divider (col 3) ────────────────────────────────────
        Region div = new Region();
        div.setMaxWidth(Double.MAX_VALUE); div.setMaxHeight(Double.MAX_VALUE);
        div.setStyle("-fx-background-color: " + borderColor + ";");
        grid.add(div, 3, 0);

        // ── New line number (col 4) ────────────────────────────
        Label newNumLbl = new Label(newNum);
        newNumLbl.setMaxWidth(Double.MAX_VALUE); newNumLbl.setMaxHeight(Double.MAX_VALUE);
        newNumLbl.setAlignment(Pos.CENTER_RIGHT);
        newNumLbl.setStyle("-fx-padding: 0 6 0 4; -fx-text-fill: #6e7681; -fx-background-color: "
                + newGutterBg + "; " + monoFont);
        grid.add(newNumLbl, 4, 0);

        // ── New gutter symbol (col 5) ──────────────────────────
        Label newGutLbl = new Label(newGutter.isBlank() ? "" : newGutter);
        newGutLbl.setMaxWidth(Double.MAX_VALUE); newGutLbl.setMaxHeight(Double.MAX_VALUE);
        newGutLbl.setAlignment(Pos.CENTER);
        newGutLbl.setStyle("-fx-text-fill: " + newSymFill + "; -fx-font-weight: bold; -fx-background-color: "
                + newGutterBg + "; " + monoFont);
        grid.add(newGutLbl, 5, 0);

        // ── New content (col 6) ────────────────────────────────
        Label newCntLbl = new Label(newContent);
        newCntLbl.setMaxWidth(Double.MAX_VALUE); newCntLbl.setMaxHeight(Double.MAX_VALUE);
        newCntLbl.setAlignment(Pos.CENTER_LEFT);
        newCntLbl.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        newCntLbl.setStyle("-fx-padding: 0 8; -fx-background-color: " + newBg
                + "; " + monoFont + " -fx-text-fill: " + newTextFill + ";");
        grid.add(newCntLbl, 6, 0);

        // Wrap in an HBox that fills full width (VBox child needs HBox wrapper)
        HBox row = new HBox(0);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setMinHeight(ROW_H); row.setPrefHeight(ROW_H); row.setMaxHeight(ROW_H + 1);
        row.setStyle("-fx-border-color: " + borderColor + "; -fx-border-width: 0 0 1 0;");
        HBox.setHgrow(grid, Priority.ALWAYS);
        row.getChildren().add(grid);
        return row;
    }

    private record WorkspaceFileSelection(
            ObjectId fileId,
            String displayName,
            String fileName) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    private record WorkspaceSnapshotSelection(
            int snapshotId,
            String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private String formatDate(java.util.Date date) {
        if (date == null)
            return "N/A";
        Instant instant = date.toInstant();
        return instant.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "N/A";
        }
        return instant.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private Map<ObjectId, String> buildFileDisplayNames() {
        Map<ObjectId, String> displayNames = new LinkedHashMap<>();
        if (currentModel == null) {
            return displayNames;
        }
        for (FolderModel folder : currentModel.folders()) {
            String folderName = folder.folderName() == null ? "root" : folder.folderName().trim();
            for (FileItemModel file : folder.files()) {
                if (file.fileId() == null) {
                    continue;
                }
                String displayName = folderName.isBlank() || "root".equalsIgnoreCase(folderName)
                        ? file.filename()
                        : folderName + "/" + file.filename();
                displayNames.put(file.fileId(), displayName);
            }
        }
        return displayNames;
    }

    private List<FileCommentOption> buildFileCommentOptions(Map<ObjectId, String> fileDisplayNames) {
        List<FileCommentOption> options = new ArrayList<>();
        fileDisplayNames.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> options.add(new FileCommentOption(entry.getKey(), entry.getValue())));
        return options;
    }

    private void renderWorkspaceComments(VBox container) {
        container.getChildren().clear();
        List<WorkspaceCommentModel> comments = workspaceService.loadWorkspaceComments(workspaceId);
        if (comments.isEmpty()) {
            Label empty = new Label("No workspace comments yet.");
            empty.getStyleClass().add("workspace-comment-empty");
            container.getChildren().add(empty);
            return;
        }
        for (WorkspaceCommentModel comment : comments) {
            container.getChildren().add(createCommentCard(
                    resolveUsername(comment.authorId()),
                    formatInstant(comment.createdAt()),
                    comment.message()));
        }
    }

    private void renderFileComments(VBox container, Map<ObjectId, String> fileDisplayNames) {
        container.getChildren().clear();
        List<FileCommentModel> comments = workspaceService.loadFileComments(workspaceId);
        if (comments.isEmpty()) {
            Label empty = new Label("No file comments yet.");
            empty.getStyleClass().add("workspace-comment-empty");
            container.getChildren().add(empty);
            return;
        }

        Map<ObjectId, List<FileCommentModel>> commentsByFile = new LinkedHashMap<>();
        for (FileCommentModel comment : comments) {
            if (comment.fileId() == null) {
                continue;
            }
            commentsByFile.computeIfAbsent(comment.fileId(), ignored -> new ArrayList<>()).add(comment);
        }

        commentsByFile.entrySet().stream()
                .sorted((left, right) -> {
                    Instant leftInstant = left.getValue().isEmpty() || left.getValue().get(0).createdAt() == null
                            ? Instant.EPOCH : left.getValue().get(0).createdAt();
                    Instant rightInstant = right.getValue().isEmpty() || right.getValue().get(0).createdAt() == null
                            ? Instant.EPOCH : right.getValue().get(0).createdAt();
                    return rightInstant.compareTo(leftInstant);
                })
                .forEach(entry -> {
                    String fileName = fileDisplayNames.getOrDefault(entry.getKey(), "Unknown file");
                    int count = entry.getValue().size();
                    Label fileTitle = new Label(fileName + "  •  " + count + " comment" + (count == 1 ? "" : "s"));
                    fileTitle.getStyleClass().add("workspace-comment-group-title");

                    VBox groupBody = new VBox(10);
                    List<FileCommentModel> ordered = new ArrayList<>(entry.getValue());
                    ordered.sort(Comparator.comparing(FileCommentModel::createdAt,
                            Comparator.nullsLast(Comparator.naturalOrder())));
                    for (FileCommentModel comment : ordered) {
                        groupBody.getChildren().add(createCommentCard(
                                resolveUsername(comment.authorId()),
                                formatInstant(comment.createdAt()),
                                comment.message()));
                    }

                    VBox group = new VBox(12, fileTitle, groupBody);
                    group.getStyleClass().add("workspace-comment-group");
                    container.getChildren().add(group);
                });
    }

    private VBox createCommentCard(String author, String createdAt, String message) {
        Label authorLabel = new Label(author == null || author.isBlank() ? "Unknown" : author);
        authorLabel.getStyleClass().add("workspace-comment-author");

        Label timeLabel = new Label(createdAt);
        timeLabel.getStyleClass().add("workspace-comment-time");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, authorLabel, spacer, timeLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        Label bodyLabel = new Label(message == null ? "" : message);
        bodyLabel.getStyleClass().add("workspace-comment-body");
        bodyLabel.setWrapText(true);

        VBox card = new VBox(10, header, bodyLabel);
        card.getStyleClass().add("workspace-comment-card");
        return card;
    }

    private void updateBranchSelector() {
        if (branchSelector == null || currentModel == null) return;

        branchSelector.setStyle(
                "-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 6 10; "
                + "-fx-background-color: #0d2016; -fx-text-fill: #00ff88; "
                + "-fx-border-color: #00ff88; -fx-border-radius: 6; -fx-background-radius: 6;");
        branchSelector.setPrefWidth(180);

        // Temporarily remove listener to avoid triggering during population
        branchSelector.setOnAction(null);

        ObservableList<String> branchNames = FXCollections.observableArrayList();
        String selectValue = currentBranchName;

        List<com.dvcs.client.core.model.Branch> branches = workspaceService.loadBranches(workspaceId);
        for (com.dvcs.client.core.model.Branch b : branches) {
            branchNames.add(b.branchName());
        }

        if (branchNames.isEmpty()) {
            branchNames.add("main");
            selectValue = "main";
        }

        branchSelector.setItems(branchNames);
        branchSelector.setValue(selectValue != null && branchNames.contains(selectValue) ? selectValue : branchNames.get(0));

        // Add "New Branch" button to the parent HBox if not already there
        if (branchSelector.getParent() instanceof HBox parentBox) {
            boolean hasNewBranchBtn = parentBox.getChildren().stream()
                    .anyMatch(n -> "newBranchBtn".equals(n.getId()));
            if (!hasNewBranchBtn) {
                Button newBranchBtn = new Button("+ New Branch");
                newBranchBtn.setId("newBranchBtn");
                newBranchBtn.setStyle(
                        "-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 6 12; "
                        + "-fx-background-color: #00ff88; -fx-text-fill: #0d2016; "
                        + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;");
                newBranchBtn.setOnAction(e -> onNewBranch());
                parentBox.getChildren().add(newBranchBtn);
            }
        }

        // Re-attach listener: on selection change switch branch and reload
        branchSelector.setOnAction(e -> {
            String selected = branchSelector.getValue();
            if (selected == null || selected.equals(currentBranchName)) return;
            branches.stream()
                    .filter(b -> b.branchName().equals(selected))
                    .findFirst()
                    .ifPresent(b -> {
                        currentBranchId = b.id();
                        currentBranchName = b.branchName();
                        currentBranchIsDefault = b.isDefault();
                        reloadWorkspace();
                    });
        });
    }

    private void onNewBranch() {
        Stage owner = resolveOwnerStage();
        String name = PopupDialogs.showTextInput(
                owner,
                "New Branch",
                "Create a new branch in this workspace.",
                "Branch name",
                "e.g. feature/login, bugfix/header",
                "Create Branch")
                .orElse(null);
        if (name == null || name.isBlank()) return;
        try {
            com.dvcs.client.core.model.Branch newBranch = workspaceService.createBranch(workspaceId, name.trim(), currentUserId);
            currentBranchId = newBranch.id();
            currentBranchName = newBranch.branchName();
            reloadWorkspace();
            showGlassMorphicSuccess("Branch Created", "Switched to branch: " + newBranch.branchName());
        } catch (Exception ex) {
            showGlassMorphicError("Branch Error", ex.getMessage());
        }
    }

    private void updateTagsPanel() {
        if (tagsContainer == null || currentModel == null) return;
        tagsContainer.getChildren().clear();
        
        if (currentModel.tags().isEmpty()) {
            Label noTags = new Label("No tags yet");
            noTags.setStyle("-fx-text-fill: rgba(255,255,255,0.4); -fx-font-style: italic; -fx-font-size: 11px;");
            tagsContainer.getChildren().add(noTags);
            return;
        }
        
        for (Tag t : currentModel.tags()) {
            VBox badge = new VBox();
            badge.getStyleClass().add("tag-badge");
            
            Label tagLabel = new Label(t.tagName());
            tagLabel.getStyleClass().add("tag-badge-text");
            
            badge.getChildren().add(tagLabel);
            tagsContainer.getChildren().add(badge);
        }
    }

    private FileDAO createWorkspaceFileDao() {
        String dbName = System.getenv("MONGODB_DB");
        if (dbName == null || dbName.isBlank()) {
            dbName = "DVCS";
        }
        return new FileDAO(MongoConnection.getDatabase(dbName));
    }

    private record FileCommentOption(
            ObjectId fileId,
            String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
