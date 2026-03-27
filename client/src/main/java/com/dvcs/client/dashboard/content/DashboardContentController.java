package com.dvcs.client.dashboard.content;

import com.dvcs.client.dashboard.analytics.AnalyticsPanelController;
import com.dvcs.client.dashboard.workspace.WorkspaceCardController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;

public class DashboardContentController {

    @FXML
    private HBox mainCard;

    @FXML
    private AnchorPane overlayRoot;

    @FXML
    private void initialize() {
        mainCard.getChildren().setAll(buildWorkspaceSection(), loadAnalyticsPanel());
        if (!mainCard.getChildren().isEmpty()) {
            HBox.setHgrow(mainCard.getChildren().getFirst(), Priority.ALWAYS);
        }

        // Size the overlay card relative to the available window area.
        // JavaFX doesn't support percentage sizing in FXML, so we bind explicitly.
        overlayRoot.widthProperty().addListener((obs, oldV, newV) -> layoutCard());
        overlayRoot.heightProperty().addListener((obs, oldV, newV) -> layoutCard());
        layoutCard();
    }

    private void layoutCard() {
        if (overlayRoot == null || mainCard == null)
            return;

        double availableW = overlayRoot.getWidth();
        double availableH = overlayRoot.getHeight();
        if (availableW <= 0 || availableH <= 0)
            return;

        double cardW = Math.max(940, availableW * 0.84);
        double cardH = Math.max(560, availableH * 0.72);

        mainCard.setPrefWidth(Math.min(cardW, availableW - 24));
        mainCard.setPrefHeight(Math.min(cardH, availableH - 24));

        // Center card and overlap top purple band and bottom white section.
        AnchorPane.setLeftAnchor(mainCard, (availableW - mainCard.getPrefWidth()) / 2.0);
        AnchorPane.setTopAnchor(mainCard, Math.max(18, (availableH - mainCard.getPrefHeight()) / 2.0 - 26));
    }

    private Node buildWorkspaceSection() {
        VBox left = new VBox(14);
        left.getStyleClass().add("workspace-section");

        // Header row
        HBox header = new HBox(12);
        Label title = new Label("My Workspace");
        title.getStyleClass().add("section-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button create = new Button("Create New Workspace");
        create.getStyleClass().add("primary-button");

        header.getChildren().addAll(title, spacer, create);

        // Grid
        FlowPane grid = new FlowPane();
        grid.getStyleClass().add("workspace-grid");
        grid.setHgap(12);
        grid.setVgap(12);

        grid.getChildren().addAll(
                createWorkspaceCard("Design Docs"),
                createWorkspaceCard("API Specs"),
                createWorkspaceCard("Sprint Notes"),
                createWorkspaceCard("Release Assets"),
                createWorkspaceCard("User Research"),
                createWorkspaceCard("QA Reports"));

        // Collaborative section
        Label collabTitle = new Label("Collaborative Workspace");
        collabTitle.getStyleClass().add("section-title");

        FlowPane collabGrid = new FlowPane();
        collabGrid.getStyleClass().add("workspace-grid");
        collabGrid.setHgap(12);
        collabGrid.setVgap(12);
        collabGrid.getChildren().addAll(
                createWorkspaceCard("Team Alpha"),
                createWorkspaceCard("Team Beta"),
                createWorkspaceCard("Team Gamma"));

        left.getChildren().addAll(header, grid, collabTitle, collabGrid);
        VBox.setVgrow(grid, Priority.NEVER);
        VBox.setVgrow(collabGrid, Priority.NEVER);
        return left;
    }

    private Node loadAnalyticsPanel() {
        Node node = loadFxmlNode("/fxml/AnalyticsPanel.fxml");
        Object controller = node.getProperties().get("fx:controller");
        if (controller instanceof AnalyticsPanelController analytics) {
            analytics.setStats(42, 6);
        }
        return node;
    }

    private Node createWorkspaceCard(String title) {
        Node node = loadFxmlNode("/fxml/WorkspaceCard.fxml");
        Object controller = node.getProperties().get("fx:controller");
        if (controller instanceof WorkspaceCardController cardController) {
            cardController.setTitle(title);
        }
        return node;
    }

    private static Node loadFxmlNode(String resource) {
        URL url = DashboardContentController.class.getResource(resource);
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
