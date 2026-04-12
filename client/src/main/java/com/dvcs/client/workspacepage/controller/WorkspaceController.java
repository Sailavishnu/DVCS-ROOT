package com.dvcs.client.workspacepage.controller;

import com.dvcs.client.auth.db.MongoConnection;
import com.dvcs.client.workspacepage.model.FileItemModel;
import com.dvcs.client.workspacepage.model.FolderModel;
import com.dvcs.client.workspacepage.model.UserModel;
import com.dvcs.client.workspacepage.model.WorkspacePageModel;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

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
    private TextArea readmeTextArea;

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
    private final Map<ObjectId, String> usernameById = new HashMap<>();
    private boolean userDirectoryLoaded;

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
                        + "-fx-background-color: transparent; -fx-text-fill: #d6e7ff;");
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

        try {
            Path baseFolder = currentBrowsePath == null ? workspaceRoot : currentBrowsePath;
            Path createdFolder = fileSystemService.createFolder(baseFolder, folderName.trim());
            String relativeFolder = resolveRelativePath(createdFolder);
            workspaceService.ensureFolderMetadata(workspaceId, currentUserId, relativeFolder);
            reloadWorkspace();
            showInfo("Folder created");
        } catch (Exception e) {
            showError("Failed to create folder: " + e.getMessage());
        }
    }

    @FXML
    private void onCreateFile() {
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
                "Create File",
                PopupDialogs.Theme.BLUE)
                .orElse(null);
        if (fileName == null || fileName.isBlank()) {
            return;
        }

        try {
            Path baseFolder = currentBrowsePath == null ? workspaceRoot : currentBrowsePath;
            Path createdFile = fileSystemService.createFile(baseFolder, fileName.trim());
            Path parent = createdFile.getParent();
            String relativeFolder = (parent == null || parent.equals(workspaceRoot))
                    ? "root"
                    : resolveRelativePath(parent);
            workspaceService.ensureFileMetadata(workspaceId, currentUserId, relativeFolder,
                    createdFile.getFileName().toString());
            reloadWorkspace();
            showInfo("File created", PopupDialogs.Theme.BLUE);
        } catch (Exception e) {
            showError("Failed to create file: " + e.getMessage());
        }
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

        Button deleteButton = new Button("Delete File");
        deleteButton.getStyleClass().addAll("neon-popup-button", "neon-popup-button-secondary");

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
                showError("File name cannot be empty");
                return;
            }
            if (newName.contains("/") || newName.contains("\\")) {
                showError("File name cannot contain path separators");
                return;
            }
            try {
                Path target = row.path().resolveSibling(newName);
                if (Files.exists(target)) {
                    showError("A file with this name already exists");
                    return;
                }
                Files.move(row.path(), target);
                if (row.file() != null) {
                    workspaceService.renameFileMetadata(row.file(), newName);
                }
                if (selectedPath != null && selectedPath.equals(row.path())) {
                    selectedPath = target;
                }
                dialog.close();
                reloadWorkspace();
                showInfo("File renamed successfully");
            } catch (Exception e) {
                showError("Failed to rename file: " + e.getMessage());
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
                        workspaceService.deleteFileMetadata(fileToDelete);
                    }
                } catch (Exception ex) {
                    errorMessage = ex.getMessage();
                }

                String finalErrorMessage = errorMessage;
                Platform.runLater(() -> {
                    if (finalErrorMessage != null && !finalErrorMessage.isBlank()) {
                        showError("Failed to delete file: " + finalErrorMessage);
                        return;
                    }
                    try {
                        if (wasSelected) {
                            selectedPath = null;
                            selectedFile = null;
                        }
                        reloadWorkspace();
                        PopupDialogs.showInfo(
                                resolveOwnerStage(),
                                "Workspace",
                                "File deleted successfully",
                                PopupDialogs.Theme.BLUE);
                    } catch (Exception refreshError) {
                        showError("File deleted, but refresh failed: " + refreshError.getMessage());
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
        closeButton.setStyle("-fx-font-size: 14; -fx-font-weight: 700; -fx-text-fill: #dff0ff;");
        closeButton.setOnAction(event -> dialog.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(spacer, closeButton);
        topRow.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(10, topRow, titleLabel, subtitleLabel, content);
        card.getStyleClass().addAll("neon-popup-card", "neon-popup-theme-blue");
        card.setMaxWidth(560);
        card.setMinHeight(340);
        card.setPadding(new Insets(20));

        StackPane rootPane = new StackPane(card);
        rootPane.getStyleClass().add("neon-popup-overlay");

        Scene scene = new Scene(rootPane);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets()
                .add(Objects.requireNonNull(getClass().getResource("/css/dashboard.css")).toExternalForm());

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

        // Main blue background panel
        BorderPane settingsRoot = new BorderPane();
        settingsRoot.setStyle("-fx-background-color: #1a2a4a;");

        // Horizontal Navigation Menu
        HBox navBar = new HBox();
        navBar.setStyle(
                "-fx-padding: 0; -fx-background-color: #0d1620; -fx-border-color: #3b82f6; -fx-border-width: 0 0 2 0;");
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

        // Menu buttons
        String[] menuItems = { "Commit History", "File Comparison", "Invite Collaborators", "Settings" };
        VBox[] contentPanels = { commitHistoryContent, fileComparisonContent, collaboratorsContent,
                workspaceSettingsContent };
        Button[] menuButtons = new Button[4];

        for (int i = 0; i < menuItems.length; i++) {
            final int index = i;
            Button btn = new Button(menuItems[i]);
            menuButtons[i] = btn;
            btn.setStyle("-fx-padding: 12 20; -fx-font-size: 12; -fx-text-fill: #a0b5d8; "
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
                    button.setStyle("-fx-padding: 12 20; -fx-font-size: 12; -fx-text-fill: #a0b5d8; "
                            + "-fx-background-color: transparent; -fx-border-width: 0 0 3 0; -fx-border-color: transparent;");
                }
                // Highlight active button
                btn.setStyle("-fx-padding: 12 20; -fx-font-size: 12; -fx-text-fill: #60a5fa; "
                        + "-fx-background-color: rgba(96,165,250,0.1); -fx-border-width: 0 0 3 0; -fx-border-color: #60a5fa;");
            });

            navBar.getChildren().add(btn);

            // Set first button as active
            if (i == 0) {
                btn.setStyle("-fx-padding: 12 20; -fx-font-size: 12; -fx-text-fill: #60a5fa; "
                        + "-fx-background-color: rgba(96,165,250,0.1); -fx-border-width: 0 0 3 0; -fx-border-color: #60a5fa;");
            }
        }

        settingsRoot.setTop(navBar);
        settingsRoot.setCenter(contentPane);

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double width = Math.max(1000, bounds.getWidth() * 0.8);
        double height = Math.max(700, bounds.getHeight() * 0.8);

        Scene scene = new Scene(settingsRoot, width, height);
        scene.getStylesheets()
                .add(Objects.requireNonNull(getClass().getResource("/css/dashboard.css")).toExternalForm());
        settingsStage.setScene(scene);

        settingsStage.setOnShown(event -> {
            if (owner != null) {
                settingsStage.setX(owner.getX() + ((owner.getWidth() - settingsStage.getWidth()) / 2.0));
                settingsStage.setY(owner.getY() + ((owner.getHeight() - settingsStage.getHeight()) / 2.0));
            }
        });

        settingsStage.showAndWait();
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
        controlsBox.setStyle("-fx-padding: 12; -fx-background-color: rgba(15,30,60,0.6); "
                + "-fx-border-color: rgba(100,160,240,0.3); -fx-border-radius: 8; -fx-background-radius: 8;");
        controlsBox.setAlignment(Pos.CENTER_LEFT);

        Label refreshLabel = new Label("Refresh:");
        refreshLabel.setStyle("-fx-text-fill: #a0b5d8;");
        Button refreshBtn = new Button("Load Commits");
        refreshBtn.setStyle("-fx-padding: 6 16; -fx-font-size: 11; -fx-text-fill: white; "
                + "-fx-background-color: linear-gradient(to bottom, #60a5fa, #3b82f6);");
        Label summaryLabel = new Label("Loading...");
        summaryLabel.setStyle("-fx-text-fill: #a0b5d8;");
        Region controlsSpacer = new Region();
        HBox.setHgrow(controlsSpacer, Priority.ALWAYS);

        controlsBox.getChildren().addAll(refreshLabel, refreshBtn, controlsSpacer, summaryLabel);

        // Table for commits
        TableView<WorkspaceCommitRow> commitTable = new TableView<>();
        commitTable
                .setStyle("-fx-background-color: rgba(10,20,40,0.8); -fx-control-inner-background: rgba(10,20,40,0.8);"
                        + "-fx-table-cell-border-color: rgba(100,160,240,0.15); -fx-text-background-color: #e7f0ff;");
        VBox.setVgrow(commitTable, Priority.ALWAYS);
        Label placeholderLabel = new Label("No commits found for this workspace yet.");
        placeholderLabel.setStyle("-fx-text-fill: #a0b5d8;");
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
                                showError("Failed to load snapshot: " + ex.getMessage());
                            });
                        }
                    });
                });

                restoreBtn.setOnAction(event -> {
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                        return;
                    }
                    WorkspaceCommitRow row = getTableView().getItems().get(getIndex());
                    restoreBtn.setDisable(true);
                    runBackground("workspace-snapshot-restore-worker", () -> {
                        try {
                            String snapshotContent = fileService.loadSnapshotContent(row.fileId(), row.snapshotId());
                            fileService.restoreSnapshot(row.fileId(), row.snapshotId(),
                                    snapshotContent == null ? "" : snapshotContent);
                            Platform.runLater(() -> {
                                restoreBtn.setDisable(false);
                                reloadWorkspace();
                                loadWorkspaceCommitHistory(commitTable, summaryLabel);
                                showInfo("Restored snapshot #" + row.snapshotId() + " for " + row.fileName());
                            });
                        } catch (Exception ex) {
                            Platform.runLater(() -> {
                                restoreBtn.setDisable(false);
                                showError("Failed to restore snapshot: " + ex.getMessage());
                            });
                        }
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
        selectorBox.setStyle("-fx-padding: 12; -fx-background-color: rgba(15,30,60,0.6); "
                + "-fx-border-color: rgba(100,160,240,0.3); -fx-border-radius: 8; -fx-background-radius: 8;");
        selectorBox.setAlignment(Pos.CENTER_LEFT);

        Label fileLabel = new Label("Select File:");
        fileLabel.setStyle("-fx-text-fill: #a0b5d8;");
        ComboBox<WorkspaceFileSelection> fileCombo = new ComboBox<>();
        fileCombo.setStyle("-fx-padding: 6; -fx-background-color: rgba(10,20,40,0.8); -fx-text-fill: #e7f0ff;");
        fileCombo.setPrefWidth(320);

        Button compareBtn = new Button("Compare");
        compareBtn.setStyle("-fx-padding: 6 16; -fx-font-size: 11; -fx-text-fill: white; "
                + "-fx-background-color: linear-gradient(to bottom, #34d399, #10b981);");

        Region selectorSpacer = new Region();
        HBox.setHgrow(selectorSpacer, Priority.ALWAYS);
        selectorBox.getChildren().addAll(fileLabel, fileCombo, selectorSpacer, compareBtn);

        HBox snapshotPickerRow = new HBox(16);
        snapshotPickerRow.setAlignment(Pos.CENTER_LEFT);
        snapshotPickerRow.setStyle("-fx-padding: 10 12; -fx-background-color: rgba(10,20,40,0.35); "
                + "-fx-border-color: rgba(100,160,240,0.22); -fx-border-radius: 8; -fx-background-radius: 8;");

        Label oldSnapLabel = new Label("Old Version:");
        oldSnapLabel.setStyle("-fx-text-fill: #a0b5d8;");
        ComboBox<WorkspaceSnapshotSelection> leftSnapshotCombo = new ComboBox<>();
        leftSnapshotCombo.setPrefWidth(300);

        Label newSnapLabel = new Label("New Version:");
        newSnapLabel.setStyle("-fx-text-fill: #a0b5d8;");
        ComboBox<WorkspaceSnapshotSelection> rightSnapshotCombo = new ComboBox<>();
        rightSnapshotCombo.setPrefWidth(300);

        snapshotPickerRow.getChildren().addAll(oldSnapLabel, leftSnapshotCombo, newSnapLabel, rightSnapshotCombo);

        Label statusLabel = new Label("Loading files...");
        statusLabel.setStyle("-fx-text-fill: #a0b5d8;");

        AtomicReference<RSyntaxTextArea> leftEditorRef = new AtomicReference<>();
        AtomicReference<RSyntaxTextArea> rightEditorRef = new AtomicReference<>();

        StackPane leftEditorContainer = new StackPane();
        leftEditorContainer
                .setStyle("-fx-border-color: rgba(100,160,240,0.3); -fx-border-radius: 8; -fx-background-radius: 8;");
        StackPane rightEditorContainer = new StackPane();
        rightEditorContainer
                .setStyle("-fx-border-color: rgba(100,160,240,0.3); -fx-border-radius: 8; -fx-background-radius: 8;");

        SwingNode leftNode = new SwingNode();
        SwingNode rightNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            RSyntaxTextArea leftEditor = new RSyntaxTextArea();
            leftEditor.setEditable(false);
            leftEditor.setCodeFoldingEnabled(true);
            leftEditor.setAntiAliasingEnabled(true);
            applyEditorTheme(leftEditor, "sample.txt");

            RSyntaxTextArea rightEditor = new RSyntaxTextArea();
            rightEditor.setEditable(false);
            rightEditor.setCodeFoldingEnabled(true);
            rightEditor.setAntiAliasingEnabled(true);
            applyEditorTheme(rightEditor, "sample.txt");

            RTextScrollPane leftScroll = new RTextScrollPane(leftEditor);
            leftScroll.setLineNumbersEnabled(true);
            RTextScrollPane rightScroll = new RTextScrollPane(rightEditor);
            rightScroll.setLineNumbersEnabled(true);

            leftEditorRef.set(leftEditor);
            rightEditorRef.set(rightEditor);
            leftNode.setContent(leftScroll);
            rightNode.setContent(rightScroll);
        });

        leftEditorContainer.getChildren().add(leftNode);
        rightEditorContainer.getChildren().add(rightNode);

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
                    showError("Failed to load files: " + ex.getMessage());
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

        compareBtn.setOnAction(e -> {
            WorkspaceFileSelection selectedFile = fileCombo.getValue();
            WorkspaceSnapshotSelection leftSnapshot = leftSnapshotCombo.getValue();
            WorkspaceSnapshotSelection rightSnapshot = rightSnapshotCombo.getValue();
            if (selectedFile == null || leftSnapshot == null || rightSnapshot == null) {
                showError("Select file and both snapshot versions before comparing.");
                return;
            }

            if (leftSnapshot.snapshotId() == rightSnapshot.snapshotId()) {
                showError("Select two different versions to compare.");
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

                    Platform.runLater(() -> {
                        compareBtn.setDisable(false);
                        SwingUtilities.invokeLater(() -> {
                            RSyntaxTextArea leftEditor = leftEditorRef.get();
                            RSyntaxTextArea rightEditor = rightEditorRef.get();
                            if (leftEditor != null) {
                                leftEditor.setText(leftContent == null ? "" : leftContent);
                                leftEditor.setSyntaxEditingStyle(detectSyntaxStyle(selectedFile.fileName()));
                                applyEditorTheme(leftEditor, selectedFile.fileName());
                            }
                            if (rightEditor != null) {
                                rightEditor.setText(rightContent == null ? "" : rightContent);
                                rightEditor.setSyntaxEditingStyle(detectSyntaxStyle(selectedFile.fileName()));
                                applyEditorTheme(rightEditor, selectedFile.fileName());
                            }
                        });

                        statusLabel.setText("Comparing " + selectedFile.displayName() + " - "
                                + leftSnapshot.label() + " vs " + rightSnapshot.label());
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        compareBtn.setDisable(false);
                        statusLabel.setText("Failed to compare snapshots.");
                        showError("Failed to load comparison: " + ex.getMessage());
                    });
                }
            });
        });

        // Comparison editors
        HBox comparisonBox = new HBox(16);
        comparisonBox.setStyle("-fx-padding: 12;");
        VBox.setVgrow(comparisonBox, Priority.ALWAYS);

        // Left editor
        VBox leftPane = new VBox(8);
        leftPane.setStyle("-fx-padding: 8;");
        HBox.setHgrow(leftPane, Priority.ALWAYS);
        Label oldLabel = new Label("Old Version");
        oldLabel.setStyle("-fx-text-fill: #a0b5d8; -fx-font-weight: bold;");
        VBox.setVgrow(leftEditorContainer, Priority.ALWAYS);
        leftEditorContainer.setMinHeight(340);
        leftPane.getChildren().addAll(oldLabel, leftEditorContainer);

        // Right editor
        VBox rightPane = new VBox(8);
        rightPane.setStyle("-fx-padding: 8;");
        HBox.setHgrow(rightPane, Priority.ALWAYS);
        Label newLabel = new Label("New Version");
        newLabel.setStyle("-fx-text-fill: #a0b5d8; -fx-font-weight: bold;");
        VBox.setVgrow(rightEditorContainer, Priority.ALWAYS);
        rightEditorContainer.setMinHeight(340);
        rightPane.getChildren().addAll(newLabel, rightEditorContainer);

        comparisonBox.getChildren().addAll(leftPane, rightPane);

        content.getChildren().addAll(headerCard, selectorBox, snapshotPickerRow, comparisonBox, statusLabel);
        return content;
    }

    private void loadWorkspaceCommitHistory(TableView<WorkspaceCommitRow> commitTable, Label summaryLabel) {
        summaryLabel.setText("Loading commits...");
        commitTable.setDisable(true);

        runBackground("workspace-commit-history-worker", () -> {
            try {
                FileDAO fileDAO = createWorkspaceFileDao();
                ensureUserDirectoryLoaded();

                List<Document> commitDocs = fileDAO.findCommitsByWorkspace(workspaceId);
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
                    showError("Failed to load commits: " + ex.getMessage());
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
        titleLabel.setStyle("-fx-text-fill: #60a5fa; -fx-font-weight: bold; -fx-font-size: 14;");

        VBox rootPane = new VBox(10, titleLabel, swingNode);
        rootPane.setPadding(new Insets(12));
        rootPane.setStyle("-fx-background-color: #0d1620;");
        VBox.setVgrow(swingNode, Priority.ALWAYS);

        Scene scene = new Scene(rootPane, 900, 620);
        scene.getStylesheets()
                .add(Objects.requireNonNull(getClass().getResource("/css/dashboard.css")).toExternalForm());
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
                    showError("Failed to load versions: " + ex.getMessage());
                });
            }
        });
    }

    private List<WorkspaceSnapshotSelection> loadSnapshotSelections(FileDAO fileDAO, ObjectId fileId) {
        Map<Integer, String> commitMessageBySnapshotId = new HashMap<>();
        for (Document commit : fileDAO.findCommitsByFileId(fileId)) {
            Integer snapshotId = commit.get("snapshotId", Integer.class);
            if (snapshotId == null) {
                continue;
            }
            String message = commit.getString("message");
            String normalized = (message == null || message.isBlank()) ? "No commit message" : message.trim();
            commitMessageBySnapshotId.putIfAbsent(snapshotId, normalized);
        }

        List<WorkspaceSnapshotSelection> snapshots = new ArrayList<>();
        for (Document snapshot : fileDAO.findSnapshotsByFileId(fileId)) {
            Integer snapshotId = snapshot.get("snapshotId", Integer.class);
            if (snapshotId == null) {
                continue;
            }

            String commitMessage = commitMessageBySnapshotId.getOrDefault(snapshotId, "No commit message");
            if (commitMessage.length() > 72) {
                commitMessage = commitMessage.substring(0, 69) + "...";
            }
            String label = "v" + snapshotId + " - " + commitMessage;
            snapshots.add(new WorkspaceSnapshotSelection(snapshotId, label));
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
        inviteForm.setStyle("-fx-padding: 16; -fx-background-color: rgba(15,30,60,0.6); "
                + "-fx-border-color: rgba(100,160,240,0.3); -fx-border-radius: 12; -fx-background-radius: 12;");

        TextField collaboratorField = new TextField();
        collaboratorField.setPromptText("Enter collaborator username");
        collaboratorField.setStyle("-fx-padding: 8; -fx-font-size: 12; "
                + "-fx-background-color: rgba(10,20,40,0.8); -fx-text-fill: #e7f0ff; "
                + "-fx-border-color: rgba(100,160,240,0.4);");

        Button inviteBtn = new Button("Invite");
        inviteBtn.setStyle("-fx-padding: 8 16; -fx-font-size: 12; -fx-text-fill: white; "
                + "-fx-background-color: linear-gradient(to bottom, #60a5fa, #3b82f6);");
        inviteBtn.setOnAction(e -> {
            String username = collaboratorField.getText() == null ? "" : collaboratorField.getText().trim();
            if (username.isEmpty()) {
                showError("Enter a username to invite.");
                return;
            }
            try {
                Optional<UserModel> targetUser = workspaceService.loadAllUsers().stream()
                        .filter(user -> user.username() != null && user.username().equalsIgnoreCase(username))
                        .findFirst();

                if (targetUser.isEmpty() || targetUser.get().userId() == null) {
                    showError("User not found: " + username);
                    return;
                }

                if (targetUser.get().userId().equals(currentUserId)) {
                    showError("Owner is already in the workspace.");
                    return;
                }

                workspaceService.addCollaborator(workspaceId, targetUser.get().userId());
                reloadWorkspace();
                showInfo("Collaborator added: " + targetUser.get().username());
                collaboratorField.clear();
            } catch (Exception ex) {
                showError("Failed to invite collaborator: " + ex.getMessage());
            }
        });

        HBox.setHgrow(collaboratorField, Priority.ALWAYS);
        inviteForm.getChildren().addAll(collaboratorField, inviteBtn);

        content.getChildren().addAll(inviteCard, inviteForm);
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
        renameForm.setStyle("-fx-padding: 16; -fx-background-color: rgba(15,30,60,0.6); "
                + "-fx-border-color: rgba(100,160,240,0.3); -fx-border-radius: 12; -fx-background-radius: 12;");

        TextField nameField = new TextField();
        nameField.setPromptText("New workspace name");
        nameField.setText(workspaceNameHeader.getText());
        nameField.setStyle("-fx-padding: 8; -fx-font-size: 12; "
                + "-fx-background-color: rgba(10,20,40,0.8); -fx-text-fill: #e7f0ff; "
                + "-fx-border-color: rgba(100,160,240,0.4);");

        Button updateBtn = new Button("Update");
        updateBtn.setStyle("-fx-padding: 8 16; -fx-font-size: 12; -fx-text-fill: white; "
                + "-fx-background-color: linear-gradient(to bottom, #60a5fa, #3b82f6);");
        updateBtn.setOnAction(e -> {
            String updatedName = nameField.getText() == null ? "" : nameField.getText().trim();
            if (updatedName.isEmpty()) {
                showError("Workspace name cannot be empty.");
                return;
            }
            try {
                workspaceService.updateWorkspaceName(workspaceId, updatedName);
                reloadWorkspace();
                nameField.setText(currentModel.workspaceName());
                showInfo("Workspace renamed successfully");
            } catch (Exception ex) {
                showError("Failed to rename workspace: " + ex.getMessage());
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
            Optional<String> confirm = PopupDialogs.showChoice(
                    resolveOwnerStage(),
                    "Delete Workspace",
                    "Are you sure you want to delete this workspace?\nAll files and history will be permanently removed.",
                    "Confirm action",
                    java.util.Arrays.asList("Delete", "Cancel"),
                    "Cancel",
                    "Delete");
            if (confirm.isPresent() && "Delete".equals(confirm.get())) {
                try {
                    workspaceService.deleteWorkspace(workspaceId);
                    showInfo("Workspace deleted");

                    if (deleteBtn.getScene() != null
                            && deleteBtn.getScene().getWindow() instanceof Stage settingsStage) {
                        settingsStage.close();
                    }
                    Stage workspaceStage = resolveOwnerStage();
                    if (workspaceStage != null) {
                        workspaceStage.close();
                    }
                } catch (Exception ex) {
                    showError("Failed to delete workspace: " + ex.getMessage());
                }
            }
        });

        content.getChildren().addAll(renameCard, renameForm, deleteCard, deleteBtn);
        return content;
    }

    private VBox createGlassMorphicCard(Label title, Label subtitle) {
        VBox card = new VBox(6);
        card.setStyle("-fx-padding: 16; -fx-background-color: rgba(15,30,60,0.5); "
                + "-fx-border-color: rgba(100,160,240,0.25); -fx-border-radius: 12; "
                + "-fx-background-radius: 12;");

        title.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #60a5fa;");
        subtitle.setStyle("-fx-font-size: 12; -fx-text-fill: #a0b5d8;");

        card.getChildren().addAll(title, subtitle);
        return card;
    }

    @FXML
    private void onCommitRequested() {
        if (selectedFile == null || selectedPath == null) {
            showError("Select a file before committing");
            return;
        }

        String commitMessage = PopupDialogs.showTextInput(
                resolveOwnerStage(),
                "Commit Changes",
                "Save this file content as a new commit.",
                "Commit message",
                "Describe your changes",
                "Commit",
                PopupDialogs.Theme.BLUE)
                .orElse(null);

        if (commitMessage != null && !commitMessage.isBlank()) {
            commitSelectedFile(commitMessage);
        }
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

        workspaceRoot = fileSystemService.normalizeWorkspaceRoot(currentModel.workspaceRootPath());
        buildMetadataIndex();
        if (currentBrowsePath == null || !isInsideWorkspace(currentBrowsePath)) {
            currentBrowsePath = workspaceRoot;
        }
        populateCurrentDirectoryTable();
        loadReadmeSection();
        updateContributorsPanel();
        updateLanguagePanel();
        if (selectedPath != null && !Files.exists(selectedPath)) {
            selectedPath = null;
            selectedFile = null;
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
            return;
        }

        selectedPath = row.path();
        selectedFile = row.file();
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

        SwingNode swingNode = new SwingNode();
        AtomicReference<RSyntaxTextArea> editorRef = new AtomicReference<>();

        Button saveButton = new Button("Commit Changes");
        saveButton.getStyleClass().add("workspace-editor-commit-button");
        saveButton.setDisable(true);

        SwingUtilities.invokeLater(() -> {
            RSyntaxTextArea editor = new RSyntaxTextArea(initialContent);
            editor.setCodeFoldingEnabled(true);
            editor.setAntiAliasingEnabled(true);
            editor.setSyntaxEditingStyle(detectSyntaxStyle(displayName));
            applyEditorTheme(editor, displayName);

            editor.getDocument().addDocumentListener(new DocumentListener() {
                private void onTextChanged() {
                    boolean changed = !editor.getText().equals(initialContent);
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
                    showError("Editor is still loading. Please try again.");
                    return;
                }

                String commitMessage = PopupDialogs.showTextInput(
                        stage,
                        "Commit Changes",
                        "Save editor updates to version history.",
                        "Commit message",
                        "Describe your changes",
                        "Commit",
                        PopupDialogs.Theme.BLUE)
                        .orElse(null);
                if (commitMessage == null) {
                    return;
                }

                String content = editor.getText();
                fileSystemService.writeFile(filePath, content);

                Path parent = filePath.getParent();
                String relativeFolder = (parent == null || parent.equals(workspaceRoot))
                        ? "root"
                        : resolveRelativePath(parent);
                workspaceService.ensureFileMetadata(
                        workspaceId,
                        currentUserId,
                        relativeFolder,
                        filePath.getFileName().toString());

                String relativeFilePath = resolveRelativePath(filePath);
                FileItemModel fileMetadata = metadataByRelativePath.get(relativeFilePath);
                if (fileMetadata == null) {
                    reloadWorkspace();
                    fileMetadata = metadataByRelativePath.get(relativeFilePath);
                }
                if (fileMetadata == null) {
                    showError("Unable to locate file metadata for commit");
                    return;
                }

                fileService.commit(fileMetadata.fileId(), currentUserId, commitMessage, content);
                reloadWorkspace();
                stage.close();
                populateCurrentDirectoryTable();
            } catch (Exception e) {
                showError("Save failed: " + e.getMessage());
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
                .add(Objects.requireNonNull(getClass().getResource("/css/dashboard.css")).toExternalForm());
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
                    .getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/monokai.xml"));
            theme.apply(editor);
        } catch (Exception ignored) {
            editor.setBackground(java.awt.Color.decode("#0b0b0b"));
            editor.setForeground(java.awt.Color.decode("#ffffff"));
            editor.setCaretColor(java.awt.Color.decode("#00ff88"));
        }

        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            editor.setForeground(java.awt.Color.decode("#ffffff"));
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

    private FileDAO createWorkspaceFileDao() {
        String dbName = System.getenv("MONGODB_DB");
        if (dbName == null || dbName.isBlank()) {
            dbName = "DVCS";
        }
        return new FileDAO(MongoConnection.getDatabase(dbName));
    }

}
