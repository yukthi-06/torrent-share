package com.vypeensoft.torrentshare.ui;

import com.vypeensoft.torrentshare.model.ApplicationSettings;
import com.vypeensoft.torrentshare.service.SettingsService;
import com.vypeensoft.torrentshare.torrent.TrackerManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Global Configuration Tab Panel for ports, rates, directories, and tracker addresses.
 */
public class SettingsTab {
    private static final Logger log = LoggerFactory.getLogger(SettingsTab.class);

    private final SettingsService settingsService;
    private final TrackerManager trackerManager;

    private VBox root;
    private TextField downloadDirField;
    private TextField shareDirField;
    private TextField portField;
    private TextField uploadLimitField;   // Input as KB/s
    private TextField downloadLimitField; // Input as KB/s
    private TextField connectionsField;
    private TextField activeTorrentsField;

    // Trackers Management UI
    private ListView<String> trackersList;
    private TextField newTrackerField;

    public SettingsTab(SettingsService settingsService, TrackerManager trackerManager) {
        this.settingsService = settingsService;
        this.trackerManager = trackerManager;
        buildUI();
    }

    public VBox getContent() {
        return root;
    }

    private void buildUI() {
        root = new VBox(24);
        root.setPadding(new Insets(25));
        root.setStyle("-fx-background-color: transparent;");

        // Use scroll container for dense settings screen
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        VBox contentBox = new VBox(24);
        contentBox.setStyle("-fx-background-color: transparent;");

        // 1. General Panel
        VBox genPanel = new VBox(15);
        genPanel.getStyleClass().add("glass-panel");

        Label genTitle = new Label("Bandwidth & Paths Settings");
        genTitle.setFont(Font.font("Outfit", FontWeight.BOLD, 18));
        genTitle.setStyle("-fx-text-fill: -fx-text-base-color;");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(15);

        // Directories Input
        createBrowseRow(grid, "Download Location:", downloadDirField = new TextField(), 0);
        createBrowseRow(grid, "Local Sharing Folder:", shareDirField = new TextField(), 1);

        // Listening Port
        createInputRow(grid, "Listen Network Port:", portField = new TextField(), 2, "Default: 6881");

        // Speed limits
        createInputRow(grid, "Upload Speed Limit (KB/s):", uploadLimitField = new TextField(), 3, "0 means unlimited");
        createInputRow(grid, "Download Speed Limit (KB/s):", downloadLimitField = new TextField(), 4, "0 means unlimited");

        // Max connections and torrents
        createInputRow(grid, "Max Global Connections:", connectionsField = new TextField(), 5, "Default: 200");
        createInputRow(grid, "Max Active Downloads:", activeTorrentsField = new TextField(), 6, "Default: 20");

        genPanel.getChildren().addAll(genTitle, grid);

        // 2. Trackers Panel
        VBox trackPanel = new VBox(15);
        trackPanel.getStyleClass().add("glass-panel");

        Label trackTitle = new Label("BitTorrent Trackers Manager");
        trackTitle.setFont(Font.font("Outfit", FontWeight.BOLD, 18));
        trackTitle.setStyle("-fx-text-fill: -fx-text-base-color;");

        trackersList = new ListView<>();
        trackersList.setPrefHeight(150);

        HBox trackerActions = new HBox(10);
        newTrackerField = new TextField();
        newTrackerField.setPromptText("udp://tracker.example.com:80/announce");
        newTrackerField.setPrefWidth(350);

        Button addBtn = new Button("Add Tracker");
        addBtn.getStyleClass().add("button-primary");
        addBtn.setOnAction(e -> handleAddTracker());

        Button deleteBtn = new Button("Remove Tracker");
        deleteBtn.getStyleClass().add("button-secondary");
        deleteBtn.setOnAction(e -> handleDeleteTracker());

        trackerActions.getChildren().addAll(newTrackerField, addBtn, deleteBtn);
        trackPanel.getChildren().addAll(trackTitle, trackersList, trackerActions);

        // Save Button panel
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_RIGHT);

        Button saveBtn = new Button("Save Global Configuration");
        saveBtn.getStyleClass().add("button-primary");
        saveBtn.setOnAction(e -> handleSaveSettings());
        footer.getChildren().add(saveBtn);

        contentBox.getChildren().addAll(genPanel, trackPanel, footer);
        scroll.setContent(contentBox);
        root.getChildren().add(scroll);

        // Load persisted values into the input controls
        loadConfigValues();
    }

    private void createBrowseRow(GridPane grid, String title, TextField tf, int row) {
        Label lbl = new Label(title);
        lbl.setFont(Font.font("Inter", FontWeight.BOLD, 12));
        lbl.setStyle("-fx-text-fill: -fx-text-base-color;");
        grid.add(lbl, 0, row);

        HBox box = new HBox(8);
        tf.setPrefWidth(450);
        Button browse = new Button("Browse");
        browse.getStyleClass().add("button-secondary");
        browse.setOnAction(e -> selectDirectory(tf));
        box.getChildren().addAll(tf, browse);
        grid.add(box, 1, row);
    }

    private void createInputRow(GridPane grid, String title, TextField tf, int row, String tooltip) {
        Label lbl = new Label(title);
        lbl.setFont(Font.font("Inter", FontWeight.BOLD, 12));
        lbl.setStyle("-fx-text-fill: -fx-text-base-color;");
        grid.add(lbl, 0, row);

        HBox box = new HBox(12);
        tf.setPrefWidth(200);
        Label hint = new Label(tooltip);
        hint.setFont(Font.font("Inter", 11));
        hint.setStyle("-fx-text-fill: -fx-text-base-color;");
        hint.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(tf, hint);
        grid.add(box, 1, row);
    }

    private void selectDirectory(TextField tf) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Configuration Folder Path");
        File current = new File(tf.getText());
        if (current.exists() && current.isDirectory()) {
            dc.setInitialDirectory(current);
        }
        File selected = dc.showDialog(root.getScene().getWindow());
        if (selected != null) {
            tf.setText(selected.getAbsolutePath());
        }
    }

    private void loadConfigValues() {
        ApplicationSettings settings = settingsService.getSettings();
        downloadDirField.setText(settings.downloadDir());
        shareDirField.setText(settings.shareDir());
        portField.setText(String.valueOf(settings.listeningPort()));
        uploadLimitField.setText(String.valueOf(settings.uploadLimit() / 1024));
        downloadLimitField.setText(String.valueOf(settings.downloadLimit() / 1024));
        connectionsField.setText(String.valueOf(settings.maxConnections()));
        activeTorrentsField.setText(String.valueOf(settings.maxActiveTorrents()));

        // Populate trackers
        refreshTrackersList();
    }

    private void refreshTrackersList() {
        trackersList.getItems().clear();
        trackersList.getItems().addAll(trackerManager.getTrackers());
    }

    private void handleAddTracker() {
        String url = newTrackerField.getText().trim();
        if (url.isEmpty()) return;

        if (!url.startsWith("udp://") && !url.startsWith("http://") && !url.startsWith("https://")) {
            showError("Invalid Tracker", "Announce URL must start with udp://, http://, or https://");
            return;
        }

        trackerManager.addTracker(url);
        newTrackerField.clear();
        refreshTrackersList();
    }

    private void handleDeleteTracker() {
        String selected = trackersList.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        trackerManager.removeTracker(selected);
        refreshTrackersList();
    }

    private void handleSaveSettings() {
        try {
            String dl = downloadDirField.getText().trim();
            String sh = shareDirField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            long ul = Long.parseLong(uploadLimitField.getText().trim()) * 1024;
            long dlLimit = Long.parseLong(downloadLimitField.getText().trim()) * 1024;
            int connections = Integer.parseInt(connectionsField.getText().trim());
            int torrents = Integer.parseInt(activeTorrentsField.getText().trim());

            if (dl.isEmpty() || sh.isEmpty()) {
                showError("Validation Error", "Folders paths cannot be empty.");
                return;
            }

            if (port < 1 || port > 65535) {
                showError("Validation Error", "Listening Port must be between 1 and 65535.");
                return;
            }

            ApplicationSettings settings = new ApplicationSettings(dl, sh, port, ul, dlLimit, connections, torrents);
            settingsService.updateSettings(settings);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Settings Saved");
            alert.setHeaderText(null);
            alert.setContentText("Configuration successfully updated and active session limits updated!");
            alert.showAndWait();

        } catch (NumberFormatException e) {
            showError("Type Error", "Please ensure listening ports, speed limits, and connections are valid positive numbers.");
        }
    }

    private void showError(String title, String details) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(details);
        alert.showAndWait();
    }
}
