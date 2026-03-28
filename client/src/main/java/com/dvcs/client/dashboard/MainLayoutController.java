package com.dvcs.client.dashboard;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;

public class MainLayoutController {

    @FXML
    private BorderPane root;

    @FXML
    private void initialize() {
        Node navbar = loadFxmlNode("/fxml/Navbar.fxml");
        VBox topContainer = new VBox(navbar);
        topContainer.setPadding(new Insets(10, 18, 0, 18));
        root.setTop(topContainer);
        root.setCenter(loadFxmlNode("/fxml/DashboardContent.fxml"));
    }

    public void setUsername(String username) {
        Node top = root.getTop();
        if (top == null)
            return;

        Object controller = top.getProperties().get("fx:controller");
        if (controller instanceof com.dvcs.client.dashboard.navbar.NavbarController navbarController) {
            navbarController.setUsername(username);
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
            // Store controller for later lookup (simple and avoids extra wiring)
            node.getProperties().put("fx:controller", loader.getController());
            return node;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + resource, e);
        }
    }
}
