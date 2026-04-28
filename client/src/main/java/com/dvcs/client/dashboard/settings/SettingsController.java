package com.dvcs.client.dashboard.settings;

import com.dvcs.client.dashboard.MainLayoutController;
import com.dvcs.client.workspacepage.dao.FileDAO;
import com.dvcs.client.workspacepage.service.CommitService;
import com.dvcs.client.workspacepage.service.FileService;
import com.dvcs.client.dashboard.service.WorkspaceService;
import com.dvcs.client.dashboard.data.WorkspaceSummary;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingNode;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.bson.types.ObjectId;
import org.bson.Document;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.SwingUtilities;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public final class SettingsController {

    @FXML
    private TabPane settingsTabPane;

    // Tab 1: Commit History
    @FXML
    private ComboBox<WorkspaceSummary> commitHistoryWorkspaceCombo;

    @FXML
    private TableView<CommitHistoryRow> commitHistoryTable;

    @FXML
    private TableColumn<CommitHistoryRow, String> fileNameColumn;

    @FXML
    private TableColumn<CommitHistoryRow, String> commitMessageColumn;

    @FXML
    private TableColumn<CommitHistoryRow, String> committedAtColumn;

    @FXML
    private TableColumn<CommitHistoryRow, String> committedByColumn;

    @FXML
    private TableColumn<CommitHistoryRow, Integer> snapshotIdColumn;

    @FXML
    private TableColumn<CommitHistoryRow, Void> commitActionsColumn;

    // Tab 2: File Comparison
    @FXML
    private ComboBox<WorkspaceSummary> comparisonWorkspaceCombo;

    @FXML
    private ComboBox<FileSelectionModel> comparisonFileCombo;

    @FXML
    private ComboBox<SnapshotSelectionModel> leftSnapshotCombo;

    @FXML
    private ComboBox<SnapshotSelectionModel> rightSnapshotCombo;

    @FXML
    private StackPane leftEditorContainer;

    @FXML
    private StackPane rightEditorContainer;

    private MainLayoutController mainLayoutController;
    private FileDAO fileDAO;
    private FileService fileService;
    private CommitService commitService;
    private WorkspaceService workspaceService;
    private ObjectId currentUserId;

    private RSyntaxTextArea leftEditor;
    private RSyntaxTextArea rightEditor;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public SettingsController() {
    }

    @FXML
    public void initialize() {
        setupCommitHistoryTable();
        setupComparisonEditors();
    }

    public void setDependencies(
            MainLayoutController mainLayoutController,
            FileDAO fileDAO,
            FileService fileService,
            CommitService commitService,
            WorkspaceService workspaceService,
            ObjectId currentUserId) {
        this.mainLayoutController = Objects.requireNonNull(mainLayoutController, "mainLayoutController");
        this.fileDAO = Objects.requireNonNull(fileDAO, "fileDAO");
        this.fileService = Objects.requireNonNull(fileService, "fileService");
        this.commitService = Objects.requireNonNull(commitService, "commitService");
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService");
        this.currentUserId = Objects.requireNonNull(currentUserId, "currentUserId");

        loadWorkspaces();
    }

    // ==================== COMMIT HISTORY TAB ====================

    private void setupCommitHistoryTable() {
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        commitMessageColumn.setCellValueFactory(new PropertyValueFactory<>("message"));
        committedAtColumn.setCellValueFactory(new PropertyValueFactory<>("committedAt"));
        committedByColumn.setCellValueFactory(new PropertyValueFactory<>("committedByUser"));
        snapshotIdColumn.setCellValueFactory(new PropertyValueFactory<>("snapshotId"));

        commitActionsColumn.setCellFactory(param -> new TableCell<CommitHistoryRow, Void>() {
            private final Button viewButton = new Button("View");
            private final Button restoreButton = new Button("Restore");
            private final HBox hbox = new HBox(6, viewButton, restoreButton);

            {
                hbox.setPadding(new Insets(4));
                viewButton.setStyle("-fx-font-size: 11;");
                restoreButton.setStyle("-fx-font-size: 11;");
                viewButton.setOnAction(event -> {
                    CommitHistoryRow row = getTableView().getItems().get(getIndex());
                    onViewSnapshot(row);
                });
                restoreButton.setOnAction(event -> {
                    CommitHistoryRow row = getTableView().getItems().get(getIndex());
                    onRestoreSnapshot(row);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : hbox);
            }
        });
    }

    @FXML
    private void onRefreshCommitHistory() {
        WorkspaceSummary workspace = commitHistoryWorkspaceCombo.getValue();
        if (workspace == null) {
            showAlert("Select a workspace first", Alert.AlertType.WARNING);
            return;
        }

        loadCommitHistory(workspace.workspaceId());
    }

    private void loadCommitHistory(ObjectId workspaceId) {
        try {
            List<Document> commitDocs = fileDAO.findCommitsByWorkspace(workspaceId);
            List<CommitHistoryRow> commits = new ArrayList<>();

            for (Document commit : commitDocs) {
                ObjectId fileId = commit.getObjectId("fileId");
                Document fileDoc = fileDAO.findFileById(fileId).orElse(null);
                if (fileDoc == null)
                    continue;

                String fileName = fileDoc.getString("filename");
                String message = commit.getString("message");
                Date committedAt = commit.getDate("committedAt");
                String committedAtStr = committedAt == null ? "" : dateFormatter.format(committedAt.toInstant());
                String committedByUser = "Unknown";
                int snapshotId = commit.getInteger("snapshotId", 0);

                commits.add(new CommitHistoryRow(
                        workspaceId, fileId, fileName, message, committedAtStr, committedByUser, snapshotId));
            }

            ObservableList<CommitHistoryRow> data = FXCollections.observableArrayList(commits);
            data.sort(Comparator.comparing(CommitHistoryRow::committedAt).reversed());
            commitHistoryTable.setItems(data);
        } catch (Exception e) {
            showAlert("Failed to load commit history: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void onViewSnapshot(CommitHistoryRow row) {
        try {
            String content = fileService.loadSnapshotContent(row.fileId(), row.snapshotId());
            showSnapshotPopup(row.fileName(), row.snapshotId(), content);
        } catch (Exception e) {
            showAlert("Failed to load snapshot: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void onRestoreSnapshot(CommitHistoryRow row) {
        try {
            String content = fileService.loadSnapshotContent(row.fileId(), row.snapshotId());
            fileService.restoreSnapshot(row.fileId(), row.snapshotId(), content);
            restoreFileToDisk(row.workspaceId(), row.fileId(), content);
            loadCommitHistory(row.workspaceId());
            showAlert("Snapshot restored successfully!", Alert.AlertType.INFORMATION);
        } catch (Exception e) {
            showAlert("Failed to restore snapshot: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void restoreFileToDisk(ObjectId workspaceId, ObjectId fileId, String content) {
        try {
            String workspaceRoot = workspaceService.resolveWorkspaceRootPath(workspaceId);
            if (workspaceRoot == null || workspaceRoot.isBlank()) return;
            Document fileDoc = fileDAO.findFileById(fileId).orElse(null);
            if (fileDoc == null) return;
            Document pathDoc = fileDoc.get("path", Document.class);
            String relPath = pathDoc != null ? pathDoc.getString("folder") : null;
            if (relPath == null || relPath.isBlank()) return;
            Path diskPath = Path.of(workspaceRoot).resolve(relPath).normalize();
            Files.writeString(diskPath, content == null ? "" : content,
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // disk write is best-effort; DB metadata is already restored
        }
    }

    private void showSnapshotPopup(String fileName, int snapshotId, String content) {
        SwingNode swingNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            RSyntaxTextArea editor = new RSyntaxTextArea(content == null ? "" : content);
            editor.setCodeFoldingEnabled(true);
            editor.setAntiAliasingEnabled(true);
            editor.setSyntaxEditingStyle(detectSyntaxStyle(fileName));
            editor.setEditable(false);
            applyEditorTheme(editor);

            RTextScrollPane scrollPane = new RTextScrollPane(editor);
            scrollPane.setLineNumbersEnabled(true);
            swingNode.setContent(scrollPane);
        });

        VBox popupContent = new VBox(10,
                new Label("File: " + fileName + " (Version #" + snapshotId + ")"),
                swingNode);
        VBox.setVgrow(swingNode, Priority.ALWAYS);
        popupContent.setPadding(new Insets(12));

        showPopupDialog(popupContent, "Snapshot Viewer", 800, 600);
    }

    // ==================== FILE COMPARISON TAB ====================

    private void setupComparisonEditors() {
        SwingNode leftNode = new SwingNode();
        SwingNode rightNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            leftEditor = new RSyntaxTextArea();
            leftEditor.setCodeFoldingEnabled(true);
            leftEditor.setAntiAliasingEnabled(true);
            leftEditor.setEditable(false);

            RTextScrollPane leftScroll = new RTextScrollPane(leftEditor);
            leftScroll.setLineNumbersEnabled(true);
            leftNode.setContent(leftScroll);

            rightEditor = new RSyntaxTextArea();
            rightEditor.setCodeFoldingEnabled(true);
            rightEditor.setAntiAliasingEnabled(true);
            rightEditor.setEditable(false);

            RTextScrollPane rightScroll = new RTextScrollPane(rightEditor);
            rightScroll.setLineNumbersEnabled(true);
            rightNode.setContent(rightScroll);
        });

        leftEditorContainer.getChildren().add(leftNode);
        rightEditorContainer.getChildren().add(rightNode);
    }

    @FXML
    private void onLoadComparisonSnapshots() {
        WorkspaceSummary workspace = comparisonWorkspaceCombo.getValue();
        FileSelectionModel file = comparisonFileCombo.getValue();

        if (workspace == null || file == null) {
            showAlert("Select workspace and file first", Alert.AlertType.WARNING);
            return;
        }

        try {
            List<Document> snapshotDocs = fileDAO.findSnapshotsByFileId(file.fileId());
            List<SnapshotSelectionModel> snapshots = new ArrayList<>();

            for (Document snap : snapshotDocs) {
                int snapshotId = snap.getInteger("snapshotId", 0);
                Date createdAt = snap.getDate("createdAt");
                String label = "Snapshot #" + snapshotId +
                        (createdAt != null ? " (" + dateFormatter.format(createdAt.toInstant()) + ")" : "");
                snapshots.add(new SnapshotSelectionModel(snapshotId, label));
            }

            ObservableList<SnapshotSelectionModel> data = FXCollections.observableArrayList(snapshots);
            leftSnapshotCombo.setItems(data);
            rightSnapshotCombo.setItems(FXCollections.observableArrayList(data));
        } catch (Exception e) {
            showAlert("Failed to load snapshots: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void onLoadComparison() {
        SnapshotSelectionModel leftSnapshot = leftSnapshotCombo.getValue();
        SnapshotSelectionModel rightSnapshot = rightSnapshotCombo.getValue();
        FileSelectionModel file = comparisonFileCombo.getValue();

        if (leftSnapshot == null || rightSnapshot == null || file == null) {
            showAlert("Select both snapshots and file", Alert.AlertType.WARNING);
            return;
        }

        try {
            String leftContent = fileService.loadSnapshotContent(file.fileId(), leftSnapshot.snapshotId());
            String rightContent = fileService.loadSnapshotContent(file.fileId(), rightSnapshot.snapshotId());

            SwingUtilities.invokeLater(() -> {
                if (leftEditor != null) {
                    leftEditor.setText(leftContent == null ? "" : leftContent);
                    leftEditor.setSyntaxEditingStyle(detectSyntaxStyle(file.name()));
                    applyEditorTheme(leftEditor);
                }
                if (rightEditor != null) {
                    rightEditor.setText(rightContent == null ? "" : rightContent);
                    rightEditor.setSyntaxEditingStyle(detectSyntaxStyle(file.name()));
                    applyEditorTheme(rightEditor);
                }
            });

        } catch (Exception e) {
            showAlert("Failed to load comparison: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // ==================== WORKSPACE/FILE LOADING ====================

    private void loadWorkspaces() {
        try {
            @SuppressWarnings("unchecked")
            List<WorkspaceSummary> workspaces = (List<WorkspaceSummary>) (List<?>) workspaceService
                    .loadUserWorkspaces(currentUserId);
            ObservableList<WorkspaceSummary> data = FXCollections.observableArrayList(workspaces);
            data.sort(Comparator.comparing(WorkspaceSummary::workspaceName));

            commitHistoryWorkspaceCombo.setItems(data);
            comparisonWorkspaceCombo.setItems(FXCollections.observableArrayList(data));

            commitHistoryWorkspaceCombo.setOnAction(e -> {
                WorkspaceSummary workspace = commitHistoryWorkspaceCombo.getValue();
                if (workspace != null) {
                    loadCommitHistory(workspace.workspaceId());
                }
            });

            comparisonWorkspaceCombo.setOnAction(e -> {
                WorkspaceSummary workspace = comparisonWorkspaceCombo.getValue();
                if (workspace != null) {
                    loadFilesForWorkspace(workspace.workspaceId());
                }
            });
        } catch (Exception e) {
            showAlert("Failed to load workspaces: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void loadFilesForWorkspace(ObjectId workspaceId) {
        try {
            List<Document> fileDocs = fileDAO.findFilesByWorkspace(workspaceId);
            List<FileSelectionModel> files = new ArrayList<>();

            for (Document fileDoc : fileDocs) {
                ObjectId fileId = fileDoc.getObjectId("_id");
                String fileName = fileDoc.getString("filename");
                files.add(new FileSelectionModel(fileId, fileName));
            }

            ObservableList<FileSelectionModel> data = FXCollections.observableArrayList(files);
            data.sort(Comparator.comparing(FileSelectionModel::name));
            comparisonFileCombo.setItems(data);
        } catch (Exception e) {
            showAlert("Failed to load files: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // ==================== HELPER MODELS ====================

    public record FileSelectionModel(ObjectId fileId, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    public record SnapshotSelectionModel(int snapshotId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    public record CommitHistoryRow(
            ObjectId workspaceId,
            ObjectId fileId,
            String fileName,
            String message,
            String committedAt,
            String committedByUser,
            int snapshotId) {
    }

    // ==================== SYNTAX HIGHLIGHTING ====================

    private String detectSyntaxStyle(String fileName) {
        if (fileName == null)
            return SyntaxConstants.SYNTAX_STYLE_NONE;

        String lower = fileName.toLowerCase();
        if (lower.endsWith(".java"))
            return SyntaxConstants.SYNTAX_STYLE_JAVA;
        if (lower.endsWith(".py"))
            return SyntaxConstants.SYNTAX_STYLE_PYTHON;
        if (lower.endsWith(".js"))
            return SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
        if (lower.endsWith(".json"))
            return SyntaxConstants.SYNTAX_STYLE_JSON;
        if (lower.endsWith(".xml"))
            return SyntaxConstants.SYNTAX_STYLE_XML;
        if (lower.endsWith(".sql"))
            return SyntaxConstants.SYNTAX_STYLE_SQL;
        if (lower.endsWith(".css"))
            return SyntaxConstants.SYNTAX_STYLE_CSS;
        if (lower.endsWith(".html") || lower.endsWith(".htm"))
            return SyntaxConstants.SYNTAX_STYLE_HTML;
        if (lower.endsWith(".md") || lower.endsWith(".markdown"))
            return SyntaxConstants.SYNTAX_STYLE_MARKDOWN;

        return SyntaxConstants.SYNTAX_STYLE_NONE;
    }

    private void applyEditorTheme(RSyntaxTextArea editor) {
        try {
            Theme theme = Theme.load(getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
            theme.apply(editor);
        } catch (Exception e) {
            // Fall back to default theme
        }
    }

    // ==================== UI HELPERS ====================

    private void showPopupDialog(VBox content, String title, int width, int height) {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);

        Scene scene = new Scene(scrollPane, width, height);
        stage.setScene(scene);
        stage.show();
    }

    private void showAlert(String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(type == Alert.AlertType.ERROR ? "Error" : "Info");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
