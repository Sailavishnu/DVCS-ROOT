package com.dvcs.client.dashboard.content;

import com.dvcs.client.dashboard.analytics.AnalyticsPanelController;
import com.dvcs.client.dashboard.workspace.WorkspaceCardController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;

import java.io.IOException;
import java.net.URL;

public class DashboardContentController {

    private static final double MY_WORKSPACE_CARD_HEIGHT = 160;
    private static final double COLLAB_CARD_HEIGHT = 160;
    private static final double ANALYTICS_PANEL_MIN_WIDTH = 420;

    @FXML
    private HBox mainCard;

    @FXML
    private AnchorPane overlayRoot;

    @FXML
    private void initialize() {
        mainCard.getChildren().setAll(buildWorkspaceSection(), loadAnalyticsPanel());
        if (!mainCard.getChildren().isEmpty()) {
            HBox.setHgrow(mainCard.getChildren().getFirst(), Priority.ALWAYS);
            if (mainCard.getChildren().size() > 1) {
                HBox.setHgrow(mainCard.getChildren().get(1), Priority.ALWAYS);
            }
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

        // Dominant card with comfortable glass margins.
        double cardW = Math.max(1080, availableW * 0.92);
        double cardH = Math.max(660, availableH * 0.80);

        mainCard.setPrefWidth(Math.min(cardW, availableW - 24));
        mainCard.setPrefHeight(Math.min(cardH, availableH - 24));

        // Center card and keep the hero/title zone visually detached from the glass
        // card.
        AnchorPane.setLeftAnchor(mainCard, (availableW - mainCard.getPrefWidth()) / 2.0);
        AnchorPane.setTopAnchor(mainCard, Math.max(158, 300 - (mainCard.getPrefHeight() * 0.22)));
    }

    private Node buildWorkspaceSection() {
        VBox workspaceContent = new VBox(22);
        workspaceContent.getStyleClass().add("workspace-section");

        VBox workspaceGlass = new VBox(22);
        workspaceGlass.getStyleClass().addAll("glass", "workspace-glass");

        // 3-column grid:
        // [ My Workspace | My Workspace | Collaborative Workspace ]
        GridPane workspaceRegion = new GridPane();
        workspaceRegion.getStyleClass().add("workspace-region");
        workspaceRegion.setHgap(30);
        workspaceRegion.setVgap(30);

        ColumnConstraints c0 = new ColumnConstraints();
        ColumnConstraints c1 = new ColumnConstraints();
        ColumnConstraints c2 = new ColumnConstraints();
        c0.setPercentWidth(40.0);
        c1.setPercentWidth(26.6667);
        c2.setPercentWidth(33.3333);
        workspaceRegion.getColumnConstraints().setAll(c0, c1, c2);

        VBox myWorkspaceBox = new VBox(14);
        Label myTitle = new Label("My Workspace");
        myTitle.getStyleClass().add("section-title");

        GridPane myGrid = new GridPane();
        myGrid.getStyleClass().add("workspace-gridpane");
        myGrid.setHgap(30);
        myGrid.setVgap(30);

        myGrid.add(createWorkspaceCard("Design Docs"), 0, 0);
        myGrid.add(createWorkspaceCard("API Specs"), 1, 0);
        myGrid.add(createWorkspaceCard("Sprint Notes"), 0, 1);
        myGrid.add(createWorkspaceCard("Release Assets"), 1, 1);

        myWorkspaceBox.getChildren().addAll(myTitle, myGrid);

        VBox collabBox = new VBox(14);
        Label collabTitle = new Label("Collaborative Workspace");
        collabTitle.getStyleClass().add("section-title");

        VBox collabList = new VBox(25);
        collabList.getStyleClass().add("collab-list");
        Node alpha = createCollaborativeRow("Team Alpha");
        Node beta = createCollaborativeRow("Team Beta");
        Node gamma = createCollaborativeRow("Team Gamma");
        Node delta = createCollaborativeRow("Team Delta");

        if (alpha instanceof javafx.scene.layout.Region ar) {
            ar.setPrefHeight(COLLAB_CARD_HEIGHT);
            ar.setMinHeight(COLLAB_CARD_HEIGHT);
        }
        if (beta instanceof javafx.scene.layout.Region br) {
            br.setPrefHeight(COLLAB_CARD_HEIGHT);
            br.setMinHeight(COLLAB_CARD_HEIGHT);
        }

        if (gamma instanceof javafx.scene.layout.Region gr) {
            gr.setPrefHeight(COLLAB_CARD_HEIGHT);
            gr.setMinHeight(COLLAB_CARD_HEIGHT);
        }
        if (delta instanceof javafx.scene.layout.Region dr) {
            dr.setPrefHeight(COLLAB_CARD_HEIGHT);
            dr.setMinHeight(COLLAB_CARD_HEIGHT);
        }

        collabList.getChildren().addAll(alpha, beta, gamma, delta);
        collabBox.getChildren().addAll(collabTitle, collabList);

        workspaceRegion.add(myWorkspaceBox, 0, 0, 2, 1);
        workspaceRegion.add(collabBox, 2, 0);

        workspaceGlass.getChildren().addAll(workspaceRegion);
        workspaceContent.getChildren().addAll(workspaceGlass);

        ScrollPane scrollPane = new ScrollPane(workspaceContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("workspace-scroll");

        return scrollPane;
    }

    private Node createCollaborativeRow(String title) {
        Node node = loadFxmlNode("/fxml/WorkspaceCard.fxml");
        node.getStyleClass().add("collab-row");
        Object controller = node.getProperties().get("fx:controller");
        if (controller instanceof WorkspaceCardController cardController) {
            cardController.setTitle(title);
        }
        return node;
    }

    private Node loadAnalyticsPanel() {
        Node node = loadFxmlNode("/fxml/AnalyticsPanel.fxml");
        node.getStyleClass().add("glass-analytics");
        if (node instanceof javafx.scene.layout.Region region) {
            region.setMinWidth(ANALYTICS_PANEL_MIN_WIDTH);
            region.setPrefWidth(ANALYTICS_PANEL_MIN_WIDTH);
            region.setMaxWidth(Double.MAX_VALUE);
        }
        Object controller = node.getProperties().get("fx:controller");
        if (controller instanceof AnalyticsPanelController analytics) {
            analytics.setStats(42, 6);
        }
        return node;
    }

    private Node createWorkspaceCard(String title) {
        Node node = loadFxmlNode("/fxml/WorkspaceCard.fxml");
        if (node instanceof javafx.scene.layout.Region region) {
            region.setPrefHeight(MY_WORKSPACE_CARD_HEIGHT);
            region.setMinHeight(MY_WORKSPACE_CARD_HEIGHT);
        }
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
