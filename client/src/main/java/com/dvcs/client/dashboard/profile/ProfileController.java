package com.dvcs.client.dashboard.profile;

import com.dvcs.client.auth.db.MongoConnection;
import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.auth.service.UserService;
import com.dvcs.client.controller.LoginSignupController;
import com.dvcs.client.dashboard.MainLayoutController;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
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
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.bson.types.ObjectId;

public final class ProfileController {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());
    private static final long DEFAULT_STORAGE_QUOTA_BYTES = 1_610_612_736L; // 1.5 GiB

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
    private VBox analyticsContent;
    private VBox analyticsStorageSection;
    private VBox analyticsUsageSection;
    private VBox analyticsCreationSection;
    private VBox analyticsHeatmapSection;
    private VBox analyticsCommitsSection;
    private VBox analyticsWorkspaceSection;
    private VBox analyticsInsightsSection;
    private VBox analyticsRecentActivitySection;
    private VBox analyticsLargestFilesSection;
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
        Label iconLabel = new Label(navGlyphFor(label));
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

    private String navGlyphFor(String label) {
        return switch (label) {
            case "Home" -> "HOME";
            case "Workspaces" -> "GRID";
            case "Notifications" -> "BELL";
            case "Collaborators" -> "TEAM";
            case "Settings" -> "SET";
            case "Analytics" -> "DATA";
            default -> label == null ? "" : label.substring(0, Math.min(4, label.length())).toUpperCase();
        };
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
        content.getStyleClass().add("analytics-dashboard");
        content.setPadding(new Insets(28, 28, 80, 28));
        content.setMaxWidth(Double.MAX_VALUE);
        this.analyticsContent = content;

        this.analyticsStorageSection = buildRedesignedStorageSection();
        this.analyticsCommitsSection = buildRedesignedCommitsSection();
        this.analyticsHeatmapSection = buildRedesignedHeatmapSection();
        this.analyticsWorkspaceSection = buildRedesignedWorkspaceSection();
        this.analyticsInsightsSection = buildRedesignedInsightsSection();
        this.analyticsRecentActivitySection = buildRedesignedRecentActivitySection();
        this.analyticsLargestFilesSection = buildRedesignedLargestFilesSection();

        HBox chartsRow = new HBox(20, analyticsCommitsSection, analyticsHeatmapSection);
        HBox.setHgrow(analyticsCommitsSection, Priority.ALWAYS);
        HBox.setHgrow(analyticsHeatmapSection, Priority.ALWAYS);

        HBox workspaceRow = new HBox(20, analyticsWorkspaceSection, analyticsInsightsSection);
        HBox.setHgrow(analyticsWorkspaceSection, Priority.ALWAYS);
        HBox.setHgrow(analyticsInsightsSection, Priority.ALWAYS);

        HBox feedRow = new HBox(20, analyticsRecentActivitySection, analyticsLargestFilesSection);
        HBox.setHgrow(analyticsRecentActivitySection, Priority.ALWAYS);
        HBox.setHgrow(analyticsLargestFilesSection, Priority.ALWAYS);

        content.getChildren().addAll(
                buildRedesignedAnalyticsHeader(),
                buildRedesignedSummaryGrid(),
                analyticsStorageSection,
                chartsRow,
                workspaceRow,
                feedRow);

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().addAll("prof-scroll", "analytics-scroll");
        sp.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/analytics.css")).toExternalForm());
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

        Button downloadButton = new Button("Download PDF");
        downloadButton.setFocusTraversable(false);
        downloadButton.setStyle(
            "-fx-background-color: linear-gradient(to right, #00ff88, #14b8a6);"
            + "-fx-text-fill: #042014; -fx-font-weight: bold; -fx-padding: 10 18;"
            + "-fx-background-radius: 999; -fx-cursor: hand;");
        downloadButton.setOnAction(e -> onDownloadAnalyticsPdf());

        HBox header = new HBox(text, spacer, downloadButton);
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
        long quota    = currentProfile.storageQuota() != null
                ? currentProfile.storageQuota()
                : DEFAULT_STORAGE_QUOTA_BYTES;
        long usedBytes = (long) currentProfile.totalFiles() * 1_048_576L;
        double pct    = quota > 0 ? (double) usedBytes / quota : 0;
        boolean over  = pct > 1.0;

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
        BarChart<String, Number> storageChart = createStorageBarChart(usedBytes, quota, over);

        VBox section = new VBox(14);
        section.getStyleClass().add("chart-section");
        Label sTitle = new Label("📁  Storage Usage");
        sTitle.setStyle("-fx-text-fill: #e8fff2; -fx-font-size: 15px; -fx-font-weight: bold;");
        Label sSub = new Label("Estimated based on file count (1 MB avg per file)");
        sSub.setStyle("-fx-text-fill: rgba(148,163,184,0.6); -fx-font-size: 11px;");
        section.getChildren().addAll(sTitle, sSub, middle, storageChart, usedLbl, bar);
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

    private BarChart<String, Number> createStorageBarChart(long usedBytes, long quotaBytes, boolean over) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("GB");
        yAxis.setMinorTickVisible(false);

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCategoryGap(36);
        chart.setBarGap(18);
        chart.setPrefHeight(220);
        chart.setMaxWidth(Double.MAX_VALUE);

        double usedGb = usedBytes / 1_073_741_824.0;
        double quotaGb = quotaBytes / 1_073_741_824.0;

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        XYChart.Data<String, Number> usedData = new XYChart.Data<>("Used", usedGb);
        XYChart.Data<String, Number> quotaData = new XYChart.Data<>("Quota", quotaGb);
        series.getData().addAll(usedData, quotaData);
        chart.getData().add(series);

        Platform.runLater(() -> {
            if (usedData.getNode() != null) {
                usedData.getNode().setStyle("-fx-bar-fill: " + (over ? "#ef4444" : "#00ff88") + ";");
            }
            if (quotaData.getNode() != null) {
                quotaData.getNode().setStyle("-fx-bar-fill: rgba(148,163,184,0.45);");
            }
        });

        return chart;
    }

    private VBox buildUsageSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("chart-section");

        Label title = new Label("Daily App Usage");
        title.setStyle("-fx-text-fill: #e8fff2; -fx-font-size: 15px; -fx-font-weight: bold;");
        Label sub = new Label("Daily interaction volume from your recorded activity");
        sub.setStyle("-fx-text-fill: rgba(148,163,184,0.6); -fx-font-size: 11px;");

        LineChart<String, Number> chart = createUsageLineChart();
        section.getChildren().addAll(title, sub, chart);
        return section;
    }

    private LineChart<String, Number> createUsageLineChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Actions");
        yAxis.setMinorTickVisible(false);
        yAxis.setTickUnit(1);

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(true);
        chart.setPrefHeight(240);
        chart.setMaxWidth(Double.MAX_VALUE);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        List<ProfileService.DayActivity> days = currentProfile.usageDays();
        int start = Math.max(0, days.size() - 14);
        for (int i = start; i < days.size(); i++) {
            ProfileService.DayActivity day = days.get(i);
            String label = day.date().getMonthValue() + "/" + day.date().getDayOfMonth();
            XYChart.Data<String, Number> point = new XYChart.Data<>(label, day.activityCount());
            series.getData().add(point);
        }
        chart.getData().add(series);
        installChartTooltips(series, " actions");
        return chart;
    }

    private VBox buildCreationSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("chart-section");

        Label title = new Label("Daily Creation Trends");
        title.setStyle("-fx-text-fill: #e8fff2; -fx-font-size: 15px; -fx-font-weight: bold;");
        Label sub = new Label("Workspaces, folders and files created per day");
        sub.setStyle("-fx-text-fill: rgba(148,163,184,0.6); -fx-font-size: 11px;");

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Created");
        yAxis.setMinorTickVisible(false);
        yAxis.setTickUnit(1);

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(true);
        chart.setPrefHeight(260);
        chart.setMaxWidth(Double.MAX_VALUE);

        XYChart.Series<String, Number> workspaceSeries = new XYChart.Series<>();
        workspaceSeries.setName("Workspaces");
        XYChart.Series<String, Number> folderSeries = new XYChart.Series<>();
        folderSeries.setName("Folders");
        XYChart.Series<String, Number> fileSeries = new XYChart.Series<>();
        fileSeries.setName("Files");

        List<ProfileService.CreationDay> days = currentProfile.creationDays();
        int start = Math.max(0, days.size() - 14);
        for (int i = start; i < days.size(); i++) {
            ProfileService.CreationDay day = days.get(i);
            String label = day.date().getMonthValue() + "/" + day.date().getDayOfMonth();
            workspaceSeries.getData().add(new XYChart.Data<>(label, day.workspaceCount()));
            folderSeries.getData().add(new XYChart.Data<>(label, day.folderCount()));
            fileSeries.getData().add(new XYChart.Data<>(label, day.fileCount()));
        }

        chart.getData().addAll(workspaceSeries, folderSeries, fileSeries);
        installChartTooltips(workspaceSeries, " workspaces");
        installChartTooltips(folderSeries, " folders");
        installChartTooltips(fileSeries, " files");

        section.getChildren().addAll(title, sub, chart);
        return section;
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
        List<ProfileService.DayCommit> days = currentProfile.commitActivityDays();
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

        installChartTooltips(series, " commits");

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
        this.analyticsWorkspaceSection = buildWorkspaceBarChart();
        HBox.setHgrow(analyticsWorkspaceSection, Priority.ALWAYS);
        HBox row = new HBox(20, analyticsWorkspaceSection);
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

    private void onDownloadAnalyticsPdf() {
        if (currentProfile == null || analyticsContent == null) {
            showError("Analytics view is not ready yet.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Analytics Report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.setInitialFileName(safeFileName(currentProfile.username()) + "-analytics-report.pdf");
        java.io.File selected = chooser.showSaveDialog(resolveStage());
        if (selected == null) {
            return;
        }

        List<ProfileAnalyticsPdfExporter.SectionImage> sections = new ArrayList<>();
        addSectionSnapshot(sections, "Storage Usage", analyticsStorageSection);
        addSectionSnapshot(sections, "Commits Over 30 Days", analyticsCommitsSection);
        addSectionSnapshot(sections, "Productivity Heatmap", analyticsHeatmapSection);
        addSectionSnapshot(sections, "Workspace Activity", analyticsWorkspaceSection);
        addSectionSnapshot(sections, "System Insights", analyticsInsightsSection);
        addSectionSnapshot(sections, "Recent Activity", analyticsRecentActivitySection);
        addSectionSnapshot(sections, "Largest Files", analyticsLargestFilesSection);

        Path outputPath = selected.toPath();
        Thread exportThread = new Thread(() -> {
            try {
                ProfileAnalyticsPdfExporter.export(outputPath, currentProfile, sections);
                Platform.runLater(() -> showInfo("Analytics PDF exported successfully."));
            } catch (IOException ex) {
                Platform.runLater(() -> showError("Failed to export PDF: " + ex.getMessage()));
            }
        }, "profile-analytics-pdf-export");
        exportThread.setDaemon(true);
        exportThread.start();
    }

    private void addSectionSnapshot(
            List<ProfileAnalyticsPdfExporter.SectionImage> sections,
            String title,
            Node node) {
        BufferedImage image = snapshotNode(node);
        if (image != null) {
            sections.add(new ProfileAnalyticsPdfExporter.SectionImage(title, image));
        }
    }

    private BufferedImage snapshotNode(Node node) {
        if (node == null) {
            return null;
        }
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        params.setTransform(Transform.scale(2.0, 2.0));
        WritableImage image = node.snapshot(params, null);
        return SwingFXUtils.fromFXImage(image, null);
    }

    private void installChartTooltips(XYChart.Series<String, Number> series, String suffix) {
        Platform.runLater(() -> {
            for (XYChart.Data<String, Number> item : series.getData()) {
                if (item.getNode() != null) {
                    Tooltip.install(item.getNode(),
                        new Tooltip(item.getXValue() + ": " + item.getYValue() + suffix));
                }
            }
        });
    }

    private static String safeFileName(String value) {
        String sanitized = value == null ? "user" : value.trim().replaceAll("[^a-zA-Z0-9._-]+", "-");
        return sanitized.isBlank() ? "user" : sanitized;
    }

    private VBox buildGithubHeatmap() {
        List<ProfileService.DayCommit> days = currentProfile.commitActivityDays();

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

    private HBox buildRedesignedAnalyticsHeader() {
        VBox titleBlock = new VBox(10);
        Label title = new Label("Analytics Dashboard");
        title.getStyleClass().add("analytics-title");

        HBox tabs = new HBox(22);
        tabs.getStyleClass().add("analytics-header-tabs");
        tabs.getChildren().addAll(
                buildHeaderTab("Overview", true),
                buildHeaderTab("Logs", false),
                buildHeaderTab("Reports", false));
        titleBlock.getChildren().addAll(title, tabs);

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("analytics-secondary-button");
        refreshButton.setOnAction(e -> reloadProfile());

        Button downloadButton = new Button("Export PDF");
        downloadButton.getStyleClass().add("analytics-primary-button");
        downloadButton.setOnAction(e -> onDownloadAnalyticsPdf());

        HBox actions = new HBox(10, refreshButton, downloadButton);
        actions.getStyleClass().add("analytics-header-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(16, titleBlock, spacer, actions);
        header.getStyleClass().add("analytics-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Label buildHeaderTab(String text, boolean active) {
        Label tab = new Label(text);
        tab.getStyleClass().add("analytics-header-tab");
        if (active) {
            tab.getStyleClass().add("analytics-header-tab-active");
        }
        return tab;
    }

    private GridPane buildRedesignedSummaryGrid() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("analytics-summary-grid");
        grid.setHgap(16);
        grid.setVgap(16);

        for (int i = 0; i < 3; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(33.333);
            grid.getColumnConstraints().add(col);
        }

        long activeDays = currentProfile.commitActivityDays().stream()
                .filter(day -> day.commitCount() > 0)
                .count();
        double avgCommits = currentProfile.commitActivityDays().isEmpty()
                ? 0.0
                : currentProfile.commitActivityDays().stream()
                        .mapToInt(ProfileService.DayCommit::commitCount)
                        .average()
                        .orElse(0.0);
        long quotaBytes = currentProfile.storageQuota() != null
                ? currentProfile.storageQuota()
                : DEFAULT_STORAGE_QUOTA_BYTES;
        double rawStorageUsage = quotaBytes <= 0
                ? 0.0
                : currentProfile.storageUsedBytes() / (double) quotaBytes;
        double storageUsage = Math.min(rawStorageUsage, 1.0);

        List<VBox> cards = List.of(
                buildRedesignedMetricCard("Total Commits", String.valueOf(currentProfile.totalCommits()),
                        "Last 30 days included", "#39ff14", clampRatio(avgCommits / 10.0)),
                buildRedesignedMetricCard("Files", String.valueOf(currentProfile.totalFiles()),
                        "Live file inventory", "#66dd8b", clampRatio(currentProfile.totalFiles() / 100.0)),
                buildRedesignedMetricCard("Folders", String.valueOf(currentProfile.totalFolders()),
                        "Workspace structure", "#8b5cf6", clampRatio(currentProfile.totalFolders() / 40.0)),
                buildRedesignedMetricCard("Workspaces", String.valueOf(currentProfile.totalWorkspaces()),
                        "Owned by this profile", "#f59e0b", clampRatio(currentProfile.totalWorkspaces() / 20.0)),
                buildRedesignedMetricCard("Active Days", String.valueOf(activeDays),
                        "Days with at least one commit", "#3b82f6", clampRatio(activeDays / 30.0)),
                buildRedesignedMetricCard("Avg / Day", String.format("%.1f", avgCommits),
                        "Mean daily commit output", rawStorageUsage > 1.0 ? "#ef4444" : "#94a3b8", storageUsage));

        for (int index = 0; index < cards.size(); index++) {
            VBox card = cards.get(index);
            GridPane.setHgrow(card, Priority.ALWAYS);
            grid.add(card, index % 3, index / 3);
        }
        return grid;
    }

    private VBox buildRedesignedMetricCard(String label, String value, String hint, String accent, double fillRatio) {
        Label labelNode = new Label(label.toUpperCase());
        labelNode.getStyleClass().add("analytics-metric-label");
        Label valueNode = new Label(value);
        valueNode.getStyleClass().add("analytics-metric-value");
        Label hintNode = new Label(hint);
        hintNode.getStyleClass().add("analytics-metric-hint");

        Region track = new Region();
        track.getStyleClass().add("analytics-metric-track");
        Region fill = new Region();
        fill.getStyleClass().add("analytics-metric-fill");
        fill.setStyle("-fx-background-color: " + accent + ";");

        StackPane bar = new StackPane(track, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        bar.setPrefHeight(8);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.widthProperty().addListener((obs, oldV, newV) ->
                fill.setPrefWidth(Math.max(16, newV.doubleValue() * clampRatio(fillRatio))));

        VBox card = new VBox(10, labelNode, valueNode, hintNode, bar);
        card.getStyleClass().add("analytics-metric-card");
        return card;
    }

    private VBox buildRedesignedStorageSection() {
        long quotaBytes = currentProfile.storageQuota() != null
                ? currentProfile.storageQuota()
                : DEFAULT_STORAGE_QUOTA_BYTES;
        long usedBytes = currentProfile.storageUsedBytes();
        long freeBytes = Math.max(0L, quotaBytes - usedBytes);
        double usagePercent = quotaBytes <= 0 ? 0.0 : (double) usedBytes / quotaBytes;
        boolean overLimit = usagePercent > 1.0;

        VBox section = createRedesignedAnalyticsPanel(
                "Storage Usage",
                "Calculated from current file snapshots stored in MongoDB.");
        section.getStyleClass().add("analytics-storage-panel");

        Label usageValue = new Label(formatBytes(usedBytes) + " / " + formatBytes(quotaBytes));
        usageValue.getStyleClass().add("analytics-storage-value");

        Label statusBadge = new Label(overLimit ? "LIMIT EXCEEDED" : "WITHIN QUOTA");
        statusBadge.getStyleClass().add(overLimit ? "analytics-badge-danger" : "analytics-badge-ok");

        HBox headline = new HBox(14, usageValue, statusBadge);
        headline.setAlignment(Pos.CENTER_LEFT);

        ProgressBar usageBar = new ProgressBar(Math.min(usagePercent, 1.0));
        usageBar.getStyleClass().add(overLimit ? "analytics-danger-bar" : "analytics-ok-bar");
        usageBar.setPrefHeight(14);
        usageBar.setMaxWidth(Double.MAX_VALUE);

        StackPane pieChart = createStoragePieChart(usedBytes, freeBytes, overLimit);

        HBox detailRow = new HBox(14,
                buildRedesignedStorageStat("Used", formatBytes(usedBytes)),
                buildRedesignedStorageStat("Remaining", formatBytes(freeBytes)),
                buildRedesignedStorageStat("Usage", String.format("%.1f%%", usagePercent * 100)));
        detailRow.setAlignment(Pos.CENTER_LEFT);

        Label note = new Label(overLimit
                ? "Storage has crossed the configured quota. The largest files list below can help you reclaim space."
                : "Storage is healthy. Quota and file size totals are read directly from the database.");
        note.getStyleClass().add("analytics-panel-note");
        note.setWrapText(true);

        HBox overview = new HBox(24, pieChart, detailRow);
        overview.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(detailRow, Priority.ALWAYS);

        section.getChildren().addAll(headline, usageBar, overview, note);
        return section;
    }

    private VBox buildRedesignedStorageStat(String label, String value) {
        Label key = new Label(label.toUpperCase());
        key.getStyleClass().add("analytics-mini-label");
        Label val = new Label(value);
        val.getStyleClass().add("analytics-mini-value");
        VBox box = new VBox(4, key, val);
        box.getStyleClass().add("analytics-mini-card");
        return box;
    }

    private StackPane createStoragePieChart(long usedBytes, long freeBytes, boolean overLimit) {
        PieChart chart = new PieChart();
        chart.setLegendVisible(false);
        chart.setLabelsVisible(false);
        chart.setClockwise(true);
        chart.setStartAngle(90);
        chart.setPrefSize(170, 170);
        chart.setMinSize(170, 170);
        chart.setMaxSize(170, 170);
        chart.getData().addAll(
                new PieChart.Data("Used", Math.max(usedBytes, 1L)),
                new PieChart.Data(overLimit ? "Overage" : "Free", Math.max(freeBytes, 1L)));
        chart.getStyleClass().add("analytics-storage-pie");

        Label percent = new Label(String.format("%.0f%%", (usedBytes + freeBytes) == 0
                ? 0.0
                : (usedBytes * 100.0) / Math.max(usedBytes + freeBytes, 1L)));
        percent.getStyleClass().add("analytics-storage-pie-value");
        Label subtitle = new Label(overLimit ? "Quota Exceeded" : "Quota Used");
        subtitle.getStyleClass().add("analytics-storage-pie-label");
        VBox center = new VBox(2, percent, subtitle);
        center.setAlignment(Pos.CENTER);

        Platform.runLater(() -> {
            if (chart.getData().size() > 0 && chart.getData().get(0).getNode() != null) {
                chart.getData().get(0).getNode().setStyle("-fx-pie-color: " + (overLimit ? "#ff6b6b" : "#39ff14") + ";");
            }
            if (chart.getData().size() > 1 && chart.getData().get(1).getNode() != null) {
                chart.getData().get(1).getNode().setStyle("-fx-pie-color: " + (overLimit ? "#3a1c20" : "#1f3a2d") + ";");
            }
        });

        StackPane wrapper = new StackPane(chart, center);
        wrapper.getStyleClass().add("analytics-storage-pie-wrapper");
        return wrapper;
    }

    private VBox buildRedesignedCommitsSection() {
        VBox section = createRedesignedAnalyticsPanel(
                "Commits Over 30 Days",
                "Daily commit volume across every workspace connected to this profile.");
        AreaChart<String, Number> chart = createRedesignedCommitChart();

        long total = currentProfile.commitActivityDays().stream()
                .mapToLong(ProfileService.DayCommit::commitCount)
                .sum();
        int peak = currentProfile.commitActivityDays().stream()
                .mapToInt(ProfileService.DayCommit::commitCount)
                .max()
                .orElse(0);
        double average = currentProfile.commitActivityDays().isEmpty()
                ? 0.0
                : total / (double) currentProfile.commitActivityDays().size();

        HBox chips = new HBox(12,
                buildRedesignedInsightChip("Total", String.valueOf(total)),
                buildRedesignedInsightChip("Peak Day", String.valueOf(peak)),
                buildRedesignedInsightChip("Avg / Day", String.format("%.1f", average)));

        section.getChildren().addAll(chart, chips);
        return section;
    }

    private AreaChart<String, Number> createRedesignedCommitChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setMinorTickVisible(false);
        yAxis.setTickUnit(1);

        AreaChart<String, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(true);
        chart.setPrefHeight(280);
        chart.getStyleClass().add("analytics-line-chart");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        int index = 0;
        for (ProfileService.DayCommit day : currentProfile.commitActivityDays()) {
            String label = index % 5 == 0
                    ? day.date().getMonthValue() + "/" + day.date().getDayOfMonth()
                    : "";
            series.getData().add(new XYChart.Data<>(label, day.commitCount()));
            index++;
        }
        chart.getData().add(series);
        Platform.runLater(() -> styleCommitChartSeries(chart, series));
        installChartTooltips(series, " commits");
        return chart;
    }

    private VBox buildRedesignedInsightChip(String label, String value) {
        Label labelNode = new Label(label.toUpperCase());
        labelNode.getStyleClass().add("analytics-chip-label");
        Label valueNode = new Label(value);
        valueNode.getStyleClass().add("analytics-chip-value");
        VBox chip = new VBox(3, labelNode, valueNode);
        chip.getStyleClass().add("analytics-chip");
        return chip;
    }

    private void styleCommitChartSeries(AreaChart<String, Number> chart, XYChart.Series<String, Number> series) {
        Node seriesNode = series.getNode();
        if (seriesNode != null) {
            seriesNode.setStyle("-fx-stroke: #39ff14; -fx-stroke-width: 3px;");
        }
        Node fillNode = chart.lookup(".chart-series-area-fill");
        if (fillNode != null) {
            fillNode.setStyle("-fx-fill: linear-gradient(to bottom, rgba(57,255,20,0.32), rgba(57,255,20,0.05));");
        }
        Node lineNode = chart.lookup(".chart-series-area-line");
        if (lineNode != null) {
            lineNode.setStyle("-fx-stroke: #39ff14; -fx-stroke-width: 3px;");
        }
        for (XYChart.Data<String, Number> point : series.getData()) {
            if (point.getNode() != null) {
                point.getNode().setStyle("-fx-background-color: #39ff14, #0b141c; -fx-background-radius: 8;");
            }
        }
    }

    private VBox buildRedesignedHeatmapSection() {
        List<ProfileService.DayCommit> days = currentProfile.commitActivityDays();
        Map<LocalDate, Integer> countMap = new HashMap<>();
        for (ProfileService.DayCommit day : days) {
            countMap.put(day.date(), day.commitCount());
        }

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(29);
        while (start.getDayOfWeek() != DayOfWeek.MONDAY) {
            start = start.minusDays(1);
        }

        long totalDays = start.until(today, java.time.temporal.ChronoUnit.DAYS) + 1;
        int weekColumns = (int) Math.ceil(totalDays / 7.0);
        String[] labels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

        VBox section = createRedesignedAnalyticsPanel(
                "Productivity Heatmap",
                "Commit intensity by day, styled after your reference dashboards.");

        HBox grid = new HBox(4);
        VBox dayLabels = new VBox(4);
        for (String label : labels) {
            Label dayLabel = new Label(label);
            dayLabel.getStyleClass().add("analytics-heatmap-day");
            dayLabel.setPrefHeight(16);
            dayLabels.getChildren().add(dayLabel);
        }
        grid.getChildren().add(dayLabels);

        for (int week = 0; week < weekColumns; week++) {
            VBox column = new VBox(4);
            for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
                LocalDate date = start.plusDays((long) week * 7 + dayIndex);
                Region cell = new Region();
                cell.setPrefSize(16, 16);
                cell.getStyleClass().addAll("heatmap-cell", date.isAfter(today)
                        ? "heatmap-level-0"
                        : heatLevel(countMap.getOrDefault(date, 0)));
                if (!date.isAfter(today)) {
                    int count = countMap.getOrDefault(date, 0);
                    Tooltip.install(cell, new Tooltip(date + ": " + count + (count == 1 ? " commit" : " commits")));
                } else {
                    cell.setOpacity(0.2);
                }
                column.getChildren().add(cell);
            }
            grid.getChildren().add(column);
        }

        HBox legend = new HBox(6);
        legend.getStyleClass().add("analytics-heatmap-legend");
        legend.getChildren().add(new Label("Less"));
        for (String style : List.of("heatmap-level-0", "heatmap-level-1", "heatmap-level-2", "heatmap-level-3", "heatmap-level-4")) {
            Region swatch = new Region();
            swatch.setPrefSize(12, 12);
            swatch.getStyleClass().addAll("heatmap-cell", style);
            legend.getChildren().add(swatch);
        }
        legend.getChildren().add(new Label("More"));

        section.getChildren().addAll(grid, legend);
        return section;
    }

    private VBox buildRedesignedWorkspaceSection() {
        VBox section = createRedesignedAnalyticsPanel(
                "Workspace Activity",
                "Top repositories ranked by commit volume.");

        List<ProfileService.PopularWorkspace> workspaces = currentProfile.popularWorkspaces();
        int maxCommits = workspaces.stream()
                .mapToInt(ProfileService.PopularWorkspace::commitCount)
                .max()
                .orElse(1);

        if (workspaces.isEmpty()) {
            Label empty = new Label("No workspace activity yet.");
            empty.getStyleClass().add("analytics-empty-state");
            section.getChildren().add(empty);
            return section;
        }

        VBox list = new VBox(14);
        for (ProfileService.PopularWorkspace workspace : workspaces) {
            String displayName = workspace.workspaceName().length() > 24
                    ? workspace.workspaceName().substring(0, 24) + "..."
                    : workspace.workspaceName();
            Label name = new Label(displayName);
            name.getStyleClass().add("analytics-workspace-name");
            Label count = new Label(workspace.commitCount() + " commits");
            count.getStyleClass().add("analytics-workspace-count");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox labels = new HBox(name, spacer, count);

            ProgressBar bar = new ProgressBar(maxCommits == 0 ? 0 : workspace.commitCount() / (double) maxCommits);
            bar.getStyleClass().add("analytics-workspace-bar");
            bar.setPrefHeight(8);
            bar.setMaxWidth(Double.MAX_VALUE);

            VBox row = new VBox(6, labels, bar);
            row.getStyleClass().add("analytics-workspace-row");
            list.getChildren().add(row);
        }

        section.getChildren().add(list);
        return section;
    }

    private VBox buildRedesignedInsightsSection() {
        VBox section = createRedesignedAnalyticsPanel(
                "System Insights",
                "Concise takeaways computed from the same analytics data feeding the charts.");

        ProfileService.DayCommit mostActiveDay = null;
        for (ProfileService.DayCommit day : currentProfile.commitActivityDays()) {
            if (mostActiveDay == null || day.commitCount() > mostActiveDay.commitCount()) {
                mostActiveDay = day;
            }
        }

        long totalActions = currentProfile.usageDays().stream()
                .mapToLong(ProfileService.DayActivity::activityCount)
                .sum();
        int totalCreations = currentProfile.creationDays().stream()
                .mapToInt(day -> day.workspaceCount() + day.folderCount() + day.fileCount())
                .sum();
        String topWorkspace = currentProfile.popularWorkspaces().isEmpty()
                ? "No workspace yet"
                : currentProfile.popularWorkspaces().get(0).workspaceName();

        VBox list = new VBox(12,
                buildRedesignedInsightRow("Most active day", mostActiveDay == null
                        ? "No commit data"
                        : mostActiveDay.date() + " (" + mostActiveDay.commitCount() + " commits)"),
                buildRedesignedInsightRow("Top workspace", topWorkspace),
                buildRedesignedInsightRow("Tracked actions", String.valueOf(totalActions)),
                buildRedesignedInsightRow("Creations in 30 days", String.valueOf(totalCreations)));
        section.getChildren().add(list);
        return section;
    }

    private HBox buildRedesignedInsightRow(String label, String value) {
        Label labelNode = new Label(label.toUpperCase());
        labelNode.getStyleClass().add("analytics-insight-label");
        Label valueNode = new Label(value);
        valueNode.getStyleClass().add("analytics-insight-value");
        valueNode.setWrapText(true);
        VBox text = new VBox(3, labelNode, valueNode);
        HBox row = new HBox(text);
        row.getStyleClass().add("analytics-insight-row");
        return row;
    }

    private VBox buildRedesignedRecentActivitySection() {
        VBox section = createRedesignedAnalyticsPanel(
                "Recent Activity",
                "Latest commits pulled from the commits collection.");

        List<ProfileService.RecentCommit> commits = currentProfile.recentCommits();
        if (commits == null || commits.isEmpty()) {
            Label empty = new Label("No recent commit activity found.");
            empty.getStyleClass().add("analytics-empty-state");
            section.getChildren().add(empty);
            return section;
        }

        VBox list = new VBox(12);
        for (ProfileService.RecentCommit commit : commits) {
            Label title = new Label(commit.message().isBlank() ? "(no message)" : commit.message());
            title.getStyleClass().add("analytics-timeline-title");
            Label meta = new Label(commit.workspaceName()
                    + (commit.time() != null ? " | " + formatRelative(commit.time()) : ""));
            meta.getStyleClass().add("analytics-timeline-meta");
            VBox text = new VBox(3, title, meta);

            Region dot = new Region();
            dot.getStyleClass().add("analytics-timeline-dot");
            dot.setMinSize(10, 10);
            dot.setPrefSize(10, 10);

            HBox row = new HBox(12, dot, text);
            row.getStyleClass().add("analytics-timeline-row");
            list.getChildren().add(row);
        }

        section.getChildren().add(list);
        return section;
    }

    private VBox buildRedesignedLargestFilesSection() {
        VBox section = createRedesignedAnalyticsPanel(
                "Largest Files",
                "Sorted by current snapshot size so storage hotspots are easy to spot.");

        List<ProfileService.FileSizeEntry> files = currentProfile.largestFiles();
        if (files == null || files.isEmpty()) {
            Label empty = new Label("No file snapshots available to measure yet.");
            empty.getStyleClass().add("analytics-empty-state");
            section.getChildren().add(empty);
            return section;
        }

        VBox list = new VBox(10);
        for (ProfileService.FileSizeEntry file : files) {
            Label filename = new Label(file.filename());
            filename.getStyleClass().add("analytics-file-name");
            Label workspace = new Label(file.workspaceName());
            workspace.getStyleClass().add("analytics-file-workspace");
            VBox names = new VBox(3, filename, workspace);
            HBox.setHgrow(names, Priority.ALWAYS);

            Label size = new Label(formatBytes(file.sizeBytes()));
            size.getStyleClass().add("analytics-file-size");

            HBox row = new HBox(12, names, size);
            row.getStyleClass().add("analytics-file-row");
            row.setAlignment(Pos.CENTER_LEFT);
            list.getChildren().add(row);
        }

        section.getChildren().add(list);
        return section;
    }

    private VBox createRedesignedAnalyticsPanel(String title, String subtitle) {
        Label titleNode = new Label(title);
        titleNode.getStyleClass().add("analytics-panel-title");
        Label subtitleNode = new Label(subtitle);
        subtitleNode.getStyleClass().add("analytics-panel-subtitle");
        subtitleNode.setWrapText(true);
        VBox panel = new VBox(16, titleNode, subtitleNode);
        panel.getStyleClass().add("chart-section");
        return panel;
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

    private static double clampRatio(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    // ── Data rendering ────────────────────────────────────────────────────

    private void reloadProfile() {
        if (profileService == null || currentUserId == null) return;
        currentProfile = profileService.loadProfile(currentUserId);
        renderProfile(currentProfile);
        if (analyticsShown) {
            showAnalyticsView();
        }
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
