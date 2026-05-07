package com.dvcs.client.dashboard.search;

import com.dvcs.client.dashboard.MainLayoutController;
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
import javafx.scene.control.CheckBox;
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

public final class SearchController {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

    @FXML private VBox sidebarNav;
    @FXML private Label userInitialsLabel;
    @FXML private Label userNameLabel;
    @FXML private TextField topSearchField;

    @FXML private CheckBox filesCheckBox;
    @FXML private CheckBox commitsCheckBox;
    @FXML private CheckBox workspacesCheckBox;

    @FXML private Label filesCountBadge;
    @FXML private Label commitsCountBadge;
    @FXML private Label workspacesCountBadge;

    @FXML private Label resultsCountLabel;
    @FXML private VBox emptyStateBox;
    @FXML private ScrollPane resultsScrollPane;
    @FXML private VBox resultsList;

    private SearchService searchService;
    private ObjectId currentUserId;
    private Runnable onNotificationRequested;
    private Runnable onProfileRequested;
    private Runnable onCollaboratorsRequested;
    private Consumer<SearchResultItem> onResultSelected;

    private String lastQuery = "";

    @FXML
    private void initialize() {
        buildSidebarNav();
    }

    private Runnable onHomeRequested;

    public void configure(
            SearchService searchService,
            ObjectId currentUserId,
            String currentUsername,
            Runnable onHomeRequested,
            Runnable onNotificationRequested,
            Runnable onProfileRequested,
            Runnable onCollaboratorsRequested,
            Consumer<SearchResultItem> onResultSelected) {
        this.searchService = Objects.requireNonNull(searchService);
        this.currentUserId = Objects.requireNonNull(currentUserId);
        this.onHomeRequested = onHomeRequested;
        this.onNotificationRequested = onNotificationRequested;
        this.onProfileRequested = onProfileRequested;
        this.onCollaboratorsRequested = onCollaboratorsRequested;
        this.onResultSelected = onResultSelected;

        if (userNameLabel != null) userNameLabel.setText(currentUsername == null ? "" : currentUsername);
        if (userInitialsLabel != null) userInitialsLabel.setText(initials(currentUsername));
    }

    public void setInitialQuery(String query) {
        if (topSearchField != null) topSearchField.setText(query == null ? "" : query);
        executeSearch(query);
    }

    @FXML
    private void onTopSearchSubmit() {
        if (topSearchField != null) executeSearch(topSearchField.getText());
    }

    @FXML
    private void onRefreshClick(MouseEvent event) {
        if (!lastQuery.isBlank()) executeSearch(lastQuery);
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
    private void onFilterChanged() {
        if (!lastQuery.isBlank()) executeSearch(lastQuery);
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
        boolean[] active   = {false, false, false, false, false};
        boolean[] disabled = {false, false, false, false, false};

        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            HBox item = buildNavItem(items[i][0], items[i][1], active[i], disabled[i]);
            if (!disabled[i]) item.setOnMouseClicked(e -> onNavItemClicked(idx));
            sidebarNav.getChildren().add(item);
        }
    }

    private HBox buildNavItem(String icon, String label, boolean isActive, boolean isDisabled) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("srch-nav-item-icon");

        Label textLabel = new Label(label);
        textLabel.getStyleClass().add("srch-nav-item-text");

        HBox item = new HBox(10, iconLabel, textLabel);
        item.getStyleClass().add("srch-nav-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(10, 16, 10, 16));
        item.setMaxWidth(Double.MAX_VALUE);

        if (isActive)   item.getStyleClass().add("srch-nav-active");
        if (isDisabled) item.getStyleClass().add("srch-nav-disabled");
        return item;
    }

    private void onNavItemClicked(int index) {
        switch (index) {
            case 0, 1 -> closeCurrentWindow();
            case 2 -> { if (onNotificationRequested != null) onNotificationRequested.run(); }
            case 3 -> { if (onCollaboratorsRequested != null) onCollaboratorsRequested.run(); }
            default -> { }
        }
    }

    // ── Search ────────────────────────────────────────────────────────────

    private void executeSearch(String query) {
        if (searchService == null || currentUserId == null) return;

        String q = query == null ? "" : query.trim();
        lastQuery = q;

        if (q.isEmpty()) {
            showEmpty("Search for something");
            return;
        }

        boolean incFiles       = filesCheckBox      == null || filesCheckBox.isSelected();
        boolean incCommits     = commitsCheckBox     == null || commitsCheckBox.isSelected();
        boolean incWorkspaces  = workspacesCheckBox  == null || workspacesCheckBox.isSelected();

        List<SearchResultItem> all = searchService.searchAll(currentUserId, q,
                incFiles, incCommits, incWorkspaces);

        long fileCount = all.stream().filter(r -> "FILE".equals(r.type())).count();
        long commitCount = all.stream().filter(r -> "COMMIT".equals(r.type())).count();
        long wsCount = all.stream().filter(r -> "WORKSPACE".equals(r.type())).count();

        if (filesCountBadge != null)      filesCountBadge.setText(String.valueOf(fileCount));
        if (commitsCountBadge != null)    commitsCountBadge.setText(String.valueOf(commitCount));
        if (workspacesCountBadge != null) workspacesCountBadge.setText(String.valueOf(wsCount));

        renderResults(all, q);
    }

    private void renderResults(List<SearchResultItem> results, String query) {
        resultsList.getChildren().clear();

        int count = results == null ? 0 : results.size();

        if (count == 0) {
            showEmpty("No matches for '" + query + "'");
            return;
        }

        emptyStateBox.setVisible(false);
        emptyStateBox.setManaged(false);
        resultsScrollPane.setVisible(true);
        resultsScrollPane.setManaged(true);

        if (resultsCountLabel != null) {
            resultsCountLabel.setText("Results for '" + query + "'  —  " + count + " match" + (count == 1 ? "" : "es"));
        }

        for (SearchResultItem item : results) {
            Node card = switch (item.type()) {
                case "FILE"      -> buildFileCard(item);
                case "COMMIT"    -> buildCommitCard(item);
                case "WORKSPACE" -> buildWorkspaceCard(item);
                default          -> buildFileCard(item);
            };
            resultsList.getChildren().add(card);
        }
    }

    private void showEmpty(String message) {
        emptyStateBox.setVisible(true);
        emptyStateBox.setManaged(true);
        resultsScrollPane.setVisible(false);
        resultsScrollPane.setManaged(false);
        if (resultsCountLabel != null) resultsCountLabel.setText(message);
    }

    // ── Card builders ─────────────────────────────────────────────────────

    private Node buildFileCard(SearchResultItem r) {
        StackPane iconBox = makeTypeBox("📄");

        Label nameLabel = new Label(r.fileName() == null ? "" : r.fileName());
        nameLabel.getStyleClass().add("srch-card-filename");

        HBox pathRow = new HBox(6);
        pathRow.setAlignment(Pos.CENTER_LEFT);
        Label folderIcon = new Label("📁");
        folderIcon.setStyle("-fx-font-size:11px;");
        Label pathLabel = new Label(r.relativePath() == null ? "" : r.relativePath());
        pathLabel.getStyleClass().add("srch-card-path");
        pathRow.getChildren().addAll(folderIcon, pathLabel);

        if (r.lastModifiedAt() != null) {
            Label sep = new Label("|");
            sep.setStyle("-fx-text-fill: rgba(0,255,156,0.2); -fx-padding: 0 4 0 4;");
            Label timeLabel = new Label("Modified: " + TIME_FMT.format(r.lastModifiedAt()));
            timeLabel.getStyleClass().add("srch-card-time");
            pathRow.getChildren().addAll(sep, timeLabel);
        }

        VBox meta = new VBox(6, nameLabel, pathRow);
        HBox.setHgrow(meta, Priority.ALWAYS);

        HBox top = new HBox(14, iconBox, meta);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, top);
        card.setMaxWidth(Double.MAX_VALUE);

        if (r.contentPreview() != null && !r.contentPreview().isBlank()) {
            Label preview = new Label(r.contentPreview());
            preview.getStyleClass().add("srch-card-preview");
            preview.setMaxWidth(Double.MAX_VALUE);
            card.getChildren().add(preview);
        }

        card.getStyleClass().add("srch-result-card");
        card.setOnMouseClicked(e -> { if (onResultSelected != null) onResultSelected.accept(r); });
        return card;
    }

    private Node buildCommitCard(SearchResultItem r) {
        StackPane iconBox = makeTypeBox("⑂");

        Label msgLabel = new Label("Commit: " + (r.contentPreview() == null ? "" : r.contentPreview()));
        msgLabel.getStyleClass().add("srch-card-filename");
        msgLabel.setWrapText(true);

        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        if (r.commitHash() != null && !r.commitHash().isEmpty()) {
            Label hashLabel = new Label(r.commitHash());
            hashLabel.getStyleClass().add("srch-card-hash");
            metaRow.getChildren().add(hashLabel);
        }

        if (r.committedBy() != null && !r.committedBy().isEmpty()) {
            Label dot = new Label("•");
            dot.setStyle("-fx-text-fill:#4B5563;");
            Label committer = new Label("@" + r.committedBy());
            committer.getStyleClass().add("srch-card-committer");
            metaRow.getChildren().addAll(dot, committer);
        }

        if (r.lastModifiedAt() != null) {
            Label dot2 = new Label("•");
            dot2.setStyle("-fx-text-fill:#4B5563;");
            Label timeLabel = new Label(TIME_FMT.format(r.lastModifiedAt()));
            timeLabel.getStyleClass().add("srch-card-time");
            metaRow.getChildren().addAll(dot2, timeLabel);
        }

        VBox meta = new VBox(6, msgLabel, metaRow);
        HBox.setHgrow(meta, Priority.ALWAYS);

        HBox top = new HBox(14, iconBox, meta);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(0, top);
        card.getStyleClass().add("srch-result-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Node buildWorkspaceCard(SearchResultItem r) {
        StackPane iconBox = makeTypeBox("⊞");

        Label nameLabel = new Label(r.workspaceName() == null ? "" : r.workspaceName());
        nameLabel.getStyleClass().add("srch-card-filename");

        HBox tags = new HBox(8);
        tags.setAlignment(Pos.CENTER_LEFT);

        if (r.contentPreview() != null && !r.contentPreview().isEmpty()) {
            Label fileCount = new Label(r.contentPreview());
            fileCount.getStyleClass().add("srch-card-file-count");
            tags.getChildren().add(fileCount);
        }

        if (r.lastModifiedAt() != null) {
            Label sep = new Label("•");
            sep.setStyle("-fx-text-fill:#4B5563;");
            Label timeLabel = new Label("Created: " + TIME_FMT.format(r.lastModifiedAt()));
            timeLabel.getStyleClass().add("srch-card-time");
            tags.getChildren().addAll(sep, timeLabel);
        }

        VBox meta = new VBox(6, nameLabel, tags);
        HBox.setHgrow(meta, Priority.ALWAYS);

        HBox top = new HBox(14, iconBox, meta);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(0, top);
        card.getStyleClass().add("srch-result-card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setOnMouseClicked(e -> { if (onResultSelected != null) onResultSelected.accept(r); });
        return card;
    }

    private static StackPane makeTypeBox(String icon) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("srch-card-type-icon");
        StackPane box = new StackPane(iconLabel);
        box.getStyleClass().add("srch-card-type-box");
        return box;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void closeCurrentWindow() {
        if (onHomeRequested != null) {
            onHomeRequested.run();
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
