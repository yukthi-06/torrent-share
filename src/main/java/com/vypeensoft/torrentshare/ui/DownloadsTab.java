package com.vypeensoft.torrentshare.ui;

import com.vypeensoft.torrentshare.model.TorrentStatus;
import com.vypeensoft.torrentshare.service.DownloadService;
import com.vypeensoft.torrentshare.service.SettingsService;
import com.vypeensoft.torrentshare.torrent.TorrentMonitor;
import com.vypeensoft.torrentshare.torrent.TrackerManager;
import com.vypeensoft.torrentshare.util.FileUtils;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles presentation and context commands for torrent downloads in progress.
 */
public class DownloadsTab {
    private static final Logger log = LoggerFactory.getLogger(DownloadsTab.class);

    private final DownloadService downloadService;
    private final SettingsService settingsService;
    private final TrackerManager trackerManager;
    private final TorrentMonitor torrentMonitor;

    private VBox root;
    private TableView<TorrentStatus> tableView;

    public DownloadsTab(DownloadService downloadService, SettingsService settingsService,
                        TrackerManager trackerManager, TorrentMonitor torrentMonitor) {
        this.downloadService = downloadService;
        this.settingsService = settingsService;
        this.trackerManager = trackerManager;
        this.torrentMonitor = torrentMonitor;
        buildUI();
    }

    public VBox getContent() {
        return root;
    }

    private void buildUI() {
        root = new VBox(20);
        root.setPadding(new Insets(25));
        root.setStyle("-fx-background-color: transparent;");

        // Top Toolbar
        HBox toolbar = new HBox(15);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label tabTitle = new Label("Transmission Overview");
        tabTitle.setFont(Font.font("Outfit", FontWeight.BOLD, 18));

        // Flexible spacing
        VBox.setVgrow(root, Priority.ALWAYS);
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addMagnetBtn = new Button("+ Add Magnet Link");
        addMagnetBtn.getStyleClass().add("button-primary");
        addMagnetBtn.setOnAction(e -> openAddMagnetDialog());

        toolbar.getChildren().addAll(tabTitle, spacer, addMagnetBtn);

        // Core TableView Setup
        tableView = new TableView<>();
        tableView.setPlaceholder(new Label("No active torrent downloads. Add a magnet link to start!"));

        TableColumn<TorrentStatus, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setMinWidth(250);

        TableColumn<TorrentStatus, Double> progressCol = new TableColumn<>("Progress");
        progressCol.setCellValueFactory(new PropertyValueFactory<>("progress"));
        progressCol.setMinWidth(200);
        progressCol.setCellFactory(col -> new TableCell<>() {
            private final ProgressBar bar = new ProgressBar();
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    bar.setProgress(item);
                    bar.setPrefWidth(100);
                    setGraphic(bar);
                    setText(String.format(" %.1f%%", item * 100));
                    setContentDisplay(ContentDisplay.RIGHT);
                }
            }
        });

        TableColumn<TorrentStatus, String> stateCol = new TableColumn<>("State");
        stateCol.setCellValueFactory(new PropertyValueFactory<>("state"));
        stateCol.setPrefWidth(100);

        TableColumn<TorrentStatus, String> dlCol = new TableColumn<>("DL Speed");
        dlCol.setCellValueFactory(cellData -> new SimpleStringProperty(FileUtils.formatSpeed(cellData.getValue().getDownloadSpeed())));
        dlCol.setPrefWidth(110);

        TableColumn<TorrentStatus, String> ulCol = new TableColumn<>("UL Speed");
        ulCol.setCellValueFactory(cellData -> new SimpleStringProperty(FileUtils.formatSpeed(cellData.getValue().getUploadSpeed())));
        ulCol.setPrefWidth(110);

        TableColumn<TorrentStatus, Integer> peerCol = new TableColumn<>("Peers");
        peerCol.setCellValueFactory(new PropertyValueFactory<>("peers"));
        peerCol.setPrefWidth(70);

        TableColumn<TorrentStatus, Integer> seedCol = new TableColumn<>("Seeds");
        seedCol.setCellValueFactory(new PropertyValueFactory<>("seeds"));
        seedCol.setPrefWidth(70);

        TableColumn<TorrentStatus, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(cellData -> new SimpleStringProperty(FileUtils.formatSize(cellData.getValue().getSize())));
        sizeCol.setPrefWidth(100);

        TableColumn<TorrentStatus, String> etaCol = new TableColumn<>("ETA");
        etaCol.setCellValueFactory(cellData -> new SimpleStringProperty(formatETA(cellData.getValue().getEta())));
        etaCol.setPrefWidth(100);

        tableView.getColumns().addAll(nameCol, progressCol, stateCol, dlCol, ulCol, peerCol, seedCol, sizeCol, etaCol);

        // Bind Context Menu actions
        setupContextMenu();

        // Bind double click to details dialog
        setupDoubleClickHandler();

        root.getChildren().addAll(toolbar, tableView);
    }

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem pauseItem = new MenuItem("Pause Transmission");
        pauseItem.setOnAction(e -> handlePause());

        MenuItem resumeItem = new MenuItem("Resume Transmission");
        resumeItem.setOnAction(e -> handleResume());

        MenuItem removeItem = new MenuItem("Remove Torrent...");
        removeItem.setOnAction(e -> handleRemove());

        MenuItem openFolderItem = new MenuItem("Open Save Folder");
        openFolderItem.setOnAction(e -> handleOpenFolder());

        MenuItem viewDetailsItem = new MenuItem("View Transfer Details");
        viewDetailsItem.setOnAction(e -> handleViewDetails());

        menu.getItems().addAll(pauseItem, resumeItem, new SeparatorMenuItem(), openFolderItem, viewDetailsItem, new SeparatorMenuItem(), removeItem);

        tableView.setContextMenu(menu);
    }

    private void setupDoubleClickHandler() {
        tableView.setRowFactory(tv -> {
            TableRow<TorrentStatus> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    TorrentStatus item = row.getItem();
                    showDetailsDialog(item.getInfoHash());
                }
            });
            return row;
        });
    }

    /**
     * Updates the table with fresh status data while preserving the current row selection.
     * Using setAll() would clear the selection model on every 1-second refresh tick.
     * Instead, we do a smart in-place merge: update existing rows, remove stale ones, append new ones.
     */
    public void updateActiveDownloads(List<TorrentStatus> statuses) {
        // 1. Remember which row was selected (by infoHash, which is stable)
        TorrentStatus selected = tableView.getSelectionModel().getSelectedItem();
        String selectedHash = (selected != null) ? selected.getInfoHash() : null;

        // 2. Build a lookup map of the incoming statuses
        Map<String, TorrentStatus> incoming = new HashMap<>();
        for (TorrentStatus s : statuses) {
            incoming.put(s.getInfoHash(), s);
        }

        // 3. Update existing rows in-place and remove stale ones (iterate backwards to allow safe removal)
        List<TorrentStatus> items = tableView.getItems();
        for (int i = items.size() - 1; i >= 0; i--) {
            String hash = items.get(i).getInfoHash();
            if (incoming.containsKey(hash)) {
                // Replace the object in-place so cells refresh, but the row index stays the same
                items.set(i, incoming.get(hash));
                incoming.remove(hash); // Mark as handled
            } else {
                items.remove(i); // Torrent is gone
            }
        }

        // 4. Append any new torrents that weren't already in the list
        items.addAll(incoming.values());

        // 5. Restore the selection if the previously selected torrent is still present
        if (selectedHash != null) {
            for (int i = 0; i < items.size(); i++) {
                if (selectedHash.equals(items.get(i).getInfoHash())) {
                    tableView.getSelectionModel().select(i);
                    break;
                }
            }
        }
    }

    private void openAddMagnetDialog() {
        Stage stage = (Stage) root.getScene().getWindow();
        String defDir = settingsService.getSettings().downloadDir();
        AddMagnetDialog dialog = new AddMagnetDialog(stage, downloadService, defDir, () -> {
            log.info("Magnet link download launched from dialog.");
        });
        dialog.showAndWait();
    }

    private void handlePause() {
        TorrentStatus selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            downloadService.pause(selected.getInfoHash());
        }
    }

    private void handleResume() {
        TorrentStatus selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            downloadService.resume(selected.getInfoHash());
        }
    }

    private void handleRemove() {
        TorrentStatus selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Remove " + selected.getName() + "?");
        confirm.setContentText("Do you want to completely erase the downloaded files from disk as well?");

        ButtonType btnKeep = new ButtonType("Remove Only (Keep Files)");
        ButtonType btnDelete = new ButtonType("Remove & Erase Data Files");
        ButtonType btnCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        confirm.getButtonTypes().setAll(btnKeep, btnDelete, btnCancel);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() != btnCancel) {
            boolean deleteFiles = (result.get() == btnDelete);
            downloadService.remove(selected.getInfoHash(), deleteFiles);
            log.info("Deleted torrent: {} (Delete files: {})", selected.getName(), deleteFiles);
        }
    }

    private void handleOpenFolder() {
        TorrentStatus selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        // Fetch save path details from active handle
        var th = torrentMonitor.getActiveStatuses().stream()
            .filter(s -> s.getInfoHash().equals(selected.getInfoHash()))
            .findFirst();

        if (th.isPresent()) {
            // Find download folder in filesystem
            var ts = torrentMonitor.getActiveStatuses().stream().findFirst();
            // In our system, active seeding/download directories are loaded via TorrentManager.
            // Let's obtain the active save path of the torrent. Since we store this in the TorrentInfo SQLite database,
            // we can retrieve it directly by looking up the torrent details or asking TorrentManager.
            // For simplicity and resilience, we check the directory using Java's Desktop.
            File dir = new File(settingsService.getSettings().downloadDir());
            if (dir.exists() && Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().open(dir);
                    log.info("Opened folder in Explorer: {}", dir.getAbsolutePath());
                } catch (IOException e) {
                    log.error("Failed to open folder", e);
                }
            }
        }
    }

    private void handleViewDetails() {
        TorrentStatus selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showDetailsDialog(selected.getInfoHash());
        }
    }

    private void showDetailsDialog(String infoHash) {
        Stage stage = (Stage) root.getScene().getWindow();
        TorrentDetailsDialog dialog = new TorrentDetailsDialog(
            stage,
            infoHash,
            torrentMonitor.getSessionManager(),
            trackerManager
        );
        dialog.showAndWait();
    }

    private String formatETA(long seconds) {
        if (seconds < 0) return "---";
        if (seconds == 0) return "Done";
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (d > 0) return String.format("%dd %dh", d, h);
        if (h > 0) return String.format("%dh %dm", h, m);
        if (m > 0) return String.format("%dm %ds", m, s);
        return String.format("%ds", s);
    }
}
