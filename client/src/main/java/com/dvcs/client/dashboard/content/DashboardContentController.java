package com.dvcs.client.dashboard.content;

import com.dvcs.client.dashboard.data.WorkspaceDetails;
import com.dvcs.client.dashboard.data.WorkspaceSummary;
import com.dvcs.client.auth.db.MongoConnection;
import com.dvcs.client.dashboard.service.WorkspaceService;
import com.dvcs.client.dashboard.workspace.WorkspaceCardController;
import com.dvcs.client.ui.PopupDialogs;
import com.dvcs.client.workspacepage.controller.WorkspaceController;
import com.mongodb.client.MongoDatabase;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.geometry.Pos;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.types.ObjectId;

public class DashboardContentController {

    private static final double MY_WORKSPACE_CARD_HEIGHT = 160;
    private static final double COLLAB_CARD_HEIGHT = 160;
    private static final double RIGHT_PANEL_MIN_WIDTH = 420;
    private static final String ICON_FOLDER_URL = "https://img.icons8.com/fluency-systems-filled/96/00ff88/folder-invoices.png";

    @FXML
    private HBox mainCard;

    @FXML
    private AnchorPane overlayRoot;

    @FXML
    private Button newWorkspaceButton;

    private VBox collabList;
    private GridPane myGrid;
    private VBox rightPanel;
    private Label storageLabel;
    private javafx.scene.control.ProgressBar storageProgressBar;
    private VBox activityList;

    private WorkspaceService workspaceService;
    private ObjectId currentUserId;
    private String currentUsername;

    private com.dvcs.client.workspacepage.service.WorkspaceService workspacePageService;
    private com.dvcs.client.workspacepage.service.FileService workspaceFileService;

    private final List<WorkspaceSummary> ownedWorkspaces = new ArrayList<>();
    private Set<ObjectId> highlightedWorkspaceIds = Set.of();
    private boolean initialCardGeometryLocked;

    @FXML
    private void initialize() {
        mainCard.getChildren().setAll(buildWorkspaceSection(), buildRightPanel());
        if (!mainCard.getChildren().isEmpty()) {
            HBox.setHgrow(mainCard.getChildren().getFirst(), Priority.ALWAYS);
            if (mainCard.getChildren().size() > 1) {
                HBox.setHgrow(mainCard.getChildren().get(1), Priority.NEVER);
            }
        }

        // Size the overlay card relative to the available window area.
        // JavaFX doesn't support percentage sizing in FXML, so we bind explicitly.
        overlayRoot.widthProperty().addListener((obs, oldV, newV) -> layoutCard());
        overlayRoot.heightProperty().addListener((obs, oldV, newV) -> layoutCard());

        if (newWorkspaceButton != null) {
            newWorkspaceButton.setFocusTraversable(false);
            newWorkspaceButton.setOnAction(e -> onNewWorkspace());
        }

        layoutCard();
    }

    private Node buildRightPanel() {
        VBox panel = new VBox(24);
        panel.setMinWidth(RIGHT_PANEL_MIN_WIDTH);
        panel.setPrefWidth(RIGHT_PANEL_MIN_WIDTH);
        panel.setMaxWidth(RIGHT_PANEL_MIN_WIDTH);
        panel.getStyleClass().add("dashboard-right-panel");
        panel.setPadding(new javafx.geometry.Insets(0, 0, 0, 10));

        // 1. Profile & Storage Card
        VBox profileCard = new VBox(20);
        profileCard.getStyleClass().addAll("glass", "stats-card");
        profileCard.setPadding(new javafx.geometry.Insets(20));

        Label profileTitle = new Label("Account Insights");
        profileTitle.getStyleClass().add("stats-title");
        
        VBox storageBox = new VBox(10);
        Label sLabel = new Label("Storage Quota");
        sLabel.getStyleClass().add("stats-label");
        
        this.storageProgressBar = new javafx.scene.control.ProgressBar(0);
        storageProgressBar.setMaxWidth(Double.MAX_VALUE);
        storageProgressBar.getStyleClass().add("storage-progress");
        
        this.storageLabel = new Label("0 / 5 GB used");
        storageLabel.getStyleClass().add("stats-sublabel");
        
        storageBox.getChildren().addAll(sLabel, storageProgressBar, storageLabel);
        profileCard.getChildren().addAll(profileTitle, storageBox);

        // 2. Recent Activity Section
        VBox activityBox = new VBox(14);
        VBox.setVgrow(activityBox, Priority.ALWAYS);
        Label activityTitle = new Label("Recent Activity");
        activityTitle.getStyleClass().add("section-title");
        
        this.activityList = new VBox(12);
        activityList.getStyleClass().add("activity-list");
        
        activityBox.getChildren().addAll(activityTitle, activityList);

        panel.getChildren().addAll(profileCard, activityBox);
        this.rightPanel = panel;
        return panel;
    }

    public void configure(WorkspaceService workspaceService, ObjectId currentUserId, String currentUsername) {
        this.workspaceService = workspaceService;
        this.currentUserId = currentUserId;
        this.currentUsername = currentUsername;
        reloadWorkspaces();
    }

    public void performSearch(String query) {
        if (workspaceService == null || currentUserId == null) {
            return;
        }

        String normalizedQuery = query == null ? "" : query.trim();
        highlightedWorkspaceIds = workspaceService.searchWorkspaceIdsByQuery(currentUserId, normalizedQuery);
        renderWorkspaceCards();
    }

    private void layoutCard() {
        if (overlayRoot == null || mainCard == null)
            return;

        double availableW = overlayRoot.getWidth();
        double availableH = overlayRoot.getHeight();
        if (availableW <= 0 || availableH <= 0)
            return;

        if (initialCardGeometryLocked) {
            return;
        }

        // Dominant card with comfortable glass margins.
        double cardW = Math.max(1080, availableW * 0.92);
        double cardH = Math.max(660, availableH * 0.80);

        double lockedW = Math.min(cardW, availableW - 24);
        double lockedH = Math.min(cardH, availableH - 24);

        // Lock initial geometry so the dashboard doesn't visibly resize during
        // startup pulses.
        mainCard.setMinWidth(lockedW);
        mainCard.setPrefWidth(lockedW);
        mainCard.setMaxWidth(lockedW);
        mainCard.setMinHeight(lockedH);
        mainCard.setPrefHeight(lockedH);
        mainCard.setMaxHeight(lockedH);

        // Center card and keep the hero/title zone visually detached from the glass
        // card.
        AnchorPane.setLeftAnchor(mainCard, (availableW - lockedW) / 2.0);
        AnchorPane.setTopAnchor(mainCard, Math.max(158, 300 - (lockedH * 0.22)));
        initialCardGeometryLocked = true;
    }

    private Node buildWorkspaceSection() {
        VBox workspaceContent = new VBox(22);
        workspaceContent.getStyleClass().add("workspace-section");

        VBox workspaceGlass = new VBox(22);
        workspaceGlass.getStyleClass().addAll("glass", "workspace-glass");

        // 3-column grid:
        // [ My Workspace | My Workspace | Collaborative Workspace ]
        GridPane workspaceRegion = new GridPane();
        workspaceRegion.getStyleClass().add("workspace-region");
        workspaceRegion.setHgap(30);
        workspaceRegion.setVgap(30);

        ColumnConstraints c0 = new ColumnConstraints();
        ColumnConstraints c1 = new ColumnConstraints();
        ColumnConstraints c2 = new ColumnConstraints();
        c0.setPercentWidth(40.0);
        c1.setPercentWidth(26.6667);
        c2.setPercentWidth(33.3333);
        workspaceRegion.getColumnConstraints().setAll(c0, c1, c2);

        VBox myWorkspaceBox = new VBox(14);
        Label myTitle = new Label("My Workspace");
        myTitle.getStyleClass().add("section-title");

        GridPane myGrid = new GridPane();
        myGrid.getStyleClass().add("workspace-gridpane");
        myGrid.setHgap(30);
        myGrid.setVgap(30);

        myGrid.add(createWorkspaceCard("Design Docs"), 0, 0);
        myGrid.add(createWorkspaceCard("API Specs"), 1, 0);
        myGrid.add(createWorkspaceCard("Sprint Notes"), 0, 1);
        myGrid.add(createWorkspaceCard("Release Assets"), 1, 1);

        myWorkspaceBox.getChildren().addAll(myTitle, myGrid);

        VBox collabBox = new VBox(14);
        Label collabTitle = new Label("Collaborative Workspace");
        collabTitle.getStyleClass().add("section-title");

        VBox collabList = new VBox(25);
        collabList.getStyleClass().add("collab-list");
        this.myGrid = myGrid;
        this.collabList = collabList;

        collabBox.getChildren().addAll(collabTitle, collabList);

        workspaceRegion.add(myWorkspaceBox, 0, 0, 2, 1);
        workspaceRegion.add(collabBox, 2, 0);

        workspaceGlass.getChildren().addAll(workspaceRegion);
        workspaceContent.getChildren().addAll(workspaceGlass);

        return workspaceContent;
    }

    private void onNewWorkspace() {
        if (workspaceService == null || currentUserId == null) {
            showError("Workspace service is not ready.");
            return;
        }

        Window owner = currentWindow();
        String workspaceName = PopupDialogs.showTextInput(
                owner,
                "New Workspace",
                "Create a workspace in your selected local directory.",
                "Workspace name",
                "Enter workspace name",
                "Create")
                .orElse(null);
        if (workspaceName == null || workspaceName.isBlank()) {
            return;
        }

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Base Directory");
        File selectedDirectory = directoryChooser.showDialog(currentWindow());
        if (selectedDirectory == null) {
            return;
        }

        try {
            WorkspaceSummary created = workspaceService.createWorkspace(
                    currentUserId,
                    workspaceName.trim(),
                    selectedDirectory.toPath());
            ownedWorkspaces.add(created);
            renderWorkspaceCards();
            showInfo("Workspace created successfully.");
        } catch (Exception e) {
            showError("Failed to create workspace: " + e.getMessage());
        }
    }

    private void onImportFiles() {
        if (workspaceService == null || currentUserId == null) {
            showError("Workspace service is not ready.");
            return;
        }

        if (ownedWorkspaces.isEmpty()) {
            showInfo("Create a workspace before importing files.");
            return;
        }

        Map<String, WorkspaceSummary> workspaceByLabel = new LinkedHashMap<>();
        for (WorkspaceSummary workspace : ownedWorkspaces) {
            String label = workspace.displayName() + " [" + workspace.workspaceId().toHexString().substring(0, 6) + "]";
            workspaceByLabel.put(label, workspace);
        }

        List<String> workspaceLabels = new ArrayList<>(workspaceByLabel.keySet());
        Window owner = currentWindow();
        String selectedWorkspaceLabel = PopupDialogs.showChoice(
                owner,
                "Import Files",
                "Step 1 of 3",
                "Select workspace",
                workspaceLabels,
                workspaceLabels.getFirst(),
                "Continue")
                .orElse(null);
        if (selectedWorkspaceLabel == null) {
            return;
        }

        WorkspaceSummary selectedWorkspace = workspaceByLabel.get(selectedWorkspaceLabel);
        if (selectedWorkspace == null) {
            showError("Invalid workspace selection.");
            return;
        }

        WorkspaceDetails details = workspaceService.loadWorkspaceDetails(selectedWorkspace.workspaceId());
        List<String> folderChoices = new ArrayList<>(details.folders());
        if (!folderChoices.contains("root")) {
            folderChoices.addFirst("root");
        }
        folderChoices.add("Create new folder...");

        String selectedFolder = PopupDialogs.showChoice(
                owner,
                "Import Files",
                "Step 2 of 3",
                "Select folder inside workspace",
                folderChoices,
                folderChoices.getFirst(),
                "Continue")
                .orElse(null);
        if (selectedFolder == null) {
            return;
        }

        if ("Create new folder...".equals(selectedFolder)) {
            String newFolder = PopupDialogs.showTextInput(
                    owner,
                    "Import Files",
                    "Step 2 of 3",
                    "New folder name",
                    "Enter folder name",
                    "Create Folder")
                    .orElse(null);
            if (newFolder == null || newFolder.isBlank()) {
                return;
            }
            selectedFolder = newFolder.trim();
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Step 3: Select Files");
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(currentWindow());
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            return;
        }

        try {
            int imported = workspaceService.importFiles(
                    currentUserId,
                    selectedWorkspace.workspaceId(),
                    selectedFolder,
                    selectedFiles);
            showInfo(imported + " file(s) imported successfully.");
        } catch (Exception e) {
            showError("Import failed: " + e.getMessage());
        }
    }

    private void reloadWorkspaces() {
        if (workspaceService == null || currentUserId == null) {
            return;
        }

        ownedWorkspaces.clear();
        ownedWorkspaces.addAll(workspaceService.loadOwnedWorkspaces(currentUserId));
        highlightedWorkspaceIds = Set.of();
        renderWorkspaceCards();
        updateDashboardStats();
    }

    private void updateDashboardStats() {
        if (workspaceService == null || currentUserId == null) return;

        // Update Stats & Progress
        org.bson.Document stats = workspaceService.loadDashboardStats(currentUserId);
        long used = stats.getLong("usedStorage");
        long quota = stats.getLong("storageQuota");
        double progress = (double) used / quota;
        
        storageProgressBar.setProgress(progress);
        storageLabel.setText(String.format("%.1f MB / %.1f GB", used / (1024 * 1024.0), quota / (1024 * 1024 * 1024.0)));

        // Update Activity
        activityList.getChildren().clear();
        List<com.dvcs.client.core.model.AuditLog> logs = workspaceService.loadRecentActivity(currentUserId, 5);
        if (logs.isEmpty()) {
            Label empty = new Label("No recent activities.");
            empty.getStyleClass().add("activity-empty-text");
            activityList.getChildren().add(empty);
        } else {
            for (com.dvcs.client.core.model.AuditLog log : logs) {
                activityList.getChildren().add(createActivityItem(log));
            }
        }
    }

    private Node createActivityItem(com.dvcs.client.core.model.AuditLog log) {
        HBox item = new HBox(12);
        item.getStyleClass().add("activity-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new javafx.geometry.Insets(10, 14, 10, 14));

        VBox content = new VBox(4);
        Label actionLabel = new Label(log.action().toUpperCase());
        actionLabel.getStyleClass().add("activity-action");
        
        Label resourceLabel = new Label(log.entityAffected() + ": " + log.entityId());
        resourceLabel.getStyleClass().add("activity-resource");
        
        content.getChildren().addAll(actionLabel, resourceLabel);
        item.getChildren().add(content);
        
        return item;
    }

    private void renderWorkspaceCards() {
        if (myGrid == null || collabList == null) {
            return;
        }

        myGrid.getChildren().clear();
        collabList.getChildren().clear();

        List<WorkspaceSummary> sorted = new ArrayList<>(ownedWorkspaces);
        sorted.sort((left, right) -> {
            boolean leftHighlighted = highlightedWorkspaceIds.contains(left.workspaceId());
            boolean rightHighlighted = highlightedWorkspaceIds.contains(right.workspaceId());
            if (leftHighlighted != rightHighlighted) {
                return leftHighlighted ? -1 : 1;
            }
            return Comparator.comparing(WorkspaceSummary::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .compare(left, right);
        });

        int row = 0;
        int column = 0;
        for (WorkspaceSummary workspace : sorted) {
            Node node = createWorkspaceCard(workspace);
            if (highlightedWorkspaceIds.contains(workspace.workspaceId())) {
                node.getStyleClass().add("workspace-card-highlighted");
            }
            myGrid.add(node, column, row);
            column++;
            if (column > 1) {
                column = 0;
                row++;
            }
        }

        if (sorted.isEmpty()) {
            Node emptyMy = createEmptyWorkspacePlaceholder("No workspace here", ICON_FOLDER_URL);
            myGrid.add(emptyMy, 0, 0, 2, 1);
        }

        List<WorkspaceSummary> collaborative = workspaceService == null || currentUserId == null
                ? List.of()
                : workspaceService.loadCollaborativeWorkspaces(currentUserId);
        if (collaborative.isEmpty()) {
            collabList.getChildren()
                    .add(createEmptyWorkspacePlaceholder("No workspace here", ICON_FOLDER_URL));
        } else {
            for (WorkspaceSummary workspace : collaborative.stream().limit(4).toList()) {
                Node node = createCollaborativeRow(workspace.displayName());
                if (node instanceof javafx.scene.layout.Region region) {
                    region.setPrefHeight(COLLAB_CARD_HEIGHT);
                    region.setMinHeight(COLLAB_CARD_HEIGHT);
                }
                node.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> openWorkspaceDetails(workspace));
                collabList.getChildren().add(node);
            }
        }
    }

    private Node createWorkspaceCard(WorkspaceSummary workspace) {
        Node node = loadFxmlNode("/fxml/WorkspaceCard.fxml");
        if (node instanceof javafx.scene.layout.Region region) {
            region.setPrefHeight(MY_WORKSPACE_CARD_HEIGHT);
            region.setMinHeight(MY_WORKSPACE_CARD_HEIGHT);
        }
        Object controller = node.getProperties().get("fx:controller");
        if (controller instanceof WorkspaceCardController cardController) {
            cardController.setTitle(workspace.displayName());
        }
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> openWorkspaceDetails(workspace));
        return node;
    }

    private Node createEmptyWorkspacePlaceholder(String message, String imagePath) {
        VBox box = new VBox(10);
        box.getStyleClass().addAll("workspace-card", "workspace-empty-card");
        box.setMinHeight(170);
        box.setPrefHeight(170);

        if (imagePath != null && !imagePath.isBlank()) {
            Image image = null;
            if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                image = new Image(imagePath, true);
            } else {
                URL iconUrl = DashboardContentController.class.getResource(imagePath);
                if (iconUrl != null) {
                    image = new Image(iconUrl.toExternalForm(), true);
                }
            }
            if (image != null) {
                ImageView icon = new ImageView(image);
                icon.setFitWidth(34);
                icon.setFitHeight(34);
                icon.setPreserveRatio(true);
                box.getChildren().add(icon);
            }
        }

        Label label = new Label(message);
        label.getStyleClass().add("workspace-empty-label");
        box.getChildren().add(label);
        return box;
    }

    private void openWorkspaceDetails(WorkspaceSummary workspace) {
        if (workspaceService == null || workspace == null) {
            return;
        }
        openWorkspacePage(workspace.workspaceId(), workspace.displayName(), null, null);
    }

    public void openWorkspaceDetailsForSearch(ObjectId workspaceId, String selectedFolder, String selectedFile) {
        if (workspaceService == null || workspaceId == null) {
            return;
        }

        try {
            WorkspaceSummary summary = ownedWorkspaces.stream()
                    .filter(w -> w.workspaceId().equals(workspaceId))
                    .findFirst()
                    .orElse(null);
            String title = summary == null ? "Workspace" : summary.displayName();
            openWorkspacePage(workspaceId, title, selectedFolder, selectedFile);
        } catch (Exception e) {
            showError("Failed to open workspace: " + summarizeException(e));
        }
    }

    private void openWorkspacePage(ObjectId workspaceId, String title, String selectedFolder, String selectedFile) {
        try {
            URL url = DashboardContentController.class.getResource("/fxml/WorkspacePage.fxml");
            if (url == null) {
                throw new IllegalStateException("FXML '/fxml/WorkspacePage.fxml' not found on classpath");
            }

            ensureWorkspacePageServices();

            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            WorkspaceController controller = loader.getController();
            controller.configure(
                    workspacePageService,
                    workspaceFileService,
                    workspaceId,
                    currentUserId,
                    selectedFolder,
                    selectedFile);

            Stage stage = new Stage();
            stage.setTitle(title == null || title.isBlank() ? "Workspace" : title);
            Window owner = currentWindow();
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.setFullScreenExitHint("");
            stage.setFullScreen(true);
            stage.show();
        } catch (Exception e) {
            showError("Failed to open workspace: " + summarizeException(e));
        }
    }

    private static String summarizeException(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }

        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return root.getClass().getSimpleName() + ": " + message;
    }

    private void ensureWorkspacePageServices() {
        if (workspacePageService != null && workspaceFileService != null) {
            return;
        }

        String dbName = System.getenv("MONGODB_DB");
        if (dbName == null || dbName.isBlank()) {
            dbName = "DVCS";
        }

        MongoDatabase database = MongoConnection.getDatabase(dbName);
        com.dvcs.client.workspacepage.dao.WorkspaceDAO workspaceDAO = new com.dvcs.client.workspacepage.dao.WorkspaceDAO(
                database);
        com.dvcs.client.workspacepage.dao.FileDAO fileDAO = new com.dvcs.client.workspacepage.dao.FileDAO(database);

        com.dvcs.client.workspacepage.service.CommitService commitService = new com.dvcs.client.workspacepage.service.CommitService(
                fileDAO);
        
        com.dvcs.client.core.dao.AuditLogDao auditLogDao = new com.dvcs.client.core.dao.AuditLogDao(database);

        this.workspaceFileService = new com.dvcs.client.workspacepage.service.FileService(fileDAO, commitService, auditLogDao);
        this.workspacePageService = new com.dvcs.client.workspacepage.service.WorkspaceService(
                workspaceDAO,
                fileDAO,
                commitService,
                auditLogDao);
    }

    private Node createCollaborativeRow(String title) {
        Node node = loadFxmlNode("/fxml/WorkspaceCard.fxml");
        node.getStyleClass().add("collab-row");
        Object controller = node.getProperties().get("fx:controller");
        if (controller instanceof WorkspaceCardController cardController) {
            cardController.setTitle(title);
        }
        return node;
    }

    private Node loadVersionControlPanel() {
        javafx.scene.layout.StackPane panel = new javafx.scene.layout.StackPane();
        panel.getStyleClass().addAll("glass-analytics", "version-control-panel");
        panel.setTranslateY(-24);
        panel.setPadding(new javafx.geometry.Insets(28, 24, 24, 24));
        panel.setMinWidth(RIGHT_PANEL_MIN_WIDTH);
        panel.setPrefWidth(RIGHT_PANEL_MIN_WIDTH);
        panel.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(28);
        content.setAlignment(Pos.TOP_CENTER);

        Label heading = new Label("Control Your Code.\nCommand Your Workflow.");
        heading.getStyleClass().add("version-control-heading");
        heading.setWrapText(true);

        ImageView imageView = new ImageView();
        URL imageUrl = DashboardContentController.class.getResource("/images/version_control.png");
        if (imageUrl != null) {
            imageView.setImage(new Image(imageUrl.toExternalForm(), true));
        }
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(260);
        imageView.setFitHeight(260);

        content.getChildren().addAll(heading, imageView);
        panel.getChildren().add(content);
        return panel;
    }

    private Node createWorkspaceCard(String title) {
        Node node = loadFxmlNode("/fxml/WorkspaceCard.fxml");
        if (node instanceof javafx.scene.layout.Region region) {
            region.setPrefHeight(MY_WORKSPACE_CARD_HEIGHT);
            region.setMinHeight(MY_WORKSPACE_CARD_HEIGHT);
        }
        Object controller = node.getProperties().get("fx:controller");
        if (controller instanceof WorkspaceCardController cardController) {
            cardController.setTitle(title);
        }
        return node;
    }

    private Window currentWindow() {
        if (mainCard == null || mainCard.getScene() == null) {
            return null;
        }
        return mainCard.getScene().getWindow();
    }

    private void showInfo(String message) {
        PopupDialogs.showInfo(currentWindow(), "Workspace", message);
    }

    private void showError(String message) {
        PopupDialogs.showError(currentWindow(), "Operation Failed", message);
    }

    private static Node loadFxmlNode(String resource) {
        URL url = DashboardContentController.class.getResource(resource);
        if (url == null) {
            throw new IllegalStateException("FXML '" + resource + "' not found on classpath");
        }
        FXMLLoader loader = new FXMLLoader(url);
        try {
            Node node = loader.load();
            node.getProperties().put("fx:controller", loader.getController());
            return node;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + resource, e);
        }
    }
}
