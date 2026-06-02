package com.vypeensoft.torrentshare.ui;

import com.frostwire.jlibtorrent.TorrentInfo;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.vypeensoft.torrentshare.model.ApplicationSettings;
import com.vypeensoft.torrentshare.service.ShareService;
import com.vypeensoft.torrentshare.service.SettingsService;
import com.vypeensoft.torrentshare.util.FileUtils;
import com.vypeensoft.torrentshare.util.MagnetUtils;
import com.vypeensoft.torrentshare.worker.TorrentCreationTask;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles Torrent creation and local sharing seeding UI controls.
 */
public class ShareTab {
    private static final Logger log = LoggerFactory.getLogger(ShareTab.class);

    private final ShareService shareService;
    private final SettingsService settingsService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "torrent-creator-worker");
        thread.setDaemon(true);
        return thread;
    });

    private VBox root;
    private StackPane dragZone;
    private VBox infoPanel;

    // UI Information Labels
    private Label nameVal;
    private Label sizeVal;
    private Label hashVal;
    private Label pieceVal;
    private Label countVal;

    private TextArea magnetText;
    private File generatedTorrentFile;
    private String generatedMagnetLink;

    public ShareTab(ShareService shareService, SettingsService settingsService) {
        this.shareService = shareService;
        this.settingsService = settingsService;
        buildUI();
    }

    public VBox getContent() {
        return root;
    }

    private void buildUI() {
        root = new VBox(20);
        root.setPadding(new Insets(25));
        root.setStyle("-fx-background-color: transparent;");

        // Top Drag and Drop Zone Container
        dragZone = new StackPane();
        dragZone.getStyleClass().add("drag-zone");
        dragZone.setMinHeight(200);

        VBox dragContent = new VBox(12);
        dragContent.setAlignment(Pos.CENTER);
        
        Label dropIcon = new Label("📥");
        dropIcon.setFont(Font.font(36));
        dropIcon.setTextFill(Color.web("#9ca3af"));

        Label dropText = new Label("Drag & Drop File or Folder Here to Share");
        dropText.setFont(Font.font("Outfit", FontWeight.BOLD, 18));
        dropText.setTextFill(Color.WHITE);

        Label dropSubtext = new Label("Accepts single files, multiple files, and full directories recursively");
        dropSubtext.setFont(Font.font("Inter", 12));
        dropSubtext.setTextFill(Color.web("#6b7280"));

        dragContent.getChildren().addAll(dropIcon, dropText, dropSubtext);
        dragZone.getChildren().add(dragContent);

        // Bind drag and drop events
        setupDragAndDrop();

        // Info Panel - hidden until torrent creation completes
        infoPanel = new VBox(20);
        infoPanel.getStyleClass().add("glass-panel");
        infoPanel.setVisible(false);

        Label panelTitle = new Label("Torrent Sharing Metadata");
        panelTitle.setFont(Font.font("Outfit", FontWeight.BOLD, 18));
        panelTitle.setTextFill(Color.WHITE);

        GridPane grid = new GridPane();
        grid.setHgap(30);
        grid.setVgap(12);
        grid.setPadding(new Insets(10, 0, 10, 0));

        nameVal = createMetaLabel(grid, "Name:", 0);
        sizeVal = createMetaLabel(grid, "Total Size:", 1);
        hashVal = createMetaLabel(grid, "Info Hash:", 2);
        pieceVal = createMetaLabel(grid, "Piece Size:", 3);
        countVal = createMetaLabel(grid, "Number of Files:", 4);

        VBox magnetBox = new VBox(8);
        Label magnetTitle = new Label("Generated Magnet URI:");
        magnetTitle.setFont(Font.font("Inter", FontWeight.BOLD, 12));
        magnetTitle.setTextFill(Color.web("#9ca3af"));

        magnetText = new TextArea();
        magnetText.setEditable(false);
        magnetText.setWrapText(true);
        magnetText.setPrefRowCount(4);
        magnetText.setFont(Font.font("Consolas", 12));

        magnetBox.getChildren().addAll(magnetTitle, magnetText);

        // Control Buttons
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button copyBtn = new Button("Copy Magnet URI");
        copyBtn.getStyleClass().add("button-primary");
        copyBtn.setOnAction(e -> copyMagnetToClipboard());

        Button saveBtn = new Button("Export .torrent File");
        saveBtn.getStyleClass().add("button-secondary");
        saveBtn.setOnAction(e -> exportTorrentFile());

        Button qrBtn = new Button("Display QR Code");
        qrBtn.getStyleClass().add("button-secondary");
        qrBtn.setOnAction(e -> showQRCodeDialog());

        actions.getChildren().addAll(copyBtn, saveBtn, qrBtn);

        infoPanel.getChildren().addAll(panelTitle, grid, magnetBox, actions);
        root.getChildren().addAll(dragZone, infoPanel);
    }

    private Label createMetaLabel(GridPane grid, String title, int row) {
        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font("Inter", FontWeight.BOLD, 13));
        titleLbl.setTextFill(Color.web("#9ca3af"));
        grid.add(titleLbl, 0, row);

        Label valLbl = new Label("---");
        valLbl.setFont(Font.font("Inter", 13));
        valLbl.setTextFill(Color.WHITE);
        grid.add(valLbl, 1, row);
        return valLbl;
    }

    private void setupDragAndDrop() {
        dragZone.setOnDragOver(event -> {
            if (event.getGestureSource() != dragZone && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
                if (!dragZone.getStyleClass().contains("drag-zone-active")) {
                    dragZone.getStyleClass().add("drag-zone-active");
                }
            }
            event.consume();
        });

        dragZone.setOnDragExited(event -> {
            dragZone.getStyleClass().remove("drag-zone-active");
            event.consume();
        });

        dragZone.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                if (!files.isEmpty()) {
                    // Start async hashing of first dragged item or full folders
                    processFilesForTorrent(files.get(0));
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void processFilesForTorrent(File source) {
        ApplicationSettings settings = settingsService.getSettings();
        File torrentDataDir = new File("data/torrents");
        FileUtils.ensureDirectoryExists(torrentDataDir);

        // Spawn JavaFX task to keep UI completely interactive
        TorrentCreationTask task = new TorrentCreationTask(shareService, source, torrentDataDir);

        ProgressDialog progressDialog = new ProgressDialog(task);
        progressDialog.show();

        task.setOnSucceeded(event -> {
            progressDialog.close();
            generatedTorrentFile = task.getValue();
            log.info("Torrent creation finished. File: {}", generatedTorrentFile.getAbsolutePath());
            Platform.runLater(() -> populateTorrentMetadata(source));
        });

        task.setOnFailed(event -> {
            progressDialog.close();
            Throwable error = task.getException();
            log.error("Torrent creation background worker failed", error);
            showError("Torrent Generation Error", "Unable to scan and build torrent metadata: " + error.getMessage());
        });

        executor.submit(task);
    }

    private void populateTorrentMetadata(File source) {
        try {
            byte[] bytes = Files.readAllBytes(generatedTorrentFile.toPath());
            TorrentInfo ti = new TorrentInfo(bytes);

            nameVal.setText(ti.name());
            sizeVal.setText(FileUtils.formatSize(ti.totalSize()));
            hashVal.setText(ti.infoHashV1().toString());
            pieceVal.setText(FileUtils.formatSize(ti.pieceLength()));
            countVal.setText(String.valueOf(ti.numFiles()));

            // Generate full magnet link
            generatedMagnetLink = MagnetUtils.generateMagnet(
                ti.infoHashV1().toString(),
                ti.name(),
                settingsService.getSettings().listeningPort() == 0 ? List.of() : List.of(
                    "udp://tracker.opentrackr.org:1337/announce",
                    "udp://tracker.openbittorrent.com:6969/announce"
                )
            );
            magnetText.setText(generatedMagnetLink);
            infoPanel.setVisible(true);

        } catch (Exception e) {
            log.error("Error reading newly created torrent file metadata", e);
            showError("Metadata Parse Failure", "Failed to retrieve compiled torrent statistics: " + e.getMessage());
        }
    }

    private void copyMagnetToClipboard() {
        if (generatedMagnetLink == null) return;
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(generatedMagnetLink);
        clipboard.setContent(content);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Clipboard Copy");
        alert.setHeaderText(null);
        alert.setContentText("Magnet Link copied to clipboard successfully!");
        alert.showAndWait();
    }

    private void exportTorrentFile() {
        if (generatedTorrentFile == null || !generatedTorrentFile.exists()) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Export Torrent File");
        fc.setInitialFileName(generatedTorrentFile.getName());
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("BitTorrent Files", "*.torrent"));

        File saveDest = fc.showSaveDialog(root.getScene().getWindow());
        if (saveDest != null) {
            try {
                Files.copy(generatedTorrentFile.toPath(), saveDest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                log.info("Exported .torrent file copy saved to: {}", saveDest.getAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to copy exported torrent file", e);
                showError("Export Error", "Failed to copy .torrent file to destination: " + e.getMessage());
            }
        }
    }

    private void showQRCodeDialog() {
        if (generatedMagnetLink == null) return;
        
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(generatedMagnetLink, BarcodeFormat.QR_CODE, 320, 320);
            
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] qrBytes = pngOutputStream.toByteArray();

            Image qrImage = new Image(new ByteArrayInputStream(qrBytes));
            ImageView imageView = new ImageView(qrImage);

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(root.getScene().getWindow());
            dialog.setTitle("Magnet Link QR Code");

            VBox container = new VBox(15);
            container.setStyle("-fx-background-color: #0f0f13; -fx-padding: 24px; -fx-alignment: center;");

            Label caption = new Label("Scan to Download via TorrentShare Client");
            caption.setFont(Font.font("Outfit", FontWeight.BOLD, 14));
            caption.setTextFill(Color.WHITE);

            Button savePngBtn = new Button("Save as PNG");
            savePngBtn.getStyleClass().add("button-primary");
            savePngBtn.setOnAction(event -> {
                FileChooser fc = new FileChooser();
                fc.setTitle("Save QR Code Image");
                fc.setInitialFileName("torrent_qr.png");
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Images", "*.png"));
                File qrDest = fc.showSaveDialog(dialog);
                if (qrDest != null) {
                    try {
                        Files.write(qrDest.toPath(), qrBytes);
                    } catch (Exception ex) {
                        log.error("Failed to save QR Code png", ex);
                    }
                }
            });

            container.getChildren().addAll(caption, imageView, savePngBtn);

            Scene scene = new Scene(container);
            dialog.setScene(scene);
            dialog.showAndWait();

        } catch (Exception e) {
            log.error("Failed to render QR Code modal", e);
            showError("QR Code Render Failure", "Failed to compile magnet link matrix into QR Code: " + e.getMessage());
        }
    }

    private void showError(String title, String details) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(details);
        alert.showAndWait();
    }

    /**
     * Reusable visual loading/progress Dialog while hashing directories.
     */
    private static class ProgressDialog extends Stage {
        public ProgressDialog(TorrentCreationTask task) {
            initModality(Modality.APPLICATION_MODAL);
            setTitle("Hashing Files");
            
            VBox box = new VBox(15);
            box.setPadding(new Insets(24));
            box.setAlignment(Pos.CENTER);
            box.setStyle("-fx-background-color: #121214; -fx-pref-width: 400px;");

            Label title = new Label("Hashing and Creating Torrent...");
            title.setFont(Font.font("Outfit", FontWeight.BOLD, 16));
            title.setTextFill(Color.WHITE);

            ProgressBar pb = new ProgressBar();
            pb.progressProperty().bind(task.progressProperty());
            pb.setPrefWidth(350);

            Label message = new Label();
            message.textProperty().bind(task.messageProperty());
            message.setTextFill(Color.web("#9ca3af"));
            message.setFont(Font.font("Inter", 12));

            box.getChildren().addAll(title, pb, message);
            setScene(new Scene(box));
        }
    }
}
