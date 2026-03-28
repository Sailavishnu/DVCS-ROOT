package com.dvcs.client.dashboard;

import com.dvcs.client.auth.db.MongoConnection;
import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.dashboard.content.DashboardContentController;
import com.dvcs.client.dashboard.data.PendingRequestView;
import com.dvcs.client.dashboard.data.dao.CollaborationRequestDao;
import com.dvcs.client.dashboard.data.dao.FileDao;
import com.dvcs.client.dashboard.data.dao.FolderDao;
import com.dvcs.client.dashboard.data.dao.WorkspaceDao;
import com.dvcs.client.dashboard.navbar.NavbarController;
import com.dvcs.client.dashboard.service.NotificationService;
import com.dvcs.client.dashboard.service.WorkspaceService;
import com.mongodb.client.MongoDatabase;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;

public class MainLayoutController {

    @FXML
    private BorderPane root;

    private NavbarController navbarController;
    private DashboardContentController dashboardContentController;

    private WorkspaceService workspaceService;
    private NotificationService notificationService;

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
        this.notificationService = new NotificationService(collaborationRequestDao, fileDao, userRepository);
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
                query -> dashboardContentController.performSearch(query),
                this::showPendingNotifications);

        dashboardContentController.configure(workspaceService, currentUserId, currentUsername);
    }

    private void showPendingNotifications() {
        if (currentUserId == null || notificationService == null) {
            return;
        }

        List<PendingRequestView> pending = notificationService.loadPendingRequests(currentUserId);
        if (pending.isEmpty()) {
            Alert info = new Alert(Alert.AlertType.INFORMATION, "No pending collaboration requests.");
            info.setHeaderText("Notifications");
            info.showAndWait();
            return;
        }

        for (PendingRequestView request : pending) {
            ButtonType accept = new ButtonType("Accept", ButtonBar.ButtonData.OK_DONE);
            ButtonType reject = new ButtonType("Reject", ButtonBar.ButtonData.NO);
            ButtonType later = new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE);

            Alert decision = new Alert(Alert.AlertType.CONFIRMATION);
            decision.setTitle("Collaboration Request");
            decision.setHeaderText("Pending Request");
            decision.setContentText("File: " + request.fileName() + "\nRequested by: " + request.requestedByUsername());
            decision.getButtonTypes().setAll(accept, reject, later);

            ButtonType selected = decision.showAndWait().orElse(later);
            if (selected == accept) {
                notificationService.acceptRequest(request.requestId());
            } else if (selected == reject) {
                notificationService.rejectRequest(request.requestId());
            }
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
