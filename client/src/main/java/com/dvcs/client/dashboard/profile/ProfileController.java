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

    @FXML
    private BorderPane root;

    @FXML
    private StackPane headerContainer;

    @FXML
    private HBox mainContainer;

    @FXML
    private VBox leftCardColumn;

    @FXML
    private StackPane profileCard;

    @FXML
    private VBox profileCardContent;

    @FXML
    private VBox rightContentSection;

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
    private VBox readOnlyBox;

    @FXML
    private VBox editFieldsBox;

    @FXML
    private TextField editNameField;

    @FXML
    private TextField editUsernameField;

    @FXML
    private PasswordField oldPasswordField;

    @FXML
    private PasswordField newPasswordField;

    @FXML
    private PasswordField confirmPasswordField;

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
            logoutButton.setContentDisplay(ContentDisplay.RIGHT);
        }

        if (profileCard != null) {
            profileCard.setPrefWidth(360);
            profileCard.setMinWidth(360);
            profileCard.setMaxWidth(360);
            profileCard.setPrefHeight(560);
            profileCard.setMinHeight(560);
        }
        if (leftCardColumn != null && profileCard != null) {
            VBox.setMargin(profileCard, new Insets(40, 0, 0, 20));
        }

        if (popularWorkspaceGrid != null) {
            popularWorkspaceGrid.setPrefHeight(220);
            popularWorkspaceGrid.setMinHeight(220);
            popularWorkspaceGrid.setMaxHeight(220);
        }

        URL profileEmptyUrl = MainLayoutController.class.getResource("/images/user_profile.png");
        if (popularWorkspaceEmptyImage != null && profileEmptyUrl != null) {
            popularWorkspaceEmptyImage.setImage(new Image(profileEmptyUrl.toExternalForm(), true));
        }
        if (activityBox != null) {
            activityBox.setPrefHeight(200);
            activityBox.setMinHeight(200);
            activityBox.setMaxHeight(200);
        }
        if (statsRow != null) {
            for (Node node : statsRow.getChildren()) {
                if (node instanceof VBox card) {
                    card.setPrefWidth(180);
                    card.setMinWidth(180);
                    card.setMaxWidth(180);
                    card.setPrefHeight(80);
                    card.setMinHeight(80);
                    card.setMaxHeight(80);
                }
            }
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
        clearEditFields();
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

    private void renderActivity(List<ProfileService.DayCommit> days) {
        activityGrid.getChildren().clear();

        int columns = 10;
        int index = 0;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM");
        for (ProfileService.DayCommit day : days) {
            int row = index / columns;
            int column = index % columns;

            Region cell = new Region();
            cell.getStyleClass().add("profile-activity-cell");
            cell.setPrefSize(18, 18);
            cell.setMinSize(18, 18);
            cell.setMaxSize(18, 18);
            cell.setBackground(new Background(
                    new BackgroundFill(colorForCommit(day.commitCount()), new CornerRadii(5), Insets.EMPTY)));
            cell.setAccessibleText(formatter.format(day.date()) + ": " + day.commitCount() + " commits");

            activityGrid.add(cell, column, row);
            index++;
        }
    }

    private static Color colorForCommit(int commitCount) {
        if (commitCount <= 0) {
            return Color.web("#ffffff");
        }
        if (commitCount == 1) {
            return Color.web("#dbe8ff");
        }
        if (commitCount == 2) {
            return Color.web("#8fb2ff");
        }
        return Color.web("#2d5fd3");
    }

    @FXML
    private void onEditProfile() {
        if (currentProfile == null) {
            return;
        }
        editNameField.setText(currentProfile.name() == null ? "" : currentProfile.name());
        editUsernameField.setText(currentProfile.username());
        oldPasswordField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
        setEditMode(true);
    }

    @FXML
    private void onCancelEdit() {
        clearEditFields();
        setEditMode(false);
    }

    @FXML
    private void onSaveProfile() {
        if (profileService == null || currentUserId == null) {
            return;
        }

        ProfileService.UpdateResult result = profileService.updateProfile(
                currentUserId,
                editNameField.getText(),
                editUsernameField.getText(),
                oldPasswordField.getText(),
                newPasswordField.getText(),
                confirmPasswordField.getText());

        if (!result.success()) {
            showError(result.message());
            return;
        }

        showInfo(result.message());
        reloadProfile();
    }

    @FXML
    private void onLogout() {
        Stage currentStage = (root != null && root.getScene() != null && root.getScene().getWindow() instanceof Stage s)
                ? s
                : null;
        Stage ownerStage = currentStage != null && currentStage.getOwner() instanceof Stage os ? os : null;

        if (ownerStage == null) {
            closeCurrentWindow();
            return;
        }

        try {
            showLoginOnStage(ownerStage);
            currentStage.setFullScreen(false);
            currentStage.close();
        } catch (Exception e) {
            showError("Logout failed. Please try again.");
        }
    }

    private void clearEditFields() {
        if (editNameField != null) {
            editNameField.clear();
        }
        if (editUsernameField != null) {
            editUsernameField.clear();
        }
        if (oldPasswordField != null) {
            oldPasswordField.clear();
        }
        if (newPasswordField != null) {
            newPasswordField.clear();
        }
        if (confirmPasswordField != null) {
            confirmPasswordField.clear();
        }
    }

    private void setEditMode(boolean editing) {
        readOnlyBox.setVisible(!editing);
        readOnlyBox.setManaged(!editing);
        editFieldsBox.setVisible(editing);
        editFieldsBox.setManaged(editing);
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

        URL iconUrl = MainLayoutController.class.getResource("/images/back_arrow.png");
        if (iconUrl != null) {
            ImageView iconView = new ImageView(new Image(iconUrl.toExternalForm(), true));
            iconView.setFitWidth(18);
            iconView.setFitHeight(18);
            iconView.setPreserveRatio(true);
            backButton.setGraphic(iconView);
        } else {
            backButton.setText("\u2190");
        }
        return backButton;
    }

    private void configureLogoutButtonGraphic() {
        URL iconUrl = MainLayoutController.class.getResource("/images/logout_icon.png");
        if (iconUrl == null) {
            return;
        }
        ImageView iconView = new ImageView(new Image(iconUrl.toExternalForm(), true));
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
