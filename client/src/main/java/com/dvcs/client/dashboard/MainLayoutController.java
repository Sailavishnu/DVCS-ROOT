package com.dvcs.client.dashboard;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

import java.io.IOException;
import java.net.URL;

public class MainLayoutController {

    @FXML
    private BorderPane root;

    @FXML
    private void initialize() {
        root.setTop(loadFxmlNode("/fxml/Navbar.fxml"));
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
