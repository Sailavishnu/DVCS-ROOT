package com.dvcs.client.dashboard.notification;

import com.dvcs.client.dashboard.MainLayoutController;
import com.dvcs.client.dashboard.service.NotificationService;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.bson.types.ObjectId;

public final class NotificationController {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

    @FXML private VBox sidebarNav;
    @FXML private Label userInitialsLabel;
    @FXML private Label userNameLabel;
    @FXML private TextField topSearchField;
    @FXML private VBox emptyFeedBox;
    @FXML private ScrollPane feedScrollPane;
    @FXML private VBox notifFeed;
    @FXML private VBox quickActionsBox;
    @FXML private Button markAllReadBtn;

    private NotificationService notificationService;
    private ObjectId currentUserId;
    private Consumer<String> onSearchSubmitted;
    private Runnable onNotificationRequested;
    private Runnable onProfileRequested;
    private Runnable onCollaboratorsRequested;

    @FXML
    private void initialize() {
        buildSidebarNav();
        buildQuickActions();
    }

    private Runnable onHomeRequested;

    public void configure(
            NotificationService notificationService,
            ObjectId currentUserId,
            String currentUsername,
            Runnable onHomeRequested,
            Consumer<String> onSearchSubmitted,
            Runnable onNotificationRequested,
            Runnable onProfileRequested,
            Runnable onCollaboratorsRequested) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.currentUserId = Objects.requireNonNull(currentUserId);
        this.onHomeRequested = onHomeRequested;
        this.onSearchSubmitted = onSearchSubmitted;
        this.onNotificationRequested = onNotificationRequested;
        this.onProfileRequested = onProfileRequested;
        this.onCollaboratorsRequested = onCollaboratorsRequested;

        if (userNameLabel != null) userNameLabel.setText(currentUsername == null ? "" : currentUsername);
        if (userInitialsLabel != null) userInitialsLabel.setText(initials(currentUsername));

        if (topSearchField != null) {
            topSearchField.setOnAction(e -> {
                String q = topSearchField.getText();
                if (q != null && !q.isBlank() && onSearchSubmitted != null) {
                    onSearchSubmitted.accept(q.trim());
                }
            });
        }

        reloadFeed();
    }

    @FXML
    private void onRefreshClick(MouseEvent event) {
        reloadFeed();
    }

    @FXML
    private void onTopNotifClick(MouseEvent event) {

        // already on notification page – no-op
    }

    @FXML
    private void onTopProfileClick(MouseEvent event) {
        if (onProfileRequested != null) onProfileRequested.run();
    }

    @FXML
    private void onMarkAllRead() {
        // visual-only: mark all cards as read
        reloadFeed();
    }

    // ── Sidebar ───────────────────────────────────────────────────────────

    private void buildSidebarNav() {
        if (sidebarNav == null) return;
        sidebarNav.getChildren().clear();

        String[][] items = {
            {"🏠", "Home"},
            {"⊞", "Workspaces"},
            {"🔔", "Notifications"},
            {"👥", "Collaborators"},
            {"⚙", "Settings"}
        };
        boolean[] active   = {false, false, true, false, false};
        boolean[] disabled = {false, false, false, false, false};

        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            HBox item = buildNavItem(items[i][0], items[i][1], active[i], disabled[i]);
            if (!disabled[i]) {
                item.setOnMouseClicked(e -> onNavItemClicked(idx));
            }
            sidebarNav.getChildren().add(item);
        }
    }

    private HBox buildNavItem(String icon, String label, boolean isActive, boolean isDisabled) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("notif-nav-item-icon");

        Label textLabel = new Label(label);
        textLabel.getStyleClass().add("notif-nav-item-text");

        HBox item = new HBox(10, iconLabel, textLabel);
        item.getStyleClass().add("notif-nav-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(10, 16, 10, 16));
        item.setMaxWidth(Double.MAX_VALUE);

        if (isActive)    item.getStyleClass().add("notif-nav-active");
        if (isDisabled)  item.getStyleClass().add("notif-nav-disabled");
        return item;
    }

    private void onNavItemClicked(int index) {
        switch (index) {
            case 0, 1 -> closeCurrentWindow();
            case 2 -> { /* Notifications – already here */ }
            case 3 -> { if (onCollaboratorsRequested != null) onCollaboratorsRequested.run(); }
            case 4 -> { /* Settings – placeholder */ }
            default -> { }
        }
    }

    private void buildQuickActions() {
        if (quickActionsBox == null) return;
        quickActionsBox.getChildren().clear();

        quickActionsBox.getChildren().add(buildQuickActionCard("⚡", "Forge Branch", "Quick-start new workspace"));
        quickActionsBox.getChildren().add(buildQuickActionCard("🔒", "Rotate Keys", "Update local key pairs"));
    }

    private HBox buildQuickActionCard(String icon, String title, String sub) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("notif-quick-action-icon");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("notif-quick-action-title");

        Label subLabel = new Label(sub);
        subLabel.getStyleClass().add("notif-quick-action-sub");

        VBox info = new VBox(2, titleLabel, subLabel);

        HBox card = new HBox(10, iconLabel, info);
        card.getStyleClass().add("notif-quick-action-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    // ── Feed ──────────────────────────────────────────────────────────────

    private void reloadFeed() {
        if (notificationService == null || currentUserId == null) return;

        List<NotificationItem> items = notificationService.loadAllNotifications(currentUserId);
        renderFeed(items);
    }

    private void renderFeed(List<NotificationItem> items) {
        notifFeed.getChildren().clear();

        boolean empty = items == null || items.isEmpty();
        emptyFeedBox.setVisible(empty);
        emptyFeedBox.setManaged(empty);
        feedScrollPane.setVisible(!empty);
        feedScrollPane.setManaged(!empty);

        if (empty) return;

        for (NotificationItem item : items) {
            notifFeed.getChildren().add(
                    "COLLAB_REQUEST".equals(item.type())
                            ? buildCollabCard(item)
                            : buildCommitCard(item));
        }
    }

    private Node buildCollabCard(NotificationItem item) {
        // Icon box
        StackPane iconBox = new StackPane(new Label("👤"));
        iconBox.getStyleClass().add("notif-card-icon-box");
        ((Label) iconBox.getChildren().get(0)).getStyleClass().add("notif-card-icon");

        // Header row
        Label titleLabel = new Label("Collaboration Request");
        titleLabel.getStyleClass().add("notif-card-title");

        Label userLabel = new Label(item.requestedBy() == null ? "" : item.requestedBy());
        userLabel.getStyleClass().add("notif-card-user");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label timeLabel = new Label(formatTime(item.time()));
        timeLabel.getStyleClass().add("notif-card-time");

        HBox headerRow = new HBox(spacer, timeLabel);
        VBox titleBlock = new VBox(4, titleLabel, userLabel);
        HBox topRow = new HBox(titleBlock, spacer, timeLabel);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Message
        Label msgLabel = new Label("Wants to collaborate on " + item.workspaceName());
        msgLabel.getStyleClass().add("notif-card-message");
        msgLabel.setMaxWidth(Double.MAX_VALUE);

        // Buttons
        Button acceptBtn = new Button("Accept");
        acceptBtn.getStyleClass().add("notif-accept-btn");
        acceptBtn.setOnAction(e -> onAccept(item));

        Button declineBtn = new Button("Decline");
        declineBtn.getStyleClass().add("notif-decline-btn");
        declineBtn.setOnAction(e -> onDecline(item));

        HBox actions = new HBox(10, acceptBtn, declineBtn);
        actions.setPadding(new Insets(6, 0, 0, 0));

        VBox content = new VBox(8, topRow, msgLabel, actions);
        HBox.setHgrow(content, Priority.ALWAYS);

        HBox card = new HBox(14, iconBox, content);
        card.getStyleClass().add("notif-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Node buildCommitCard(NotificationItem item) {
        // Icon box
        StackPane iconBox = new StackPane(new Label("⑂"));
        iconBox.getStyleClass().add("notif-card-icon-box");
        ((Label) iconBox.getChildren().get(0)).getStyleClass().add("notif-card-icon");

        // Header row
        Label titleLabel = new Label("New Commit Detected");
        titleLabel.getStyleClass().add("notif-card-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label timeLabel = new Label(formatTime(item.time()));
        timeLabel.getStyleClass().add("notif-card-time");

        HBox topRow = new HBox(titleLabel, spacer, timeLabel);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Preview: committer + message
        String preview = (item.requestedBy() == null ? "" : item.requestedBy())
                + " committed: "
                + (item.message() == null ? "" : item.message());
        Label previewLabel = new Label(preview);
        previewLabel.getStyleClass().add("notif-card-preview");
        previewLabel.setMaxWidth(Double.MAX_VALUE);
        previewLabel.setWrapText(true);

        // Footer: hash + workspace
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);

        if (item.commitHash() != null && !item.commitHash().isEmpty()) {
            Label hashLabel = new Label("SH: " + item.commitHash());
            hashLabel.getStyleClass().add("notif-card-hash");
            footer.getChildren().add(hashLabel);
        }

        if (item.workspaceName() != null && !item.workspaceName().isEmpty()) {
            Label wsLabel = new Label("Workspace: " + item.workspaceName());
            wsLabel.getStyleClass().add("notif-card-ws-label");
            footer.getChildren().add(wsLabel);
        }

        VBox content = new VBox(8, topRow, previewLabel, footer);
        HBox.setHgrow(content, Priority.ALWAYS);

        HBox card = new HBox(14, iconBox, content);
        card.getStyleClass().add("notif-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void onAccept(NotificationItem item) {
        if (notificationService == null || item.requestId() == null) return;
        if (notificationService.acceptRequest(item.requestId())) reloadFeed();
    }

    private void onDecline(NotificationItem item) {
        if (notificationService == null || item.requestId() == null) return;
        if (notificationService.rejectRequest(item.requestId())) reloadFeed();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void closeCurrentWindow() {
        if (onHomeRequested != null) {
            onHomeRequested.run();
        }
    }

    private static String formatTime(Instant time) {
        if (time == null) return "";
        return TIME_FMT.format(time);
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
