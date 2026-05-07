package com.dvcs.client.dashboard;

import com.dvcs.client.auth.db.MongoConnection;
import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.dashboard.collaborators.CollaboratorsController;
import com.dvcs.client.dashboard.collaborators.CollaboratorsService;
import com.dvcs.client.dashboard.content.DashboardContentController;
import com.dvcs.client.dashboard.data.dao.CollaborationRequestDao;
import com.dvcs.client.dashboard.data.dao.CommitDao;
import com.dvcs.client.dashboard.data.dao.FileDao;
import com.dvcs.client.dashboard.data.dao.FolderDao;
import com.dvcs.client.dashboard.data.dao.WorkspaceDao;
import com.dvcs.client.dashboard.notification.NotificationController;
import com.dvcs.client.dashboard.profile.ProfileController;
import com.dvcs.client.dashboard.profile.ProfileService;
import com.dvcs.client.dashboard.profile.dao.CommitDAO;
import com.dvcs.client.dashboard.profile.dao.UserDAO;
import com.dvcs.client.dashboard.profile.dao.WorkspaceDAO;
import com.dvcs.client.dashboard.search.SearchController;
import com.dvcs.client.dashboard.search.SearchResultItem;
import com.dvcs.client.dashboard.search.SearchService;
import com.dvcs.client.dashboard.service.NotificationService;
import com.dvcs.client.core.dao.AuditLogDao;
import com.dvcs.client.dashboard.service.WorkspaceService;
import com.mongodb.client.MongoDatabase;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import org.bson.types.ObjectId;

public class MainLayoutController {

    @FXML private Pane contentArea;
    @FXML private Pane drawerOverlay;
    @FXML private VBox drawerSidebar;
    @FXML private VBox drawerNav;
    @FXML private Label drawerUserInitials;
    @FXML private Label drawerUserName;

    // Inline navbar fields
    @FXML private TextField searchField;
    @FXML private Label avatarInitialsLabel;

    private DashboardContentController dashboardContentController;

    private WorkspaceService workspaceService;
    private SearchService searchService;
    private NotificationService notificationService;
    private ProfileService profileService;
    private CollaboratorsService collaboratorsService;

    private String currentUsername;
    private ObjectId currentUserId;

    private boolean drawerOpen = false;
    private static final double DRAWER_WIDTH = 260;

    @FXML
    private void initialize() {
        initializeServices();

        Node dashboardContent = loadFxmlNode("/fxml/DashboardContent.fxml");
        this.dashboardContentController = (DashboardContentController) dashboardContent.getProperties()
                .get("fx:controller");
        contentArea.getChildren().setAll(dashboardContent);
        if (dashboardContent instanceof javafx.scene.layout.Region r) {
            r.prefWidthProperty().bind(contentArea.widthProperty());
            r.prefHeightProperty().bind(contentArea.heightProperty());
        }

        if (drawerSidebar != null) {
            drawerSidebar.setTranslateX(-DRAWER_WIDTH);
        }

        buildDrawerNav();
        bindSession();
    }

    public void setUsername(String username) {
        this.currentUsername = username;
        bindSession();
    }

    private void initializeServices() {
        String dbName = System.getenv("MONGODB_DB");
        if (dbName == null || dbName.isBlank()) dbName = "DVCS";

        MongoDatabase database = MongoConnection.getDatabase(dbName);
        UserRepository userRepository = new UserRepository(database);

        WorkspaceDao workspaceDao = new WorkspaceDao(database);
        FolderDao folderDao = new FolderDao(database);
        FileDao fileDao = new FileDao(database);
        CollaborationRequestDao collaborationRequestDao = new CollaborationRequestDao(database);
        CommitDao commitDao = new CommitDao(database);
        AuditLogDao auditLogDao = new AuditLogDao(database);

        this.workspaceService = new WorkspaceService(workspaceDao, folderDao, fileDao,
                collaborationRequestDao, userRepository, auditLogDao);
        this.searchService = new SearchService(workspaceDao, folderDao, fileDao,
                commitDao, userRepository);
        this.notificationService = new NotificationService(collaborationRequestDao, fileDao,
                folderDao, workspaceDao, userRepository, commitDao);
        this.profileService = new ProfileService(
                new UserDAO(database),
                new WorkspaceDAO(database),
                new CommitDAO(database));
        this.collaboratorsService = new CollaboratorsService(
                collaborationRequestDao, userRepository, workspaceDao, commitDao);
    }

    private void bindSession() {
        if (workspaceService == null || dashboardContentController == null
                || currentUsername == null || currentUsername.isBlank()) {
            return;
        }

        Optional<ObjectId> userIdOpt = workspaceService.findUserIdByUsername(currentUsername);
        if (userIdOpt.isEmpty()) return;

        this.currentUserId = userIdOpt.get();

        if (avatarInitialsLabel != null) avatarInitialsLabel.setText(initials(currentUsername));
        if (drawerUserName != null) drawerUserName.setText(currentUsername);
        if (drawerUserInitials != null) drawerUserInitials.setText(initials(currentUsername));

        dashboardContentController.configure(workspaceService, currentUserId, currentUsername);
    }

    // ── Navbar handlers ───────────────────────────────────────────────────

    @FXML
    private void onHamburgerClick() {
        toggleDrawer();
    }

    @FXML
    private void onRefreshClick() {
        if (dashboardContentController != null) {
            dashboardContentController.reloadWorkspaces();
        }
    }

    @FXML
    private void onSearchSubmit() {

        if (searchField != null) openSearchResults(searchField.getText());
    }

    @FXML
    private void onSearchIconClick(MouseEvent event) {
        if (searchField != null) openSearchResults(searchField.getText());
    }

    @FXML
    private void onNotificationClick(MouseEvent event) {
        openNotificationPage();
    }

    @FXML
    private void onProfileClick(MouseEvent event) {
        openProfilePage();
    }

    // ── Drawer ────────────────────────────────────────────────────────────

    @FXML
    private void onDrawerOverlayClicked() {
        if (drawerOpen) closeDrawer();
    }

    private void toggleDrawer() {
        if (drawerOpen) closeDrawer(); else openDrawer();
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        drawerSidebar.setVisible(true);
        drawerSidebar.setManaged(true);
        drawerOverlay.setVisible(true);
        drawerOverlay.setManaged(true);
        drawerOverlay.setMouseTransparent(false);

        TranslateTransition slide = new TranslateTransition(Duration.millis(220), drawerSidebar);
        slide.setFromX(-DRAWER_WIDTH);
        slide.setToX(0);

        FadeTransition fade = new FadeTransition(Duration.millis(220), drawerOverlay);
        fade.setFromValue(0);
        fade.setToValue(1);

        slide.play();
        fade.play();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;

        TranslateTransition slide = new TranslateTransition(Duration.millis(200), drawerSidebar);
        slide.setFromX(0);
        slide.setToX(-DRAWER_WIDTH);
        slide.setOnFinished(e -> {
            drawerSidebar.setVisible(false);
            drawerSidebar.setManaged(false);
        });

        FadeTransition fade = new FadeTransition(Duration.millis(200), drawerOverlay);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            drawerOverlay.setVisible(false);
            drawerOverlay.setManaged(false);
            drawerOverlay.setMouseTransparent(true);
        });

        slide.play();
        fade.play();
    }

    private void buildDrawerNav() {
        if (drawerNav == null) return;
        drawerNav.getChildren().clear();

        String[][] items = {
            {"🏠", "Home"},
            {"⊞", "Workspaces"},
            {"🔔", "Notifications"},
            {"👥", "Collaborators"},
            {"⚙", "Settings"}
        };

        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            HBox item = buildDrawerNavItem(items[i][0], items[i][1]);
            item.setOnMouseClicked(e -> onDrawerNavClicked(idx));
            drawerNav.getChildren().add(item);
        }
    }

    private HBox buildDrawerNavItem(String icon, String label) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("drawer-nav-icon");

        Label textLabel = new Label(label);
        textLabel.getStyleClass().add("drawer-nav-text");

        HBox item = new HBox(10, iconLabel, textLabel);
        item.getStyleClass().add("drawer-nav-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(10, 16, 10, 16));
        item.setMaxWidth(Double.MAX_VALUE);
        return item;
    }

    private void onDrawerNavClicked(int index) {
        closeDrawer();
        switch (index) {
            case 0, 1 -> { }
            case 2 -> openNotificationPage();
            case 3 -> openCollaboratorsPage();
            case 4 -> { }
            default -> { }
        }
    }

    // ── Page openers ──────────────────────────────────────────────────────

    private Parent cachedMainRoot;

    private void cacheRoot() {
        if (cachedMainRoot == null && contentArea != null && contentArea.getScene() != null) {
            cachedMainRoot = contentArea.getScene().getRoot();
        }
    }

    private void goHome() {
        Stage stage = null;
        for (Window w : Window.getWindows()) {
            if (w.isFocused() && w instanceof Stage s) { stage = s; break; }
        }
        if (stage == null && contentArea != null && contentArea.getScene() != null) {
            stage = (Stage) contentArea.getScene().getWindow();
        }
        if (stage != null && cachedMainRoot != null) {
            stage.setTitle("Document Version Control System");
            stage.getScene().setRoot(cachedMainRoot);
        }
    }

    private void navigateToRoot(Parent rootNode, String title) {
        cacheRoot();
        Stage stage = null;
        for (Window w : Window.getWindows()) {
            if (w.isFocused() && w instanceof Stage s) { stage = s; break; }
        }
        if (stage == null && contentArea != null && contentArea.getScene() != null) {
            stage = (Stage) contentArea.getScene().getWindow();
        }
        if (stage != null) {
            stage.setTitle(title);
            stage.getScene().setRoot(rootNode);
        }
    }

    private void openSearchResults(String query) {
        if (searchService == null || currentUserId == null) return;
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return;

        URL url = MainLayoutController.class.getResource("/fxml/SearchResults.fxml");
        if (url == null) throw new IllegalStateException("SearchResults.fxml not found");

        try {
            FXMLLoader loader = new FXMLLoader(url);
            Parent rootNode = loader.load();
            SearchController controller = loader.getController();
            controller.configure(searchService, currentUserId, currentUsername,
                    this::goHome, this::openNotificationPage, this::openProfilePage,
                    this::openCollaboratorsPage, this::openFromSearchResult);
            controller.setInitialQuery(q);

            navigateToRoot(rootNode, "Search Results");
        } catch (IOException e) {
            throw new RuntimeException("Failed to open search results", e);
        }
    }

    private void openFromSearchResult(SearchResultItem resultItem) {
        if (dashboardContentController == null || resultItem == null) return;
        goHome();
        dashboardContentController.openWorkspaceDetailsForSearch(
                resultItem.workspaceId(), resultItem.folderName(), resultItem.fileName());
    }

    private void openNotificationPage() {
        if (currentUserId == null || notificationService == null) return;

        URL url = MainLayoutController.class.getResource("/fxml/NotificationPage.fxml");
        if (url == null) throw new IllegalStateException("NotificationPage.fxml not found");

        try {
            FXMLLoader loader = new FXMLLoader(url);
            Parent rootNode = loader.load();
            NotificationController controller = loader.getController();
            controller.configure(notificationService, currentUserId, currentUsername,
                    this::goHome, this::openSearchResults, this::openNotificationPage,
                    this::openProfilePage, this::openCollaboratorsPage);

            navigateToRoot(rootNode, "Notifications");
        } catch (IOException e) {
            throw new RuntimeException("Failed to open notifications", e);
        }
    }

    private void openProfilePage() {
        if (currentUserId == null || profileService == null) return;

        URL url = MainLayoutController.class.getResource("/fxml/ProfilePage.fxml");
        if (url == null) throw new IllegalStateException("ProfilePage.fxml not found");

        try {
            FXMLLoader loader = new FXMLLoader(url);
            Parent rootNode = loader.load();
            ProfileController controller = loader.getController();
            controller.configure(profileService, currentUserId,
                    this::goHome, () -> openSearchResults(""), this::openNotificationPage,
                    this::openProfilePage, this::openCollaboratorsPage);

            navigateToRoot(rootNode, "Profile");
        } catch (IOException e) {
            throw new RuntimeException("Failed to open profile", e);
        }
    }

    private void openCollaboratorsPage() {
        if (currentUserId == null || collaboratorsService == null) return;

        URL url = MainLayoutController.class.getResource("/fxml/CollaboratorsPage.fxml");
        if (url == null) throw new IllegalStateException("CollaboratorsPage.fxml not found");

        try {
            FXMLLoader loader = new FXMLLoader(url);
            Parent rootNode = loader.load();
            CollaboratorsController controller = loader.getController();
            controller.configure(collaboratorsService, currentUserId, currentUsername,
                    this::goHome, () -> openSearchResults(""), this::openNotificationPage, this::openProfilePage);

            navigateToRoot(rootNode, "Collaborators");
        } catch (IOException e) {
            throw new RuntimeException("Failed to open collaborators", e);
        }
    }

    private static Node loadFxmlNode(String resource) {
        URL url = MainLayoutController.class.getResource(resource);
        if (url == null) throw new IllegalStateException("FXML '" + resource + "' not found");
        FXMLLoader loader = new FXMLLoader(url);
        try {
            Node node = loader.load();
            node.getProperties().put("fx:controller", loader.getController());
            return node;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + resource, e);
        }
    }

    private static String initials(String username) {
        if (username == null || username.isBlank()) return "U";
        String[] parts = username.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isBlank()) sb.append(Character.toUpperCase(p.charAt(0)));
            if (sb.length() == 2) break;
        }
        return sb.isEmpty() ? "U" : sb.toString();
    }
}
