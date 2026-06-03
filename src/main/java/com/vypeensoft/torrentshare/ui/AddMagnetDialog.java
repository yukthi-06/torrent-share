package com.vypeensoft.torrentshare.ui;

import com.vypeensoft.torrentshare.service.DownloadService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Prompt dialog validating magnet link entries and save path selections.
 */
public class AddMagnetDialog extends Stage {
    private static final Logger log = LoggerFactory.getLogger(AddMagnetDialog.class);

    private final DownloadService downloadService;
    private final String defaultDownloadPath;
    private final Runnable onSuccessCallback;

    private TextField magnetField;
    private TextField pathField;
    private Label errorLabel;

    public AddMagnetDialog(Stage owner, DownloadService downloadService,
                           String defaultDownloadPath, Runnable onSuccessCallback) {
        this.downloadService = downloadService;
        this.defaultDownloadPath = defaultDownloadPath;
        this.onSuccessCallback = onSuccessCallback;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Add Magnet Link");

        buildUI();
    }

    private void buildUI() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-pref-width: 550px;");

        Label headerTitle = new Label("Add New Magnet Download");
        headerTitle.setFont(Font.font("Outfit", FontWeight.BOLD, 18));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setAlignment(Pos.CENTER_LEFT);

        Label magnetLabel = new Label("Magnet Link URI:");
        magnetLabel.setFont(Font.font("Inter", FontWeight.BOLD, 12));
        grid.add(magnetLabel, 0, 0);

        magnetField = new TextField();
        magnetField.setPromptText("magnet:?xt=urn:btih:...");
        magnetField.setPrefWidth(350);
        grid.add(magnetField, 1, 0);

        Label pathLabel = new Label("Save Directory:");
        pathLabel.setFont(Font.font("Inter", FontWeight.BOLD, 12));
        grid.add(pathLabel, 0, 1);

        HBox pathBox = new HBox(8);
        pathField = new TextField(defaultDownloadPath);
        pathField.setPrefWidth(270);
        
        Button browseBtn = new Button("Browse");
        browseBtn.getStyleClass().add("button-secondary");
        browseBtn.setOnAction(e -> selectDirectory());

        pathBox.getChildren().addAll(pathField, browseBtn);
        grid.add(pathBox, 1, 1);

        errorLabel = new Label();
        errorLabel.setTextFill(Color.web("#f87171"));
        errorLabel.setFont(Font.font("Inter", 12));
        errorLabel.setWrapText(true);

        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("button-secondary");
        cancelBtn.setOnAction(e -> close());

        Button startBtn = new Button("Start Download");
        startBtn.getStyleClass().add("button-primary");
        startBtn.setOnAction(e -> handleAdd());

        buttons.getChildren().addAll(cancelBtn, startBtn);

        root.getChildren().addAll(headerTitle, grid, errorLabel, buttons);

        Scene scene = new Scene(root);
        setScene(scene);
    }

    private void selectDirectory() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Download Directory");
        
        File initial = new File(pathField.getText());
        if (initial.exists() && initial.isDirectory()) {
            dc.setInitialDirectory(initial);
        }

        File chosen = dc.showDialog(this);
        if (chosen != null) {
            pathField.setText(chosen.getAbsolutePath());
        }
    }

    private void handleAdd() {
        String magnet = magnetField.getText().trim();
        String savePath = pathField.getText().trim();

        if (magnet.isEmpty()) {
            errorLabel.setText("Magnet URI cannot be blank.");
            return;
        }

        if (!downloadService.validateMagnet(magnet)) {
            errorLabel.setText("Invalid magnet link format. Must include xt=urn:btih:.");
            return;
        }

        if (savePath.isEmpty()) {
            errorLabel.setText("Please select a valid download directory.");
            return;
        }

        File dir = new File(savePath);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                errorLabel.setText("Failed to create download directory: " + savePath);
                return;
            }
            log.info("Created missing download directory: {}", savePath);
        } else if (!dir.isDirectory()) {
            errorLabel.setText("Specified path exists but is not a directory.");
            return;
        }

        try {
            downloadService.downloadMagnet(magnet, dir);
            onSuccessCallback.run();
            close();
        } catch (Exception e) {
            log.error("Failed to start magnet download", e);
            errorLabel.setText("Failed to register download task: " + e.getMessage());
        }
    }
}
