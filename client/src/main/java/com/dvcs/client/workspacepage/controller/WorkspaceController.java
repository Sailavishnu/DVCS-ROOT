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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.geometry.Rectangle2D;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

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

    @FXML
    private void initialize() {
        HBox.setHgrow(leftSection, Priority.ALWAYS);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);
        VBox.setVgrow(fileTable, Priority.ALWAYS);

        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().displayName()));
        lastCommitColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().lastCommitMessage()));
        lastModifiedColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().lastModified()));
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

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Folder");
        dialog.setHeaderText("Create folder in workspace");
        dialog.setContentText("Folder name:");
        Stage owner = resolveOwnerStage();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        applyDialogTheme(dialog);

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
        dialog.setHeaderText("Create file (nested paths supported)");
        dialog.setContentText("File path (e.g. src/file.js):");
        Stage owner = resolveOwnerStage();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        applyDialogTheme(dialog);

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
    private void onSettingsRequested() {
        Stage owner = resolveOwnerStage();
        Stage settingsStage = new Stage();
        settingsStage.setTitle("Workspace Settings");
        settingsStage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            settingsStage.initOwner(owner);
        }

        BorderPane settingsRoot = new BorderPane();
        settingsRoot.getStyleClass().add("workspace-settings-root");

        Label title = new Label("Settings");
        title.getStyleClass().add("workspace-section-title");
        Label subtitle = new Label("Settings panel is ready. Tell me what to add next.");
        subtitle.getStyleClass().add("workspace-meta-label-dark");

        VBox content = new VBox(10, title, subtitle);
        content.getStyleClass().add("workspace-settings-content");
        settingsRoot.setCenter(content);

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double width = Math.max(900, bounds.getWidth() * 0.75);
        double height = Math.max(600, bounds.getHeight() * 0.75);

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
        applyDialogTheme(dialog);

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

                TextInputDialog commitDialog = new TextInputDialog();
                commitDialog.setTitle("Commit Changes");
                commitDialog.setHeaderText("Enter commit message");
                commitDialog.setContentText("Message:");
                commitDialog.initOwner(stage);
                applyDialogTheme(commitDialog);

                String commitMessage = commitDialog.showAndWait()
                        .map(String::trim)
                        .filter(text -> !text.isEmpty())
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
        applyDialogTheme(alert);
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
        applyDialogTheme(alert);
        alert.showAndWait();
    }

    private void applyDialogTheme(Dialog<?> dialog) {
        if (dialog == null || dialog.getDialogPane() == null) {
            return;
        }

        String css = Objects.requireNonNull(getClass().getResource("/css/dashboard.css")).toExternalForm();
        if (!dialog.getDialogPane().getStylesheets().contains(css)) {
            dialog.getDialogPane().getStylesheets().add(css);
        }
        if (!dialog.getDialogPane().getStyleClass().contains("workspace-popup-dialog")) {
            dialog.getDialogPane().getStyleClass().add("workspace-popup-dialog");
        }
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
