package com.dvcs.client.dashboard.collaborators;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.bson.types.ObjectId;

public final class CollaboratorsController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    @FXML private VBox sidebarNav;
    @FXML private Label userInitialsLabel;
    @FXML private Label userNameLabel;
    @FXML private TextField topSearchField;
    @FXML private Label nodesOnlineLabel;
    @FXML private Label totalCollabLabel;
    @FXML private Label activeCollabLabel;
    @FXML private TilePane cardsGrid;
    @FXML private StackPane inviteModal;
    @FXML private TextField inviteUsernameField;
    @FXML private TextField inviteWorkspaceField;

    private CollaboratorsService collaboratorsService;
    private ObjectId currentUserId;
    private Runnable onSearchRequested;
    private Runnable onNotificationRequested;
    private Runnable onProfileRequested;
    private java.util.function.Consumer<ObjectId> onViewOtherProfile;

    @FXML
    private void initialize() {
        buildSidebarNav();
    }

    private Runnable onHomeRequested;

    public void configure(
            CollaboratorsService collaboratorsService,
            ObjectId currentUserId,
            String currentUsername,
            Runnable onHomeRequested,
            Runnable onSearchRequested,
            Runnable onNotificationRequested,
            Runnable onProfileRequested,
            java.util.function.Consumer<ObjectId> onViewOtherProfile) {
        this.collaboratorsService = Objects.requireNonNull(collaboratorsService);
        this.currentUserId = Objects.requireNonNull(currentUserId);
        this.onHomeRequested = onHomeRequested;
        this.onSearchRequested = onSearchRequested;
        this.onNotificationRequested = onNotificationRequested;
        this.onProfileRequested = onProfileRequested;
        this.onViewOtherProfile = onViewOtherProfile;

        if (userNameLabel != null) userNameLabel.setText(currentUsername == null ? "" : currentUsername);
        if (userInitialsLabel != null) userInitialsLabel.setText(initials(currentUsername));

        if (topSearchField != null) {
            topSearchField.setOnAction(e -> {
                if (onSearchRequested != null) onSearchRequested.run();
            });
        }

        reloadCollaborators();
    }

    @FXML
    private void onRefreshClick(MouseEvent event) {
        reloadCollaborators();
    }

    @FXML
    private void onTopNotifClick(MouseEvent event) {

        if (onNotificationRequested != null) onNotificationRequested.run();
    }

    @FXML
    private void onTopProfileClick(MouseEvent event) {
        if (onProfileRequested != null) onProfileRequested.run();
    }

    @FXML
    private void onInviteClick() {
        if (inviteModal != null) {
            if (inviteUsernameField != null) inviteUsernameField.clear();
            if (inviteWorkspaceField != null) inviteWorkspaceField.clear();
            inviteModal.setVisible(true);
            inviteModal.setManaged(true);
        }
    }

    @FXML
    private void onInviteCancel() {
        hideInviteModal();
    }

    @FXML
    private void onInviteSubmit() {
        if (collaboratorsService == null || currentUserId == null) return;
        String username = inviteUsernameField == null ? "" : inviteUsernameField.getText().trim();
        String workspace = inviteWorkspaceField == null ? "" : inviteWorkspaceField.getText().trim();

        if (username.isEmpty() || workspace.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Both username and workspace name are required.");
            return;
        }
        try {
            collaboratorsService.createInvite(currentUserId, username, workspace);
            hideInviteModal();
            showAlert(Alert.AlertType.INFORMATION, "Invite sent to @" + username + ".");
        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.ERROR, e.getMessage());
        }
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
        boolean[] active   = {false, false, false, true, false};
        boolean[] disabled = {false, false, false, false, true};

        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            HBox item = buildNavItem(items[i][0], items[i][1], active[i], disabled[i]);
            if (!disabled[i]) item.setOnMouseClicked(e -> onNavItemClicked(idx));
            sidebarNav.getChildren().add(item);
        }
    }

    private HBox buildNavItem(String icon, String label, boolean isActive, boolean isDisabled) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("collab-nav-item-icon");

        Label textLabel = new Label(label);
        textLabel.getStyleClass().add("collab-nav-item-text");

        HBox item = new HBox(10, iconLabel, textLabel);
        item.getStyleClass().add("collab-nav-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(10, 16, 10, 16));
        item.setMaxWidth(Double.MAX_VALUE);

        if (isActive)   item.getStyleClass().add("collab-nav-active");
        if (isDisabled) item.getStyleClass().add("collab-nav-disabled");
        return item;
    }

    private void onNavItemClicked(int index) {
        switch (index) {
            case 0, 1 -> closeCurrentWindow();
            case 2 -> { if (onNotificationRequested != null) onNotificationRequested.run(); }
            case 3 -> { /* already here */ }
            default -> { }
        }
    }

    // ── Cards ─────────────────────────────────────────────────────────────

    private void reloadCollaborators() {
        if (collaboratorsService == null || currentUserId == null) return;
        List<CollaboratorItem> collaborators = collaboratorsService.loadCollaborators(currentUserId);

        long online = collaboratorsService.countOnline(collaborators);
        if (nodesOnlineLabel != null)
            nodesOnlineLabel.setText(online + " Node" + (online == 1 ? "" : "s") + " Online");
        if (totalCollabLabel != null) totalCollabLabel.setText(String.valueOf(collaborators.size()));
        if (activeCollabLabel != null) activeCollabLabel.setText(String.valueOf(online));

        if (cardsGrid != null) {
            cardsGrid.getChildren().clear();
            for (CollaboratorItem item : collaborators) {
                cardsGrid.getChildren().add(buildCollaboratorCard(item));
            }
            cardsGrid.getChildren().add(buildInviteCard());
        }
    }

    private VBox buildCollaboratorCard(CollaboratorItem item) {
        // Avatar + status dot
        Label avatarLabel = new Label(item.initials());
        avatarLabel.getStyleClass().add("collab-card-avatar-text");
        StackPane avatarBox = new StackPane(avatarLabel);
        avatarBox.getStyleClass().add("collab-card-avatar");

        Label statusDot = new Label("●");
        statusDot.getStyleClass().addAll("collab-card-status-dot", "status-dot-" + item.status().toLowerCase());
        StackPane.setAlignment(statusDot, Pos.BOTTOM_RIGHT);
        StackPane avatarWithDot = new StackPane(avatarBox, statusDot);
        avatarWithDot.setMaxWidth(56);
        avatarWithDot.setPrefWidth(56);

        Label nameLabel = new Label(item.username());
        nameLabel.getStyleClass().add("collab-card-name");
        Label handleLabel = new Label("@" + item.username());
        handleLabel.getStyleClass().add("collab-card-handle");
        VBox nameBlock = new VBox(2, nameLabel, handleLabel);
        HBox.setHgrow(nameBlock, Priority.ALWAYS);

        Label statusBadge = new Label(item.status());
        statusBadge.getStyleClass().addAll("collab-status-badge",
                "collab-status-" + item.status().toLowerCase());

        HBox topRow = new HBox(14, avatarWithDot, nameBlock, statusBadge);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Info row: workspace + since
        VBox wsBox = new VBox(4);
        wsBox.getStyleClass().add("collab-card-info-box");
        HBox.setHgrow(wsBox, Priority.ALWAYS);
        Label wsTitle = new Label("Collaborating on");
        wsTitle.getStyleClass().add("collab-card-info-label");
        Label wsValue = new Label(item.workspaceName());
        wsValue.getStyleClass().add("collab-card-info-value");
        wsBox.getChildren().addAll(wsTitle, wsValue);

        VBox sinceBox = new VBox(4);
        sinceBox.getStyleClass().add("collab-card-info-box");
        HBox.setHgrow(sinceBox, Priority.ALWAYS);
        Label sinceTitle = new Label("Since");
        sinceTitle.getStyleClass().add("collab-card-info-label");
        Label sinceValue = new Label(item.since() != null ? DATE_FMT.format(item.since()) : "—");
        sinceValue.getStyleClass().add("collab-card-info-value");
        sinceBox.getChildren().addAll(sinceTitle, sinceValue);

        HBox infoRow = new HBox(12, wsBox, sinceBox);

        // Recent commits
        VBox commitsBox = new VBox(6);
        Label commitsHeader = new Label("⟲  Recent Changes");
        commitsHeader.getStyleClass().add("collab-card-commits-title");
        commitsBox.getChildren().add(commitsHeader);

        List<String> msgs = item.recentCommitMessages();
        List<Instant> times = item.recentCommitTimes();
        for (int i = 0; i < Math.min(msgs.size(), 2); i++) {
            String msg = msgs.get(i);
            Instant t = (i < times.size()) ? times.get(i) : null;
            Label msgLabel = new Label(msg == null ? "" : msg);
            msgLabel.getStyleClass().add("collab-commit-msg");
            HBox.setHgrow(msgLabel, Priority.ALWAYS);
            Label timeLabel = new Label(formatRelative(t));
            timeLabel.getStyleClass().add("collab-commit-time");
            HBox row = new HBox(msgLabel, timeLabel);
            row.getStyleClass().add("collab-commit-row");
            row.setAlignment(Pos.CENTER_LEFT);
            commitsBox.getChildren().add(row);
        }
        if (msgs.isEmpty()) {
            Label none = new Label("No recent commits");
            none.getStyleClass().add("collab-commit-msg");
            none.setOpacity(0.45);
            commitsBox.getChildren().add(none);
        }

        Button viewProfileBtn = new Button("View Profile");
        viewProfileBtn.setStyle("-fx-padding: 5 12; -fx-font-size: 11; -fx-text-fill: #032312; "
                + "-fx-background-color: #00df7a; -fx-background-radius: 4; -fx-cursor: hand;");
        viewProfileBtn.setOnAction(e -> {
            if (onViewOtherProfile != null && item.userId() != null) {
                onViewOtherProfile.accept(item.userId());
            }
        });
        HBox actionsRow = new HBox(viewProfileBtn);
        actionsRow.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(16, topRow, infoRow, commitsBox, actionsRow);
        card.getStyleClass().add("collab-card");
        card.setPrefWidth(300);
        return card;
    }

    private VBox buildInviteCard() {
        Label icon = new Label("+");
        icon.getStyleClass().add("collab-invite-card-icon");
        StackPane iconBox = new StackPane(icon);
        iconBox.getStyleClass().add("collab-invite-card-icon-box");

        Label title = new Label("Invite Collaborator");
        title.getStyleClass().add("collab-invite-card-title");

        Label sub = new Label("Send an invite to a new developer node");
        sub.getStyleClass().add("collab-invite-card-sub");
        sub.setWrapText(true);

        VBox card = new VBox(12, iconBox, title, sub);
        card.getStyleClass().add("collab-invite-card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(300);
        card.setOnMouseClicked(e -> onInviteClick());
        return card;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void hideInviteModal() {
        if (inviteModal != null) {
            inviteModal.setVisible(false);
            inviteModal.setManaged(false);
        }
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Alert alert = new Alert(type, msg);
        alert.setHeaderText(null);
        for (Window w : Window.getWindows()) {
            if (w.isFocused() && w instanceof Stage s) {
                alert.initOwner(s);
                alert.initModality(Modality.WINDOW_MODAL);
                break;
            }
        }
        alert.showAndWait();
    }

    private void closeCurrentWindow() {
        if (onHomeRequested != null) {
            onHomeRequested.run();
        }
    }

    private static String formatRelative(Instant t) {
        if (t == null) return "";
        long s = Instant.now().getEpochSecond() - t.getEpochSecond();
        if (s < 60) return "Just now";
        if (s < 3600) return (s / 60) + "m ago";
        if (s < 86400) return (s / 3600) + "h ago";
        return (s / 86400) + "d ago";
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
