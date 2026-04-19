package com.dvcs.client.dashboard.profile;

import com.dvcs.client.auth.db.MongoConnection;
import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.auth.service.UserService;
import com.dvcs.client.controller.LoginSignupController;
import com.dvcs.client.dashboard.MainLayoutController;
import com.dvcs.client.dashboard.navbar.NavbarController;
import com.mongodb.client.MongoDatabase;
import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.bson.types.ObjectId;

public final class ProfileController {

    private static final String ICON_BACK_URL = "https://img.icons8.com/fluency-systems-filled/48/00ff88/left.png";
    private static final String ICON_LOGOUT_URL = "https://img.icons8.com/fluency-systems-filled/48/00ff88/logout-rounded-left.png";
    private static final String ICON_USER_PROFILE_URL = "https://img.icons8.com/fluency-systems-filled/96/00ff88/user-male-circle.png";

    @FXML
    private BorderPane root;

    @FXML
    private StackPane headerContainer;

    @FXML
    private VBox mainContentColumn;

    @FXML
    private Label avatarInitialsLabel;

    @FXML
    private Label nameValueLabel;

    @FXML
    private Label usernameValueLabel;

    @FXML
    private Button editProfileButton;

    @FXML
    private Button logoutButton;

    @FXML
    private StackPane editProfileModal;

    @FXML
    private VBox passwordFieldsBox;

    @FXML
    private Button togglePasswordButton;

    @FXML
    private TextField editNameField;

    @FXML
    private TextField editUsernameField;

    @FXML
    private PasswordField passwordPopupOldField;

    @FXML
    private PasswordField passwordPopupNewField;

    @FXML
    private PasswordField passwordPopupConfirmField;

    @FXML
    private GridPane popularWorkspaceGrid;

    @FXML
    private Label popularWorkspaceEmptyLabel;

    @FXML
    private VBox popularWorkspaceEmptyBox;

    @FXML
    private ImageView popularWorkspaceEmptyImage;

    @FXML
    private VBox activityBox;

    @FXML
    private HBox statsRow;

    @FXML
    private GridPane activityGrid;

    @FXML
    private Label totalWorkspacesValueLabel;

    @FXML
    private Label totalFoldersValueLabel;

    @FXML
    private Label totalFilesValueLabel;

    @FXML
    private Label totalCommitsValueLabel;

    private NavbarController navbarController;

    private ProfileService profileService;
    private ObjectId currentUserId;
    private Consumer<String> onSearchSubmitted;
    private Runnable onNotificationRequested;
    private Runnable onProfileRequested;

    private ProfileService.ProfileViewModel currentProfile;

    @FXML
    private void initialize() {
        Node navbar = loadFxmlNode("/fxml/Navbar.fxml");
        this.navbarController = (NavbarController) navbar.getProperties().get("fx:controller");

        if (navbar instanceof HBox navbarBox) {
            navbarBox.getChildren().add(0, createNavbarBackButton());
        }

        VBox topContainer = new VBox(navbar);
        topContainer.setPadding(new Insets(10, 18, 0, 0));
        headerContainer.getChildren().setAll(topContainer);

        if (logoutButton != null) {
            configureLogoutButtonGraphic();
            logoutButton.setContentDisplay(ContentDisplay.LEFT);
            logoutButton.setGraphicTextGap(8);
        }

        if (popularWorkspaceGrid != null) {
            popularWorkspaceGrid.setPrefHeight(220);
            popularWorkspaceGrid.setMinHeight(220);
            popularWorkspaceGrid.setMaxHeight(220);
        }

        if (popularWorkspaceEmptyImage != null) {
            popularWorkspaceEmptyImage.setImage(new Image(ICON_USER_PROFILE_URL, true));
        }

        setEditMode(false);
    }

    public void configure(
            ProfileService profileService,
            ObjectId currentUserId,
            Consumer<String> onSearchSubmitted,
            Runnable onNotificationRequested,
            Runnable onProfileRequested) {
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.currentUserId = Objects.requireNonNull(currentUserId, "currentUserId");
        this.onSearchSubmitted = onSearchSubmitted;
        this.onNotificationRequested = onNotificationRequested;
        this.onProfileRequested = onProfileRequested;

        if (navbarController != null) {
            navbarController.configureHandlers(this::handleSearch, this::handleNotifications, this::handleProfileClick);
        }

        reloadProfile();
    }

    private void reloadProfile() {
        if (profileService == null || currentUserId == null) {
            return;
        }

        currentProfile = profileService.loadProfile(currentUserId);
        renderProfile(currentProfile);
        setEditMode(false);
    }

    private void renderProfile(ProfileService.ProfileViewModel profile) {
        if (profile == null) {
            return;
        }

        avatarInitialsLabel.setText(profile.initials());
        nameValueLabel.setText((profile.name() == null || profile.name().isBlank()) ? "Not set" : profile.name());
        usernameValueLabel.setText("@" + profile.username());

        if (navbarController != null) {
            navbarController.setProfileIdentity(profile.name(), profile.username());
        }

        renderPopularWorkspaces(profile.popularWorkspaces());

        totalWorkspacesValueLabel.setText(String.valueOf(profile.totalWorkspaces()));
        totalFoldersValueLabel.setText(String.valueOf(profile.totalFolders()));
        totalFilesValueLabel.setText(String.valueOf(profile.totalFiles()));
        totalCommitsValueLabel.setText(String.valueOf(profile.totalCommits()));

        renderActivity(profile.activityDays());
    }

    private void renderPopularWorkspaces(List<ProfileService.PopularWorkspace> workspaces) {
        popularWorkspaceGrid.getChildren().clear();

        List<ProfileService.PopularWorkspace> safeList = workspaces == null ? List.of() : workspaces;
        if (safeList.isEmpty()) {
            if (popularWorkspaceEmptyBox != null) {
                popularWorkspaceEmptyBox.setVisible(true);
                popularWorkspaceEmptyBox.setManaged(true);
            } else {
                popularWorkspaceEmptyLabel.setVisible(true);
                popularWorkspaceEmptyLabel.setManaged(true);
            }
            return;
        }

        if (popularWorkspaceEmptyBox != null) {
            popularWorkspaceEmptyBox.setVisible(false);
            popularWorkspaceEmptyBox.setManaged(false);
        } else {
            popularWorkspaceEmptyLabel.setVisible(false);
            popularWorkspaceEmptyLabel.setManaged(false);
        }

        int index = 0;
        for (ProfileService.PopularWorkspace workspace : safeList) {
            if (index >= 6) {
                break;
            }

            VBox card = new VBox(4);
            card.getStyleClass().add("profile-workspace-item");

            Label nameLabel = new Label(workspace.workspaceName());
            nameLabel.getStyleClass().add("profile-workspace-name");
            Label commitLabel = new Label(workspace.commitCount() + " commits");
            commitLabel.getStyleClass().add("profile-workspace-commits");

            card.getChildren().addAll(nameLabel, commitLabel);

            int row = index / 3;
            int col = index % 3;
            popularWorkspaceGrid.add(card, col, row);
            index++;
        }
    }

    private static String colorClassForCommit(int commitCount) {
        if (commitCount <= 0) return "activity-glow-none";
        if (commitCount < 3) return "activity-glow-low";
        if (commitCount < 7) return "activity-glow-med";
        return "activity-glow-high";
    }

    private void renderActivity(List<ProfileService.DayCommit> days) {
        activityGrid.getChildren().clear();

        int columns = 10;
        int index = 0;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM");
        for (ProfileService.DayCommit day : days) {
            int row = index / columns;
            int column = index % columns;

            Region cell = new Region();
            cell.getStyleClass().addAll("profile-activity-cell", colorClassForCommit(day.commitCount()));
            cell.setPrefSize(20, 20);
            cell.setMinSize(20, 20);
            cell.setAccessibleText(formatter.format(day.date()) + ": " + day.commitCount() + " commits");

            activityGrid.add(cell, column, row);
            index++;
        }
    }

    @FXML
    private void onEditProfile() {
        if (currentProfile == null) return;
        editNameField.setText(currentProfile.name() == null ? "" : currentProfile.name());
        editUsernameField.setText(currentProfile.username());
        
        // Reset password fields state
        passwordFieldsBox.setVisible(false);
        passwordFieldsBox.setManaged(false);
        togglePasswordButton.setText("Change Password");
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

        // 1. Update basic info
        ProfileService.UpdateResult basicResult = profileService.updateProfile(
                currentUserId, editNameField.getText(), editUsernameField.getText());

        if (!basicResult.success()) {
            showError(basicResult.message());
            return;
        }

        // 2. Update password if fields are visible
        if (passwordFieldsBox.isVisible()) {
            ProfileService.UpdateResult passResult = profileService.changePassword(
                    currentUserId,
                    passwordPopupOldField.getText(),
                    passwordPopupNewField.getText(),
                    passwordPopupConfirmField.getText());
            
            if (!passResult.success()) {
                showError(passResult.message());
                return;
            }
        }

        showInfo("Profile updated successfully");
        reloadProfile();
    }

    @FXML
    private void onTogglePasswordFields() {
        boolean isVisible = passwordFieldsBox.isVisible();
        passwordFieldsBox.setVisible(!isVisible);
        passwordFieldsBox.setManaged(!isVisible);
        togglePasswordButton.setText(isVisible ? "Change Password" : "Hide Password Section");
        if (isVisible) clearPasswordFields();
    }

    private void clearPasswordFields() {
        passwordPopupOldField.clear();
        passwordPopupNewField.clear();
        passwordPopupConfirmField.clear();
    }

    private void setEditMode(boolean editing) {
        if (editProfileModal != null) {
            editProfileModal.setVisible(editing);
            editProfileModal.setManaged(editing);
        }
    }

    @FXML
    private void onLogout() {
        Stage currentStage = (root != null && root.getScene() != null && root.getScene().getWindow() instanceof Stage s)
                ? s
                : null;
        if (currentStage == null) {
            showError("Logout failed. Please try again.");
            return;
        }

        try {
            showLandingPageOnStage(currentStage);
        } catch (Exception e) {
            showError("Logout failed. Please try again.");
        }
    }


    private void handleSearch(String query) {
        if (onSearchSubmitted != null) {
            onSearchSubmitted.accept(query);
        }
    }

    private void handleNotifications() {
        if (onNotificationRequested != null) {
            onNotificationRequested.run();
        }
    }

    private void handleProfileClick() {
        if (onProfileRequested != null) {
            onProfileRequested.run();
        }
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        Window owner = root == null || root.getScene() == null ? null : root.getScene().getWindow();
        if (owner != null) {
            alert.initOwner(owner);
            alert.initModality(Modality.WINDOW_MODAL);
        }
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Profile update failed");
        Window owner = root == null || root.getScene() == null ? null : root.getScene().getWindow();
        if (owner != null) {
            alert.initOwner(owner);
            alert.initModality(Modality.WINDOW_MODAL);
        }
        alert.showAndWait();
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

    private void closeCurrentWindow() {
        Stage stage = null;
        if (root != null && root.getScene() != null && root.getScene().getWindow() instanceof Stage currentStage) {
            stage = currentStage;
        }

        if (stage == null) {
            for (Window window : Window.getWindows()) {
                if (window.isFocused() && window instanceof Stage focusedStage) {
                    stage = focusedStage;
                    break;
                }
            }
        }

        if (stage != null) {
            Window owner = stage.getOwner();
            stage.setFullScreen(false);
            stage.close();
            if (owner instanceof Stage ownerStage) {
                if (!ownerStage.isShowing()) {
                    ownerStage.show();
                }
                ownerStage.setIconified(false);
                ownerStage.toFront();
                ownerStage.requestFocus();
            }
        }
    }

    private Button createNavbarBackButton() {
        Button backButton = new Button();
        backButton.getStyleClass().add("navbar-back-inline");
        backButton.setOnAction(e -> closeCurrentWindow());

        ImageView iconView = new ImageView(new Image(ICON_BACK_URL, true));
        iconView.setFitWidth(18);
        iconView.setFitHeight(18);
        iconView.setPreserveRatio(true);
        backButton.setGraphic(iconView);
        return backButton;
    }

    private void configureLogoutButtonGraphic() {
        ImageView iconView = new ImageView(new Image(ICON_LOGOUT_URL, true));
        iconView.setFitWidth(14);
        iconView.setFitHeight(14);
        iconView.setPreserveRatio(true);
        logoutButton.setGraphic(iconView);
        logoutButton.setContentDisplay(ContentDisplay.RIGHT);
    }

    private void showLoginOnStage(Stage stage) throws Exception {
        URL fxmlUrl = MainLayoutController.class.getResource("/fxml/login_signup.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("FXML '/fxml/login_signup.fxml' not found on classpath");
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent loginRoot = loader.load();

        LoginSignupController controller = loader.getController();
        controller.setUserService(createUserService());
        controller.setOnAuthSuccess(username -> {
            try {
                showMainLayoutOnStage(stage, username);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        stage.setFullScreen(false);
        stage.setScene(new Scene(loginRoot));
        stage.setMaximized(true);
        stage.show();
    }

    private void showLandingPageOnStage(Stage stage) throws Exception {
        URL fxmlUrl = MainLayoutController.class.getResource("/fxml/landing.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("FXML '/fxml/landing.fxml' not found on classpath");
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent landingRoot = loader.load();

        stage.setFullScreen(false);
        stage.setScene(new Scene(landingRoot));
        stage.setMaximized(true);
        stage.show();
    }

    private void showMainLayoutOnStage(Stage stage, String username) throws Exception {
        URL fxmlUrl = MainLayoutController.class.getResource("/fxml/MainLayout.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("FXML '/fxml/MainLayout.fxml' not found on classpath");
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent mainRoot = loader.load();
        MainLayoutController controller = loader.getController();
        controller.setUsername(username);

        stage.setScene(new Scene(mainRoot));
        stage.setMaximized(true);
        stage.setFullScreenExitHint("");
        stage.setFullScreen(true);
        stage.show();
    }

    private static UserService createUserService() {
        String dbName = System.getenv("MONGODB_DB");
        if (dbName == null || dbName.isBlank()) {
            dbName = "DVCS";
        }

        MongoDatabase database = MongoConnection.getDatabase(dbName);
        return new UserService(new UserRepository(database));
    }
}
