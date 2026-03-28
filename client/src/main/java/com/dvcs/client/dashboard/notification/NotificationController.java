package com.dvcs.client.dashboard.notification;

import com.dvcs.client.dashboard.MainLayoutController;
import com.dvcs.client.dashboard.navbar.NavbarController;
import com.dvcs.client.dashboard.service.NotificationService;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.bson.types.ObjectId;

public final class NotificationController {

    @FXML
    private BorderPane root;

    @FXML
    private StackPane headerContainer;

    @FXML
    private HBox bodyRow;

    @FXML
    private VBox leftSection;

    @FXML
    private VBox rightSection;

    @FXML
    private Label pendingCountLabel;

    @FXML
    private Label emptyStateLabel;

    @FXML
    private ScrollPane requestsScrollPane;

    @FXML
    private VBox requestsList;

    private NavbarController navbarController;

    private NotificationService notificationService;
    private ObjectId currentUserId;
    private Consumer<String> onSearchSubmitted;
    private Runnable onNotificationRequested;
    private Runnable onProfileRequested;

    private final List<NotificationRequestItem> currentItems = new ArrayList<>();

    @FXML
    private void initialize() {
        Node navbar = loadFxmlNode("/fxml/Navbar.fxml");
        this.navbarController = (NavbarController) navbar.getProperties().get("fx:controller");

        VBox topContainer = new VBox(navbar);
        topContainer.setPadding(new Insets(10, 18, 0, 18));
        headerContainer.getChildren().setAll(topContainer);

        if (bodyRow != null) {
            bodyRow.widthProperty().addListener((obs, oldValue, newValue) -> applyColumnRatio());
        }
        applyColumnRatio();
    }

    public void configure(
            NotificationService notificationService,
            ObjectId currentUserId,
            String currentUsername,
            Consumer<String> onSearchSubmitted,
            Runnable onNotificationRequested,
            Runnable onProfileRequested) {
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.currentUserId = Objects.requireNonNull(currentUserId, "currentUserId");
        this.onSearchSubmitted = onSearchSubmitted;
        this.onNotificationRequested = onNotificationRequested;
        this.onProfileRequested = onProfileRequested;

        if (navbarController != null) {
            navbarController.setUsername(currentUsername);
            navbarController.configureHandlers(this::handleSearch, this::handleNotificationClick,
                    this::handleProfileClick);
        }

        reloadRequests();
    }

    private void applyColumnRatio() {
        if (bodyRow == null || leftSection == null || rightSection == null) {
            return;
        }
        double width = bodyRow.getWidth();
        if (width <= 0) {
            return;
        }

        double spacing = bodyRow.getSpacing();
        double available = Math.max(0, width - spacing);

        leftSection.setPrefWidth(available * 0.65);
        rightSection.setPrefWidth(available * 0.35);
    }

    private void handleSearch(String query) {
        if (onSearchSubmitted != null) {
            onSearchSubmitted.accept(query);
        }
    }

    private void handleNotificationClick() {
        if (onNotificationRequested != null) {
            onNotificationRequested.run();
        }
    }

    private void handleProfileClick() {
        if (onProfileRequested != null) {
            onProfileRequested.run();
        }
    }

    private void reloadRequests() {
        if (notificationService == null || currentUserId == null) {
            return;
        }

        currentItems.clear();
        currentItems.addAll(notificationService.loadNotificationRequests(currentUserId));
        renderRequests();
    }

    private void renderRequests() {
        requestsList.getChildren().clear();

        int count = currentItems.size();
        pendingCountLabel.setText(count + (count == 1 ? " Request Pending" : " Requests Pending"));

        boolean hasRequests = count > 0;
        emptyStateLabel.setVisible(!hasRequests);
        emptyStateLabel.setManaged(!hasRequests);
        requestsScrollPane.setVisible(hasRequests);
        requestsScrollPane.setManaged(hasRequests);

        if (!hasRequests) {
            return;
        }

        for (NotificationRequestItem item : currentItems) {
            requestsList.getChildren().add(createNotificationCard(item));
        }
    }

    private Node createNotificationCard(NotificationRequestItem item) {
        HBox card = new HBox(14);
        card.getStyleClass().add("notification-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(14));

        StackPane iconBox = new StackPane();
        iconBox.getStyleClass().add("notification-icon-box");
        iconBox.setPrefSize(50, 50);
        iconBox.setMinSize(50, 50);
        iconBox.setMaxSize(50, 50);

        Label iconText = new Label("COL");
        iconText.getStyleClass().add("notification-icon-text");
        iconBox.getChildren().add(iconText);

        VBox content = new VBox(10);
        HBox.setHgrow(content, Priority.ALWAYS);

        Label workspaceNameLabel = new Label(item.workspaceName());
        workspaceNameLabel.getStyleClass().add("notification-workspace-name");

        Label messageLabel = new Label(item.requestedByUsername() + " invited you for a collaboration");
        messageLabel.getStyleClass().add("notification-message");

        HBox actions = new HBox(10);
        Button acceptButton = new Button("Accept");
        acceptButton.getStyleClass().add("notification-accept-button");
        acceptButton.setOnAction(event -> onAccept(item));

        Button declineButton = new Button("Decline");
        declineButton.getStyleClass().add("notification-decline-button");
        declineButton.setOnAction(event -> onDecline(item));

        actions.getChildren().addAll(acceptButton, declineButton);
        content.getChildren().addAll(workspaceNameLabel, messageLabel, actions);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        card.getChildren().addAll(iconBox, content, spacer);
        return card;
    }

    private void onAccept(NotificationRequestItem item) {
        if (item == null || notificationService == null) {
            return;
        }
        if (notificationService.acceptRequest(item.requestId())) {
            currentItems.remove(item);
            renderRequests();
        }
    }

    private void onDecline(NotificationRequestItem item) {
        if (item == null || notificationService == null) {
            return;
        }
        if (notificationService.rejectRequest(item.requestId())) {
            currentItems.remove(item);
            renderRequests();
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
            node.getProperties().put("fx:controller", loader.getController());
            return node;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + resource, e);
        }
    }
}
