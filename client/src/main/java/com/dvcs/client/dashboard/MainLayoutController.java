package com.dvcs.client.dashboard;

import com.dvcs.client.auth.db.MongoConnection;
import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.dashboard.content.DashboardContentController;
import com.dvcs.client.dashboard.data.dao.CollaborationRequestDao;
import com.dvcs.client.dashboard.data.dao.FileDao;
import com.dvcs.client.dashboard.data.dao.FolderDao;
import com.dvcs.client.dashboard.data.dao.WorkspaceDao;
import com.dvcs.client.dashboard.navbar.NavbarController;
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
import com.dvcs.client.dashboard.service.WorkspaceService;
import com.mongodb.client.MongoDatabase;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import org.bson.types.ObjectId;

public class MainLayoutController {

    @FXML
    private BorderPane root;

    private NavbarController navbarController;
    private DashboardContentController dashboardContentController;

    private WorkspaceService workspaceService;
    private SearchService searchService;
    private NotificationService notificationService;
    private ProfileService profileService;

    private String currentUsername;
    private ObjectId currentUserId;

    @FXML
    private void initialize() {
        initializeServices();

        Node navbar = loadFxmlNode("/fxml/Navbar.fxml");
        this.navbarController = (NavbarController) navbar.getProperties().get("fx:controller");

        VBox topContainer = new VBox(navbar);
        topContainer.setPadding(new Insets(10, 18, 0, 18));
        root.setTop(topContainer);

        Node dashboardContent = loadFxmlNode("/fxml/DashboardContent.fxml");
        this.dashboardContentController = (DashboardContentController) dashboardContent.getProperties()
                .get("fx:controller");
        root.setCenter(dashboardContent);

        bindSession();
    }

    public void setUsername(String username) {
        this.currentUsername = username;
        bindSession();
    }

    private void initializeServices() {
        String dbName = System.getenv("MONGODB_DB");
        if (dbName == null || dbName.isBlank()) {
            dbName = "DVCS";
        }

        MongoDatabase database = MongoConnection.getDatabase(dbName);
        UserRepository userRepository = new UserRepository(database);

        WorkspaceDao workspaceDao = new WorkspaceDao(database);
        FolderDao folderDao = new FolderDao(database);
        FileDao fileDao = new FileDao(database);
        CollaborationRequestDao collaborationRequestDao = new CollaborationRequestDao(database);

        this.workspaceService = new WorkspaceService(workspaceDao, folderDao, fileDao, collaborationRequestDao,
                userRepository);
        this.searchService = new SearchService(workspaceDao, folderDao, fileDao);
        this.notificationService = new NotificationService(
                collaborationRequestDao,
                fileDao,
                folderDao,
                workspaceDao,
                userRepository);
        this.profileService = new ProfileService(
                new UserDAO(database),
                new WorkspaceDAO(database),
                new CommitDAO(database));
    }

    private void bindSession() {
        if (workspaceService == null || navbarController == null || dashboardContentController == null) {
            return;
        }
        if (currentUsername == null || currentUsername.isBlank()) {
            return;
        }

        Optional<ObjectId> userIdOpt = workspaceService.findUserIdByUsername(currentUsername);
        if (userIdOpt.isEmpty()) {
            return;
        }

        this.currentUserId = userIdOpt.get();

        navbarController.setUsername(currentUsername);
        navbarController.configureHandlers(
                this::openSearchResults,
                this::openNotificationPage,
                this::openProfilePage);

        dashboardContentController.configure(workspaceService, currentUserId, currentUsername);
    }

    private void openSearchResults(String query) {
        if (searchService == null || currentUserId == null) {
            return;
        }

        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return;
        }

        URL url = MainLayoutController.class.getResource("/fxml/SearchResults.fxml");
        if (url == null) {
            throw new IllegalStateException("FXML '/fxml/SearchResults.fxml' not found on classpath");
        }

        try {
            FXMLLoader loader = new FXMLLoader(url);
            Parent rootNode = loader.load();
            SearchController controller = loader.getController();
            controller.configure(
                    searchService,
                    currentUserId,
                    currentUsername,
                    this::openNotificationPage,
                    this::openProfilePage,
                    this::openFromSearchResult);
            controller.setInitialQuery(normalizedQuery);

            Stage stage = new Stage();
            stage.setTitle("Search Results");

            Window owner = root == null || root.getScene() == null ? null : root.getScene().getWindow();
            if (owner != null) {
                stage.initOwner(owner);
            }

            stage.setScene(new Scene(rootNode));
            stage.setMaximized(true);
            stage.setFullScreenExitHint("");
            stage.setFullScreen(true);
            stage.show();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open search results", e);
        }
    }

    private void openFromSearchResult(SearchResultItem resultItem) {
        if (dashboardContentController == null || resultItem == null) {
            return;
        }
        dashboardContentController.openWorkspaceDetailsForSearch(
                resultItem.workspaceId(),
                resultItem.folderName(),
                resultItem.fileName());
    }

    private void openNotificationPage() {
        if (currentUserId == null || notificationService == null) {
            return;
        }

        URL url = MainLayoutController.class.getResource("/fxml/NotificationPage.fxml");
        if (url == null) {
            throw new IllegalStateException("FXML '/fxml/NotificationPage.fxml' not found on classpath");
        }

        try {
            FXMLLoader loader = new FXMLLoader(url);
            Parent rootNode = loader.load();
            NotificationController controller = loader.getController();
            controller.configure(
                    notificationService,
                    currentUserId,
                    currentUsername,
                    this::openSearchResults,
                    this::openNotificationPage,
                    this::openProfilePage);

            Stage stage = new Stage();
            stage.setTitle("Notifications");

            Window owner = root == null || root.getScene() == null ? null : root.getScene().getWindow();
            if (owner != null) {
                stage.initOwner(owner);
            }

            stage.setScene(new Scene(rootNode));
            stage.setMaximized(true);
            stage.setFullScreenExitHint("");
            stage.setFullScreen(true);
            stage.show();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open notifications", e);
        }
    }

    private void openProfilePage() {
        if (currentUserId == null || profileService == null) {
            return;
        }

        URL url = MainLayoutController.class.getResource("/fxml/ProfilePage.fxml");
        if (url == null) {
            throw new IllegalStateException("FXML '/fxml/ProfilePage.fxml' not found on classpath");
        }

        try {
            FXMLLoader loader = new FXMLLoader(url);
            Parent rootNode = loader.load();
            ProfileController controller = loader.getController();
            controller.configure(
                    profileService,
                    currentUserId,
                    this::openSearchResults,
                    this::openNotificationPage,
                    this::openProfilePage);

            Stage stage = new Stage();
            stage.setTitle("Profile");

            Window owner = root == null || root.getScene() == null ? null : root.getScene().getWindow();
            if (owner != null) {
                stage.initOwner(owner);
            }

            stage.setScene(new Scene(rootNode));
            stage.setMaximized(true);
            stage.setFullScreenExitHint("");
            stage.setFullScreen(true);
            stage.show();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open profile", e);
        }
    }

    private static Node loadFxmlNode(String resource) {
        URL url = MainLayoutController.class.getResource(resource);
        if (url == null) {
            throw new IllegalStateException("FXML '" + resource + "' not found on classpath");
        }
        FXMLLoader loader = new FXMLLoader(url);
        try {
            Node node = loader.load();
            // Store controller for later lookup (simple and avoids extra wiring)
            node.getProperties().put("fx:controller", loader.getController());
            return node;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + resource, e);
        }
    }
}
