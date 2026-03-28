package com.dvcs.client.dashboard.content;

import com.dvcs.client.dashboard.analytics.AnalyticsPanelController;
import com.dvcs.client.dashboard.data.WorkspaceDetails;
import com.dvcs.client.dashboard.data.WorkspaceSummary;
import com.dvcs.client.auth.db.MongoConnection;
import com.dvcs.client.dashboard.service.WorkspaceService;
import com.dvcs.client.dashboard.workspace.WorkspaceCardController;
import com.dvcs.client.workspacepage.controller.WorkspaceController;
import com.mongodb.client.MongoDatabase;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
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
import java.util.Optional;
import java.util.Set;
import org.bson.types.ObjectId;

public class DashboardContentController {

    private static final double MY_WORKSPACE_CARD_HEIGHT = 160;
    private static final double COLLAB_CARD_HEIGHT = 160;
    private static final double ANALYTICS_PANEL_MIN_WIDTH = 420;

    @FXML
    private HBox mainCard;

    @FXML
    private AnchorPane overlayRoot;

    @FXML
    private Button newWorkspaceButton;

    @FXML
    private Button importFilesButton;

    private GridPane myGrid;
    private VBox collabList;

    private WorkspaceService workspaceService;
    private ObjectId currentUserId;
    private String currentUsername;

    private com.dvcs.client.workspacepage.service.WorkspaceService workspacePageService;
    private com.dvcs.client.workspacepage.service.FileService workspaceFileService;

    private final List<WorkspaceSummary> ownedWorkspaces = new ArrayList<>();
    private Set<ObjectId> highlightedWorkspaceIds = Set.of();

    @FXML
    private void initialize() {
        mainCard.getChildren().setAll(buildWorkspaceSection(), loadAnalyticsPanel());
        if (!mainCard.getChildren().isEmpty()) {
            HBox.setHgrow(mainCard.getChildren().getFirst(), Priority.ALWAYS);
            if (mainCard.getChildren().size() > 1) {
                HBox.setHgrow(mainCard.getChildren().get(1), Priority.ALWAYS);
            }
        }

        // Size the overlay card relative to the available window area.
        // JavaFX doesn't support percentage sizing in FXML, so we bind explicitly.
        overlayRoot.widthProperty().addListener((obs, oldV, newV) -> layoutCard());
        overlayRoot.heightProperty().addListener((obs, oldV, newV) -> layoutCard());

        if (newWorkspaceButton != null) {
            newWorkspaceButton.setOnAction(e -> onNewWorkspace());
        }
        if (importFilesButton != null) {
            importFilesButton.setOnAction(e -> onImportFiles());
        }

        layoutCard();
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

        // Dominant card with comfortable glass margins.
        double cardW = Math.max(1080, availableW * 0.92);
        double cardH = Math.max(660, availableH * 0.80);

        mainCard.setPrefWidth(Math.min(cardW, availableW - 24));
        mainCard.setPrefHeight(Math.min(cardH, availableH - 24));

        // Center card and keep the hero/title zone visually detached from the glass
        // card.
        AnchorPane.setLeftAnchor(mainCard, (availableW - mainCard.getPrefWidth()) / 2.0);
        AnchorPane.setTopAnchor(mainCard, Math.max(158, 300 - (mainCard.getPrefHeight() * 0.22)));
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

        ScrollPane scrollPane = new ScrollPane(workspaceContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("workspace-scroll");

        return scrollPane;
    }

    private void onNewWorkspace() {
        if (workspaceService == null || currentUserId == null) {
            showError("Workspace service is not ready.");
            return;
        }

        TextInputDialog nameDialog = new TextInputDialog();
        nameDialog.setTitle("New Workspace");
        nameDialog.setHeaderText("Create Workspace");
        nameDialog.setContentText("Workspace name:");

        Optional<String> nameResult = nameDialog.showAndWait();
        if (nameResult.isEmpty() || nameResult.get().isBlank()) {
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
                    nameResult.get().trim(),
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
        ChoiceDialog<String> workspaceChoice = new ChoiceDialog<>(workspaceLabels.getFirst(), workspaceLabels);
        workspaceChoice.setTitle("Import Files");
        workspaceChoice.setHeaderText("Step 1: Select Workspace");
        workspaceChoice.setContentText("Workspace:");

        Optional<String> workspaceSelection = workspaceChoice.showAndWait();
        if (workspaceSelection.isEmpty()) {
            return;
        }

        WorkspaceSummary selectedWorkspace = workspaceByLabel.get(workspaceSelection.get());
        if (selectedWorkspace == null) {
            showError("Invalid workspace selection.");
            return;
        }

        TextInputDialog folderDialog = new TextInputDialog("root");
        WorkspaceDetails details = workspaceService.loadWorkspaceDetails(selectedWorkspace.workspaceId());
        List<String> folderChoices = new ArrayList<>(details.folders());
        if (!folderChoices.contains("root")) {
            folderChoices.addFirst("root");
        }
        folderChoices.add("Create new folder...");

        ChoiceDialog<String> folderChoice = new ChoiceDialog<>(folderChoices.getFirst(), folderChoices);
        folderChoice.setTitle("Import Files");
        folderChoice.setHeaderText("Step 2: Select Folder Inside Workspace");
        folderChoice.setContentText("Folder:");

        Optional<String> folderSelection = folderChoice.showAndWait();
        if (folderSelection.isEmpty()) {
            return;
        }

        String selectedFolder = folderSelection.get();
        if ("Create new folder...".equals(selectedFolder)) {
            folderDialog.setTitle("Import Files");
            folderDialog.setHeaderText("Create Folder");
            folderDialog.setContentText("New folder name:");
            Optional<String> newFolder = folderDialog.showAndWait();
            if (newFolder.isEmpty() || newFolder.get().isBlank()) {
                return;
            }
            selectedFolder = newFolder.get().trim();
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

        List<WorkspaceSummary> collaborative = workspaceService == null || currentUserId == null
                ? List.of()
                : workspaceService.loadCollaborativeWorkspaces(currentUserId);
        if (collaborative.isEmpty()) {
            Label empty = new Label("No collaborative workspaces yet.");
            empty.getStyleClass().add("panel-body");
            collabList.getChildren().add(empty);
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
            showError("Failed to open workspace: " + e.getMessage());
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
            showError("Failed to open workspace: " + e.getMessage());
        }
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
        this.workspaceFileService = new com.dvcs.client.workspacepage.service.FileService(fileDAO, commitService);
        this.workspacePageService = new com.dvcs.client.workspacepage.service.WorkspaceService(
                workspaceDAO,
                fileDAO,
                commitService);
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

    private Node loadAnalyticsPanel() {
        Node node = loadFxmlNode("/fxml/AnalyticsPanel.fxml");
        node.getStyleClass().add("glass-analytics");
        if (node instanceof javafx.scene.layout.Region region) {
            region.setMinWidth(ANALYTICS_PANEL_MIN_WIDTH);
            region.setPrefWidth(ANALYTICS_PANEL_MIN_WIDTH);
            region.setMaxWidth(Double.MAX_VALUE);
        }
        Object controller = node.getProperties().get("fx:controller");
        if (controller instanceof AnalyticsPanelController analytics) {
            analytics.setStats(42, 6);
        }
        return node;
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
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Operation failed");
        alert.showAndWait();
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
