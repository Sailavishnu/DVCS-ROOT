package com.dvcs.client.dashboard.profile;

import com.dvcs.client.auth.db.MongoConnection;
import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.auth.service.UserService;
import com.dvcs.client.controller.LoginSignupController;
import com.dvcs.client.dashboard.MainLayoutController;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.bson.types.ObjectId;

public final class ProfileController {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

    // Sidebar
    @FXML private VBox sidebarNav;
    @FXML private Label sidebarInitialsLabel;
    @FXML private Label sidebarUserNameLabel;

    // Topbar
    @FXML private TextField topSearchField;

    // Header card
    @FXML private Label avatarInitialsLabel;
    @FXML private Label nameValueLabel;
    @FXML private Label usernameValueLabel;
    @FXML private Label bioLabel;

    // Workspaces
    @FXML private GridPane popularWorkspaceGrid;
    @FXML private VBox popularWorkspaceEmptyBox;
    @FXML private Label popularWorkspaceEmptyLabel;

    // Recent activity
    @FXML private VBox activityFeed;
    @FXML private ScrollPane activityFeedScrollPane;

    // Profile content area (for analytics toggle)
    @FXML private ScrollPane profileScrollPane;

    // Edit modal
    @FXML private StackPane editProfileModal;
    @FXML private TextField editNameField;
    @FXML private TextField editUsernameField;
    @FXML private TextArea editBioField;
    @FXML private VBox passwordFieldsBox;
    @FXML private Button togglePasswordButton;
    @FXML private PasswordField passwordPopupOldField;
    @FXML private PasswordField passwordPopupNewField;
    @FXML private PasswordField passwordPopupConfirmField;

    private ProfileService profileService;
    private ObjectId currentUserId;
    private Runnable onSearchRequested;
    private Runnable onNotificationRequested;
    private Runnable onProfileRequested;
    private Runnable onCollaboratorsRequested;

    private ProfileService.ProfileViewModel currentProfile;
    private ScrollPane analyticsPanel;
    private boolean analyticsShown = false;

    @FXML
    private void initialize() {
        buildSidebarNav();
        setEditMode(false);
    }

    private Runnable onHomeRequested;

    public void configure(
            ProfileService profileService,
            ObjectId currentUserId,
            Runnable onHomeRequested,
            Runnable onSearchRequested,
            Runnable onNotificationRequested,
            Runnable onProfileRequested,
            Runnable onCollaboratorsRequested) {
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.currentUserId = Objects.requireNonNull(currentUserId, "currentUserId");
        this.onHomeRequested = onHomeRequested;
        this.onSearchRequested = onSearchRequested;
        this.onNotificationRequested = onNotificationRequested;
        this.onProfileRequested = onProfileRequested;
        this.onCollaboratorsRequested = onCollaboratorsRequested;

        if (topSearchField != null) {
            topSearchField.setOnAction(e -> { if (onSearchRequested != null) onSearchRequested.run(); });
        }

        reloadProfile();
    }

    // ── FXML handlers ─────────────────────────────────────────────────────

    @FXML
    private void onRefreshClick(MouseEvent event) {
        reloadProfile();
    }

    @FXML
    private void onTopNotifClick(MouseEvent event) {

        if (onNotificationRequested != null) onNotificationRequested.run();
    }

    @FXML
    private void onTopProfileClick(MouseEvent event) {
        // already on profile – no-op
    }

    @FXML
    private void onEditProfile() {
        if (currentProfile == null) return;
        if (editNameField != null) editNameField.setText(currentProfile.name() == null ? "" : currentProfile.name());
        if (editUsernameField != null) editUsernameField.setText(currentProfile.username());
        if (editBioField != null) editBioField.setText(currentProfile.bio() == null ? "" : currentProfile.bio());
        if (passwordFieldsBox != null) {
            passwordFieldsBox.setVisible(false);
            passwordFieldsBox.setManaged(false);
        }
        if (togglePasswordButton != null) togglePasswordButton.setText("Change Password");
        clearPasswordFields();
        setEditMode(true);
    }

    @FXML
    private void onCancelEdit() {
        setEditMode(false);
    }

    @FXML
    private void onSaveProfile() {
        if (profileService == null || currentUserId == null) return;

        String bio = editBioField != null ? editBioField.getText() : null;
        ProfileService.UpdateResult basicResult = profileService.updateProfile(
                currentUserId, editNameField.getText(), editUsernameField.getText(), bio);
        if (!basicResult.success()) { showError(basicResult.message()); return; }

        if (passwordFieldsBox != null && passwordFieldsBox.isVisible()) {
            ProfileService.UpdateResult passResult = profileService.changePassword(
                    currentUserId,
                    passwordPopupOldField.getText(),
                    passwordPopupNewField.getText(),
                    passwordPopupConfirmField.getText());
            if (!passResult.success()) { showError(passResult.message()); return; }
        }

        showInfo("Profile updated successfully");
        reloadProfile();
    }

    @FXML
    private void onTogglePasswordFields() {
        if (passwordFieldsBox == null) return;
        boolean vis = passwordFieldsBox.isVisible();
        passwordFieldsBox.setVisible(!vis);
        passwordFieldsBox.setManaged(!vis);
        if (togglePasswordButton != null)
            togglePasswordButton.setText(vis ? "Change Password" : "Hide Password Section");
        if (vis) clearPasswordFields();
    }

    @FXML
    private void onLogout() {
        Stage profileStage = resolveStage();
        if (profileStage == null) { showError("Logout failed. Please try again."); return; }
        Stage targetStage = (profileStage.getOwner() instanceof Stage os) ? os : profileStage;
        try {
            showLandingPageOnStage(targetStage);
            if (targetStage != profileStage) profileStage.close();
        } catch (Exception e) {
            showError("Logout failed. Please try again.");
        }
    }

    @FXML
    private void onFabClick() {
        showInfo("New workspace creation coming soon.");
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
            {"⚙", "Settings"},
            {"📊", "Analytics"}
        };
        boolean[] active   = {false, false, false, false, !analyticsShown, analyticsShown};
        boolean[] disabled = {false, false, false, false, false, false};

        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            HBox item = buildNavItem(items[i][0], items[i][1], active[i], disabled[i]);
            if (!disabled[i]) item.setOnMouseClicked(e -> onNavItemClicked(idx));
            sidebarNav.getChildren().add(item);
        }
    }

    private HBox buildNavItem(String icon, String label, boolean isActive, boolean isDisabled) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("prof-nav-item-icon");

        Label textLabel = new Label(label);
        textLabel.getStyleClass().add("prof-nav-item-text");

        HBox item = new HBox(10, iconLabel, textLabel);
        item.getStyleClass().add("prof-nav-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(10, 16, 10, 16));
        item.setMaxWidth(Double.MAX_VALUE);

        if (isActive)   item.getStyleClass().add("prof-nav-active");
        if (isDisabled) item.getStyleClass().add("prof-nav-disabled");
        return item;
    }

    private void onNavItemClicked(int index) {
        switch (index) {
            case 0, 1 -> closeCurrentWindow();
            case 2 -> { if (onNotificationRequested != null) onNotificationRequested.run(); }
            case 3 -> { if (onCollaboratorsRequested != null) onCollaboratorsRequested.run(); }
            case 4 -> showProfileView();
            case 5 -> showAnalyticsView();
            default -> { }
        }
    }

    // ── Analytics panel ───────────────────────────────────────────────────

    private void showAnalyticsView() {
        if (currentProfile == null) return;
        if (profileScrollPane == null) return;

        // Rebuild each time to reflect fresh data
        if (analyticsPanel != null) {
            VBox parent = (VBox) profileScrollPane.getParent();
            parent.getChildren().remove(analyticsPanel);
        }
        analyticsPanel = buildAnalyticsScrollPane();
        VBox parent = (VBox) profileScrollPane.getParent();
        VBox.setVgrow(analyticsPanel, Priority.ALWAYS);
        parent.getChildren().add(analyticsPanel);

        profileScrollPane.setVisible(false);
        profileScrollPane.setManaged(false);
        analyticsPanel.setVisible(true);
        analyticsPanel.setManaged(true);
        analyticsShown = true;
        buildSidebarNav();
    }

    private void showProfileView() {
        if (analyticsPanel != null) {
            analyticsPanel.setVisible(false);
            analyticsPanel.setManaged(false);
        }
        if (profileScrollPane != null) {
            profileScrollPane.setVisible(true);
            profileScrollPane.setManaged(true);
        }
        analyticsShown = false;
        buildSidebarNav();
    }

    private ScrollPane buildAnalyticsScrollPane() {
        VBox content = new VBox(24);
        content.setPadding(new Insets(28, 28, 80, 28));
        content.setMaxWidth(Double.MAX_VALUE);
        content.setStyle("-fx-background-color: transparent;");

        content.getChildren().addAll(
            buildAnalyticsHeader(),
            buildStatTiles(),
            buildStorageSection(),
            buildGithubHeatmap(),
            buildCommitsAreaChart(),
            buildBottomRow()
        );

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("prof-scroll");
        sp.getStylesheets().add(
            java.util.Objects.requireNonNull(
                getClass().getResource("/css/analytics.css")).toExternalForm());
        VBox.setVgrow(sp, Priority.ALWAYS);
        return sp;
    }

    private HBox buildAnalyticsHeader() {
        VBox text = new VBox(4);
        Label title = new Label("📊  Analytics Dashboard");
        title.setStyle("-fx-text-fill: #e8fff2; -fx-font-size: 22px; -fx-font-weight: bold;");
        Label sub = new Label("Storage, commits & workspace insights for your account");
        sub.setStyle("-fx-text-fill: rgba(0,255,136,0.55); -fx-font-size: 12px;");
        text.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(text, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private HBox buildStatTiles() {
        HBox row = new HBox(16);
        row.setMaxWidth(Double.MAX_VALUE);

        row.getChildren().addAll(
            statTile("TOTAL COMMITS",   String.valueOf(currentProfile.totalCommits()),   "#00ff88"),
            statTile("FILES",           String.valueOf(currentProfile.totalFiles()),      "#3b82f6"),
            statTile("FOLDERS",         String.valueOf(currentProfile.totalFolders()),    "#a78bfa"),
            statTile("WORKSPACES",      String.valueOf(currentProfile.totalWorkspaces()), "#f59e0b")
        );
        for (Node n : row.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        return row;
    }

    private VBox statTile(String label, String value, String color) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: rgba(148,163,184,0.75); -fx-font-size: 10px; -fx-font-weight: bold;");
        Label val = new Label(value);
        val.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 32px; -fx-font-weight: bold;");
        VBox tile = new VBox(6, lbl, val);
        tile.setStyle("-fx-background-color: rgba(15,30,20,0.7); "
            + "-fx-border-color: rgba(0,255,136,0.12); -fx-border-width: 1; "
            + "-fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 18 22;");
        tile.setAlignment(Pos.CENTER_LEFT);
        return tile;
    }

    private VBox buildStorageSection() {
        long quota    = currentProfile.storageQuota() != null ? currentProfile.storageQuota() : 5_368_709_120L;
        long usedBytes = (long) currentProfile.totalFiles() * 1_048_576L;
        double pct    = quota > 0 ? (double) usedBytes / quota : 0;
        boolean over  = pct > 0.8;

        // Gauge bar
        ProgressBar bar = new ProgressBar(Math.min(pct, 1.0));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(18);
        bar.setStyle(over
            ? "-fx-accent: #ef4444; -fx-background-radius: 8; -fx-background-color: rgba(255,255,255,0.06);"
            : "-fx-accent: #00ff88; -fx-background-radius: 8; -fx-background-color: rgba(255,255,255,0.06);");

        // Labels
        String usedStr  = formatBytes(usedBytes);
        String quotaStr = formatBytes(quota);
        String pctStr   = String.format("%.1f%%", pct * 100);

        Label usedLbl = new Label(usedStr + " used of " + quotaStr);
        usedLbl.setStyle("-fx-text-fill: #b0d4c0; -fx-font-size: 13px;");

        Label pctLbl = new Label(pctStr);
        pctLbl.setStyle("-fx-text-fill: " + (over ? "#ef4444" : "#00ff88")
            + "; -fx-font-size: 28px; -fx-font-weight: bold;");

        Label limitLbl = over
            ? new Label("⚠  Storage limit exceeded — consider removing unused files")
            : new Label("✓  Storage within limit");
        limitLbl.setStyle("-fx-text-fill: " + (over ? "#ef4444" : "#4ade80")
            + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        // Left: percentage + warning; Right: exact bytes
        VBox leftBox = new VBox(6, pctLbl, limitLbl);
        leftBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(leftBox, Priority.ALWAYS);

        // Fine print breakdown
        VBox rightBox = new VBox(4);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        addFineRow(rightBox, "Used",      usedStr,  over ? "#ef4444" : "#e8fff2");
        addFineRow(rightBox, "Quota",     quotaStr, "#9ab7a8");
        addFineRow(rightBox, "Free",      formatBytes(Math.max(0, quota - usedBytes)), "#4ade80");

        HBox middle = new HBox(24, leftBox, rightBox);
        middle.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(14);
        section.getStyleClass().add("chart-section");
        Label sTitle = new Label("📁  Storage Usage");
        sTitle.setStyle("-fx-text-fill: #e8fff2; -fx-font-size: 15px; -fx-font-weight: bold;");
        Label sSub = new Label("Estimated based on file count (1 MB avg per file)");
        sSub.setStyle("-fx-text-fill: rgba(148,163,184,0.6); -fx-font-size: 11px;");
        section.getChildren().addAll(sTitle, sSub, middle, bar);
        return section;
    }

    private void addFineRow(VBox box, String key, String val, String valColor) {
        Label k = new Label(key + ": ");
        k.setStyle("-fx-text-fill: rgba(148,163,184,0.6); -fx-font-size: 11px;");
        Label v = new Label(val);
        v.setStyle("-fx-text-fill: " + valColor + "; -fx-font-size: 11px; -fx-font-weight: bold;");
        HBox row = new HBox(k, v);
        row.setAlignment(Pos.CENTER_RIGHT);
        box.getChildren().add(row);
    }

    private VBox buildCommitsAreaChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        xAxis.setTickLabelsVisible(true);
        xAxis.setTickMarkVisible(false);
        yAxis.setLabel("Commits");
        yAxis.setMinorTickVisible(false);
        yAxis.setTickUnit(1);

        AreaChart<String, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(false);
        chart.setPrefHeight(240);
        chart.setMaxWidth(Double.MAX_VALUE);
        chart.setStyle(
            ".chart-series-area-fill { -fx-fill: rgba(0,255,136,0.15); }" +
            ".chart-series-area-line { -fx-stroke: #00ff88; -fx-stroke-width: 2px; }" +
            ".chart-symbol { -fx-background-color: #00ff88, #0a1a10; -fx-background-radius: 4px; }");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        List<ProfileService.DayCommit> days = currentProfile.activityDays();
        if (days != null) {
            // Show every 3rd label to avoid clutter
            int i = 0;
            for (ProfileService.DayCommit d : days) {
                String lbl = (i % 3 == 0)
                    ? (d.date().getMonthValue() + "/" + d.date().getDayOfMonth())
                    : "";
                series.getData().add(new XYChart.Data<>(lbl, d.commitCount()));
                i++;
            }
        }
        chart.getData().add(series);

        for (XYChart.Data<String, Number> item : series.getData()) {
            if (item.getNode() != null)
                Tooltip.install(item.getNode(),
                    new Tooltip(item.getXValue() + ": " + item.getYValue() + " commits"));
        }

        // Insights row
        long total = days == null ? 0 : days.stream().mapToLong(ProfileService.DayCommit::commitCount).sum();
        long peak  = days == null ? 0 : days.stream().mapToLong(ProfileService.DayCommit::commitCount).max().orElse(0);
        double avg = days == null || days.isEmpty() ? 0 : (double) total / days.size();

        HBox insights = new HBox(24);
        insights.setPadding(new Insets(8, 0, 0, 0));
        insights.getChildren().addAll(
            insightChip("Total", String.valueOf(total), "#00ff88"),
            insightChip("Peak day", String.valueOf(peak), "#3b82f6"),
            insightChip("Avg / day", String.format("%.1f", avg), "#a78bfa")
        );

        VBox section = new VBox(12);
        section.getStyleClass().add("chart-section");
        Label sTitle = new Label("📈  Commits Over 30 Days");
        sTitle.setStyle("-fx-text-fill: #e8fff2; -fx-font-size: 15px; -fx-font-weight: bold;");
        Label sSub = new Label("Daily commit activity across all your workspaces");
        sSub.setStyle("-fx-text-fill: rgba(148,163,184,0.6); -fx-font-size: 11px;");
        section.getChildren().addAll(sTitle, sSub, chart, insights);
        return section;
    }

    private HBox insightChip(String label, String value, String color) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: rgba(148,163,184,0.6); -fx-font-size: 10px; -fx-font-weight: bold;");
        Label val = new Label(value);
        val.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 20px; -fx-font-weight: bold;");
        VBox chip = new VBox(2, lbl, val);
        chip.setStyle("-fx-background-color: rgba(15,30,20,0.5); -fx-border-color: rgba(0,255,136,0.1);"
            + "-fx-border-width:1; -fx-border-radius:10; -fx-background-radius:10; -fx-padding: 10 18;");
        return new HBox(chip);
    }

    private HBox buildBottomRow() {
        VBox workspaceChart = buildWorkspaceBarChart();
        HBox.setHgrow(workspaceChart, Priority.ALWAYS);
        HBox row = new HBox(20, workspaceChart);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private VBox buildWorkspaceBarChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        xAxis.setLabel("Workspace");
        yAxis.setLabel("Commits");
        yAxis.setMinorTickVisible(false);

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(260);
        chart.setMaxWidth(Double.MAX_VALUE);
        chart.setBarGap(4);
        chart.setCategoryGap(12);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        List<ProfileService.PopularWorkspace> wsList = currentProfile.popularWorkspaces();
        if (wsList != null) {
            for (ProfileService.PopularWorkspace ws : wsList) {
                String name = ws.workspaceName().length() > 12
                    ? ws.workspaceName().substring(0, 12) + "…"
                    : ws.workspaceName();
                series.getData().add(new XYChart.Data<>(name, ws.commitCount()));
            }
        }
        chart.getData().add(series);

        VBox section = new VBox(12);
        section.getStyleClass().add("chart-section");
        Label sTitle = new Label("🏗  Workspace Activity");
        sTitle.setStyle("-fx-text-fill: #e8fff2; -fx-font-size: 15px; -fx-font-weight: bold;");
        Label sSub = new Label("Commits per workspace");
        sSub.setStyle("-fx-text-fill: rgba(148,163,184,0.6); -fx-font-size: 11px;");
        section.getChildren().addAll(sTitle, sSub, chart);
        return section;
    }

    private VBox buildGithubHeatmap() {
        List<ProfileService.DayCommit> days = currentProfile.activityDays();

        // Build lookup: date → commit count
        Map<LocalDate, Integer> countMap = new HashMap<>();
        if (days != null) {
            for (ProfileService.DayCommit dc : days) countMap.put(dc.date(), dc.commitCount());
        }

        // Start from Monday of the week containing (today - 29 days)
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(29);
        // Rewind to Monday
        while (start.getDayOfWeek() != DayOfWeek.MONDAY) start = start.minusDays(1);

        // Columns = number of weeks needed to cover start → today
        long totalDays = start.until(today, java.time.temporal.ChronoUnit.DAYS) + 1;
        int numWeeks = (int) Math.ceil(totalDays / 7.0) + 1;

        // Row labels (Mon–Sun)
        String[] dayLabels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

        HBox gridArea = new HBox(4);
        gridArea.setAlignment(Pos.TOP_LEFT);

        // Day-of-week label column
        VBox rowLabels = new VBox(4);
        rowLabels.setAlignment(Pos.TOP_LEFT);
        rowLabels.setPadding(new Insets(0, 4, 0, 0));
        for (String dl : dayLabels) {
            Label lbl = new Label(dl);
            lbl.setStyle("-fx-text-fill: rgba(148,163,184,0.45); -fx-font-size: 9px;");
            lbl.setPrefHeight(16);
            lbl.setMinHeight(16);
            rowLabels.getChildren().add(lbl);
        }
        gridArea.getChildren().add(rowLabels);

        // Week columns
        for (int w = 0; w < numWeeks; w++) {
            VBox col = new VBox(4);
            col.setAlignment(Pos.TOP_CENTER);
            for (int d = 0; d < 7; d++) {
                LocalDate date = start.plusDays((long) w * 7 + d);
                Region cell = new Region();
                cell.setPrefSize(16, 16);
                cell.setMinSize(16, 16);
                if (date.isAfter(today)) {
                    cell.getStyleClass().addAll("heatmap-cell", "heatmap-level-0");
                    cell.setOpacity(0.2);
                } else {
                    int cnt = countMap.getOrDefault(date, 0);
                    cell.getStyleClass().addAll("heatmap-cell", heatLevel(cnt));
                    String tip = date + ": " + cnt + (cnt == 1 ? " commit" : " commits");
                    Tooltip.install(cell, new Tooltip(tip));
                }
                col.getChildren().add(cell);
            }
            gridArea.getChildren().add(col);
        }

        // Legend
        HBox legend = new HBox(6);
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.setPadding(new Insets(10, 0, 0, 0));
        String[] levels = {"0", "1-2", "3-6", "7-9", "10+"};
        String[] names  = {"heatmap-level-0","heatmap-level-1","heatmap-level-2","heatmap-level-3","heatmap-level-4"};
        Label lessLbl = new Label("Less");
        lessLbl.setStyle("-fx-text-fill: rgba(148,163,184,0.45); -fx-font-size: 10px;");
        legend.getChildren().add(lessLbl);
        for (int i = 0; i < names.length; i++) {
            Region cell = new Region();
            cell.setPrefSize(13, 13);
            cell.setMinSize(13, 13);
            cell.getStyleClass().addAll("heatmap-cell", names[i]);
            Tooltip.install(cell, new Tooltip(levels[i] + " commits/day"));
            legend.getChildren().add(cell);
        }
        Label moreLbl = new Label("More");
        moreLbl.setStyle("-fx-text-fill: rgba(148,163,184,0.45); -fx-font-size: 10px;");
        legend.getChildren().add(moreLbl);

        VBox section = new VBox(12);
        section.getStyleClass().add("chart-section");
        Label sTitle = new Label("🔥  Contribution Activity");
        sTitle.setStyle("-fx-text-fill: #e8fff2; -fx-font-size: 15px; -fx-font-weight: bold;");
        Label sSub = new Label("Hover a cell to see daily commit count");
        sSub.setStyle("-fx-text-fill: rgba(148,163,184,0.6); -fx-font-size: 11px;");
        section.getChildren().addAll(sTitle, sSub, gridArea, legend);
        return section;
    }

    private static String heatLevel(int count) {
        if (count <= 0)  return "heatmap-level-0";
        if (count <= 2)  return "heatmap-level-1";
        if (count <= 6)  return "heatmap-level-2";
        if (count <= 9)  return "heatmap-level-3";
        return "heatmap-level-4";
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1_073_741_824L) return String.format("%.2f GB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576L)     return String.format("%.1f MB", bytes / 1_048_576.0);
        if (bytes >= 1024)           return String.format("%.1f KB", bytes / 1024.0);
        return bytes + " B";
    }

    // ── Data rendering ────────────────────────────────────────────────────

    private void reloadProfile() {
        if (profileService == null || currentUserId == null) return;
        currentProfile = profileService.loadProfile(currentUserId);
        renderProfile(currentProfile);
        setEditMode(false);
    }

    private void renderProfile(ProfileService.ProfileViewModel p) {
        if (p == null) return;

        String name = p.name() == null || p.name().isBlank() ? "Not set" : p.name();
        if (avatarInitialsLabel != null)
            avatarInitialsLabel.setText(p.initials());
        if (nameValueLabel != null)     nameValueLabel.setText(name);
        if (usernameValueLabel != null) usernameValueLabel.setText("@" + p.username());
        if (bioLabel != null)           bioLabel.setText(p.bio() == null ? "" : p.bio());

        if (sidebarUserNameLabel != null) sidebarUserNameLabel.setText(p.username());
        if (sidebarInitialsLabel != null) sidebarInitialsLabel.setText(p.initials());

        renderPopularWorkspaces(p.popularWorkspaces());
        renderRecentActivity(p.recentCommits());
    }

    private void renderPopularWorkspaces(List<ProfileService.PopularWorkspace> workspaces) {
        if (popularWorkspaceGrid == null) return;
        popularWorkspaceGrid.getChildren().clear();

        List<ProfileService.PopularWorkspace> list = workspaces == null ? List.of() : workspaces;
        if (list.isEmpty()) {
            if (popularWorkspaceEmptyBox != null) {
                popularWorkspaceEmptyBox.setVisible(true);
                popularWorkspaceEmptyBox.setManaged(true);
            }
            return;
        }
        if (popularWorkspaceEmptyBox != null) {
            popularWorkspaceEmptyBox.setVisible(false);
            popularWorkspaceEmptyBox.setManaged(false);
        }

        int index = 0;
        for (ProfileService.PopularWorkspace ws : list) {
            if (index >= 6) break;
            VBox card = new VBox(4);
            card.getStyleClass().add("profile-workspace-item");
            Label nameLabel = new Label(ws.workspaceName());
            nameLabel.getStyleClass().add("profile-workspace-name");
            Label commitLabel = new Label(ws.commitCount() + " commits");
            commitLabel.getStyleClass().add("profile-workspace-commits");
            card.getChildren().addAll(nameLabel, commitLabel);
            popularWorkspaceGrid.add(card, index % 2, index / 2);
            index++;
        }
    }

    private void renderRecentActivity(List<ProfileService.RecentCommit> commits) {
        if (activityFeed == null) return;
        activityFeed.getChildren().clear();

        if (commits == null || commits.isEmpty()) {
            Label empty = new Label("No recent commits");
            empty.getStyleClass().add("prof-activity-sub");
            empty.setPadding(new Insets(12, 16, 12, 16));
            activityFeed.getChildren().add(empty);
            return;
        }

        for (ProfileService.RecentCommit commit : commits) {
            Label iconLabel = new Label("⑂");
            iconLabel.getStyleClass().add("prof-activity-icon");

            Label msgLabel = new Label(commit.message().isBlank() ? "(no message)" : commit.message());
            msgLabel.getStyleClass().add("prof-activity-msg");
            msgLabel.setWrapText(true);
            HBox.setHgrow(msgLabel, Priority.ALWAYS);

            String sub = commit.workspaceName()
                    + (commit.time() != null ? " • " + formatRelative(commit.time()) : "");
            Label subLabel = new Label(sub);
            subLabel.getStyleClass().add("prof-activity-sub");

            VBox textBox = new VBox(2, msgLabel, subLabel);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            HBox item = new HBox(10, iconLabel, textBox);
            item.getStyleClass().add("prof-activity-item");
            item.setPadding(new Insets(10, 16, 10, 16));
            activityFeed.getChildren().add(item);
        }
    }


    // ── Helpers ───────────────────────────────────────────────────────────

    private void setEditMode(boolean editing) {
        if (editProfileModal != null) {
            editProfileModal.setVisible(editing);
            editProfileModal.setManaged(editing);
        }
    }

    private void clearPasswordFields() {
        if (passwordPopupOldField != null)     passwordPopupOldField.clear();
        if (passwordPopupNewField != null)     passwordPopupNewField.clear();
        if (passwordPopupConfirmField != null) passwordPopupConfirmField.clear();
    }

    private Stage resolveStage() {
        for (Window w : Window.getWindows()) {
            if (w.isFocused() && w instanceof Stage s) return s;
        }
        return null;
    }

    private void closeCurrentWindow() {
        if (onHomeRequested != null) {
            onHomeRequested.run();
        }
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        Stage s = resolveStage();
        if (s != null) { alert.initOwner(s); alert.initModality(Modality.WINDOW_MODAL); }
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Error");
        Stage s = resolveStage();
        if (s != null) { alert.initOwner(s); alert.initModality(Modality.WINDOW_MODAL); }
        alert.showAndWait();
    }

    private static String formatRelative(Instant t) {
        if (t == null) return "";
        long s = Instant.now().getEpochSecond() - t.getEpochSecond();
        if (s < 60) return "Just now";
        if (s < 3600) return (s / 60) + "m ago";
        if (s < 86400) return (s / 3600) + "h ago";
        return (s / 86400) + "d ago";
    }

    private static String formatStorage(long mb) {
        if (mb >= 1024) return String.format("%.1f GB", mb / 1024.0);
        return mb + " MB";
    }

    private void showLandingPageOnStage(Stage stage) throws Exception {
        URL fxmlUrl = MainLayoutController.class.getResource("/fxml/landing.fxml");
        if (fxmlUrl == null) throw new IllegalStateException("landing.fxml not found");
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent landingRoot = loader.load();
        stage.setFullScreen(false);
        stage.setScene(new Scene(landingRoot));
        stage.setMaximized(true);
        stage.show();
    }

    private static UserService createUserService() {
        String dbName = System.getenv("MONGODB_DB");
        if (dbName == null || dbName.isBlank()) dbName = "DVCS";
        com.mongodb.client.MongoDatabase database = MongoConnection.getDatabase(dbName);
        return new UserService(new UserRepository(database));
    }
}
