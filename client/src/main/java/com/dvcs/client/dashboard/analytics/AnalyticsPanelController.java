package com.dvcs.client.dashboard.analytics;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;

public class AnalyticsPanelController {

    @FXML
    private Label commitsValue;

    @FXML
    private Label activeWorkspacesValue;

    @FXML
    private Label taglineLabel;

    @FXML
    private Label highlightsLabel;

    @FXML
    private LineChart<String, Number> activityChart;

    @FXML
    private CategoryAxis xAxis;

    public void setStats(int commits, int activeWorkspaces) {
        if (commitsValue != null) {
            commitsValue.setText(String.valueOf(commits));
        }
        if (activeWorkspacesValue != null) {
            activeWorkspacesValue.setText(String.valueOf(activeWorkspaces));
        }

        if (taglineLabel != null) {
            taglineLabel.setText("Ship clean versions. Track every change.");
        }
        if (highlightsLabel != null) {
            highlightsLabel.setText("• 3 docs reviewed\n• 1 merge queued\n• 2 teammates active");
        }

        if (activityChart != null) {
            if (xAxis != null) {
                xAxis.setCategories(FXCollections.observableArrayList("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"));
            }

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.getData().add(new XYChart.Data<>("Mon", 4));
            series.getData().add(new XYChart.Data<>("Tue", 8));
            series.getData().add(new XYChart.Data<>("Wed", 6));
            series.getData().add(new XYChart.Data<>("Thu", 11));
            series.getData().add(new XYChart.Data<>("Fri", 9));
            series.getData().add(new XYChart.Data<>("Sat", 13));
            series.getData().add(new XYChart.Data<>("Sun", 10));

            activityChart.getData().setAll(series);
        }
    }
}
