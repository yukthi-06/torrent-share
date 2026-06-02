package com.vypeensoft.torrentshare.ui;

import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.vypeensoft.torrentshare.persistence.SettingsRepository;
import com.vypeensoft.torrentshare.torrent.SessionManager;
import com.vypeensoft.torrentshare.torrent.TrackerManager;
import com.vypeensoft.torrentshare.util.FileUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Custom Tabbed Dialog exposing detailed metrics and tree views for active torrents.
 */
public class TorrentDetailsDialog extends Stage {
    private static final Logger log = LoggerFactory.getLogger(TorrentDetailsDialog.class);

    private final String infoHash;
    private final SessionManager sessionManager;
    private final TrackerManager trackerManager;

    private VBox root;
    private TabPane tabPane;

    public TorrentDetailsDialog(Stage owner, String infoHash, SessionManager sessionManager, TrackerManager trackerManager) {
        this.infoHash = infoHash;
        this.sessionManager = sessionManager;
        this.trackerManager = trackerManager;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Torrent Transmission Details");

        buildUI();
    }

    private void buildUI() {
        root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-pref-width: 650px; -fx-pref-height: 550px;");

        // Load active native handle
        TorrentHandle th = sessionManager.getJlibtorrentSession().find(new Sha1Hash(infoHash));

        if (th == null || !th.isValid()) {
            Label err = new Label("Torrent handle is no longer valid.");
            err.setTextFill(Color.web("#f87171"));
            root.getChildren().add(err);
            return;
        }

        com.frostwire.jlibtorrent.TorrentStatus ts = th.status();
        TorrentInfo ti = th.torrentFile();

        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab 1: General Info
        Tab genTab = new Tab("General", buildGeneralTab(ts, ti));
        // Tab 2: Network Info
        Tab netTab = new Tab("Network", buildNetworkTab(ts, th));
        // Tab 3: Transfer Info
        Tab xferTab = new Tab("Transfer", buildTransferTab(ts));
        // Tab 4: File List Tree View
        Tab fileTab = new Tab("Files", buildFilesTab(ti));

        tabPane.getTabs().addAll(genTab, netTab, xferTab, fileTab);
        
        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("button-secondary");
        closeBtn.setAlignment(Pos.CENTER_RIGHT);
        closeBtn.setOnAction(e -> close());

        root.getChildren().addAll(tabPane, closeBtn);

        Scene scene = new Scene(root);
        setScene(scene);
    }

    private ScrollPane buildGeneralTab(com.frostwire.jlibtorrent.TorrentStatus ts, TorrentInfo ti) {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setHgap(30);
        grid.setVgap(15);

        createMetaRow(grid, "Torrent Name:", ts.name(), 0);
        createMetaRow(grid, "Info Hash:", infoHash, 1);
        
        long totalSize = ts.totalWanted();
        if (totalSize == 0 && ti != null) {
            totalSize = ti.totalSize();
        }
        createMetaRow(grid, "Total Size:", FileUtils.formatSize(totalSize), 2);

        if (ti != null) {
            createMetaRow(grid, "Piece Size:", FileUtils.formatSize(ti.pieceLength()), 3);
            createMetaRow(grid, "Piece Count:", String.valueOf(ti.numPieces()), 4);
        } else {
            createMetaRow(grid, "Piece Size:", "Fetching metadata...", 3);
            createMetaRow(grid, "Piece Count:", "Fetching metadata...", 4);
        }

        ScrollPane sp = new ScrollPane(grid);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return sp;
    }

    private ScrollPane buildNetworkTab(com.frostwire.jlibtorrent.TorrentStatus ts, TorrentHandle th) {
        VBox box = new VBox(20);
        box.setPadding(new Insets(20));

        GridPane grid = new GridPane();
        grid.setHgap(30);
        grid.setVgap(12);

        createMetaRow(grid, "Connected Seeds:", String.valueOf(ts.numSeeds()), 0);
        createMetaRow(grid, "Connected Peers:", String.valueOf(ts.numPeers()), 1);
        createMetaRow(grid, "Active DHT Nodes:", String.valueOf(sessionManager.getJlibtorrentSession().dhtNodes()), 2);

        box.getChildren().add(grid);

        // Show trackers list
        Label trackerLbl = new Label("Active Trackers Announce URIs:");
        trackerLbl.setFont(Font.font("Inter", FontWeight.BOLD, 12));

        ListView<String> trackersList = new ListView<>();
        trackersList.setPrefHeight(180);
        
        List<String> list = trackerManager.getTrackers();
        if (list.isEmpty()) {
            trackersList.getItems().add("No trackers configured. DHT and LSD discovery active.");
        } else {
            trackersList.getItems().addAll(list);
        }
        box.getChildren().addAll(trackerLbl, trackersList);

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return sp;
    }

    private ScrollPane buildTransferTab(com.frostwire.jlibtorrent.TorrentStatus ts) {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setHgap(30);
        grid.setVgap(15);

        createMetaRow(grid, "Total Downloaded:", FileUtils.formatSize(ts.totalDone()), 0);
        createMetaRow(grid, "Total Uploaded:", FileUtils.formatSize(ts.allTimeUpload()), 1);
        
        double ratio = ts.totalDone() > 0 ? (double) ts.allTimeUpload() / ts.totalDone() : 0.0;
        createMetaRow(grid, "Sharing Ratio:", String.format("%.3f", ratio), 2);
        createMetaRow(grid, "Download Speed:", FileUtils.formatSpeed(ts.downloadRate()), 3);
        createMetaRow(grid, "Upload Speed:", FileUtils.formatSpeed(ts.uploadRate()), 4);

        ScrollPane sp = new ScrollPane(grid);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return sp;
    }

    private ScrollPane buildFilesTab(TorrentInfo ti) {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));

        if (ti == null) {
            Label loading = new Label("Metadata is still downloading from network peers...\nFiles tree will be available shortly.");
            loading.setFont(Font.font("Inter", 13));
            box.getChildren().add(loading);
        } else {
            FileStorage fs = ti.files();
            TreeItem<String> rootItem = new TreeItem<>(ti.name());
            rootItem.setExpanded(true);

            for (int i = 0; i < fs.numFiles(); i++) {
                String path = fs.filePath(i);
                long fileSize = fs.fileSize(i);
                addFileToTree(rootItem, path, fileSize);
            }

            TreeView<String> treeView = new TreeView<>(rootItem);
            treeView.setPrefHeight(350);
            box.getChildren().add(treeView);
        }

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return sp;
    }

    private void addFileToTree(TreeItem<String> rootNode, String filePath, long size) {
        String[] parts = filePath.split("/");
        TreeItem<String> current = rootNode;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i == parts.length - 1) {
                // Leaf file node
                TreeItem<String> leaf = new TreeItem<>(part + " (" + FileUtils.formatSize(size) + ")");
                current.getChildren().add(leaf);
            } else {
                // Folder node
                TreeItem<String> folder = findOrCreateFolderNode(current, part);
                current = folder;
            }
        }
    }

    private TreeItem<String> findOrCreateFolderNode(TreeItem<String> parent, String folderName) {
        for (TreeItem<String> child : parent.getChildren()) {
            if (child.getValue().equals(folderName)) {
                return child;
            }
        }
        TreeItem<String> newFolder = new TreeItem<>(folderName);
        newFolder.setExpanded(true);
        parent.getChildren().add(newFolder);
        return newFolder;
    }

    private void createMetaRow(GridPane grid, String key, String value, int row) {
        Label keyLbl = new Label(key);
        keyLbl.setFont(Font.font("Inter", FontWeight.BOLD, 13));
        grid.add(keyLbl, 0, row);

        Label valLbl = new Label(value);
        valLbl.setFont(Font.font("Inter", 13));
        grid.add(valLbl, 1, row);
    }
}
