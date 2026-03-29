package com.dvcs.client.dashboard.search;

import com.dvcs.client.dashboard.MainLayoutController;
import com.dvcs.client.dashboard.navbar.NavbarController;
import java.io.IOException;
import java.net.URL;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.bson.types.ObjectId;

public final class SearchController {

    @FXML
    private BorderPane root;

    @FXML
    private StackPane headerContainer;

    @FXML
    private Label matchesCountLabel;

    @FXML
    private ScrollPane resultsScrollPane;

    @FXML
    private GridPane resultsGrid;

    @FXML
    private Label emptyStateLabel;

    private SearchService searchService;
    private ObjectId currentUserId;
    private Runnable onNotificationRequested;
    private Runnable onProfileRequested;
    private Consumer<SearchResultItem> onResultSelected;

    private NavbarController navbarController;

    @FXML
    private void initialize() {
        Node navbar = loadFxmlNode("/fxml/Navbar.fxml");
        this.navbarController = (NavbarController) navbar.getProperties().get("fx:controller");

        Button backButton = new Button("\u2190");
        backButton.getStyleClass().add("app-back-button");
        backButton.setOnAction(e -> closeCurrentWindow());

        HBox topContainer = new HBox(10, backButton, navbar);
        topContainer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(navbar, Priority.ALWAYS);
        topContainer.setPadding(new Insets(10, 18, 0, 18));
        headerContainer.getChildren().setAll(topContainer);
    }

    public void configure(
            SearchService searchService,
            ObjectId currentUserId,
            String currentUsername,
            Runnable onNotificationRequested,
            Runnable onProfileRequested,
            Consumer<SearchResultItem> onResultSelected) {
        this.searchService = Objects.requireNonNull(searchService, "searchService");
        this.currentUserId = Objects.requireNonNull(currentUserId, "currentUserId");
        this.onNotificationRequested = onNotificationRequested;
        this.onProfileRequested = onProfileRequested;
        this.onResultSelected = onResultSelected;

        if (navbarController != null) {
            navbarController.setUsername(currentUsername);
            navbarController.configureHandlers(this::executeSearch, this::handleNotifications, this::handleProfile);
        }
    }

    public void setInitialQuery(String query) {
        if (navbarController != null) {
            navbarController.setSearchQuery(query == null ? "" : query);
        }
        executeSearch(query);
    }

    private void executeSearch(String query) {
        if (searchService == null || currentUserId == null) {
            return;
        }

        List<SearchResultItem> results = searchService.searchFileResultsForOwner(currentUserId, query);
        renderResults(results);
    }

    private void renderResults(List<SearchResultItem> results) {
        resultsGrid.getChildren().clear();

        int count = results == null ? 0 : results.size();
        matchesCountLabel.setText(count + " matches found");

        if (count == 0) {
            emptyStateLabel.setVisible(true);
            emptyStateLabel.setManaged(true);
            resultsScrollPane.setVisible(false);
            resultsScrollPane.setManaged(false);
            return;
        }

        emptyStateLabel.setVisible(false);
        emptyStateLabel.setManaged(false);
        resultsScrollPane.setVisible(true);
        resultsScrollPane.setManaged(true);

        int column = 0;
        int row = 0;
        for (SearchResultItem result : results) {
            Node card = createResultCard(result);
            resultsGrid.add(card, column, row);
            column++;
            if (column == 3) {
                column = 0;
                row++;
            }
        }
    }

    private Node createResultCard(SearchResultItem result) {
        VBox card = new VBox(8);
        card.getStyleClass().add("search-result-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(14));

        HBox iconRow = new HBox();
        iconRow.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label("FILE");
        iconLabel.getStyleClass().add("search-file-icon");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        iconRow.getChildren().addAll(iconLabel, spacer);

        Label fileNameLabel = new Label(result.fileName());
        fileNameLabel.getStyleClass().add("search-file-name");

        Label pathLabel = new Label(result.relativePath());
        pathLabel.getStyleClass().add("search-file-path");
        pathLabel.setWrapText(true);

        card.getChildren().addAll(iconRow, fileNameLabel, pathLabel);
        card.setOnMouseClicked(event -> {
            if (onResultSelected != null) {
                onResultSelected.accept(result);
            }
        });
        return card;
    }

    private void handleNotifications() {
        if (onNotificationRequested != null) {
            onNotificationRequested.run();
        }
    }

    private void handleProfile() {
        if (onProfileRequested != null) {
            onProfileRequested.run();
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
            stage.close();
            if (owner instanceof Stage ownerStage) {
                ownerStage.toFront();
                ownerStage.requestFocus();
            }
        }
    }
}
