package com.torrentshare;

import com.torrentshare.persistence.DatabaseManager;
import com.torrentshare.persistence.SettingsRepository;
import com.torrentshare.persistence.TorrentRepository;
import com.torrentshare.service.DownloadService;
import com.torrentshare.service.ShareService;
import com.torrentshare.service.SettingsService;
import com.torrentshare.torrent.ResumeManager;
import com.torrentshare.torrent.SessionManager;
import com.torrentshare.torrent.TorrentManager;
import com.torrentshare.torrent.TorrentMonitor;
import com.torrentshare.torrent.TrackerManager;
import com.torrentshare.ui.MainWindow;
import com.torrentshare.util.LoggingUtils;
import com.torrentshare.worker.StatusRefreshTask;
import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standard Application entry point for TorrentShare BitTorrent Native Desktop App.
 */
public class Main extends Application {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private SessionManager sessionManager;
    private TorrentManager torrentManager;
    private StatusRefreshTask statusRefreshTask;
    private MainWindow mainWindow;

    @Override
    public void start(Stage primaryStage) {
        try {
            LoggingUtils.logStartup();

            // 1. Database and Persistences setup
            DatabaseManager dbManager = DatabaseManager.getInstance();
            TorrentRepository torrentRepo = new TorrentRepository(dbManager);
            SettingsRepository settingsRepo = new SettingsRepository(dbManager);

            // 2. Managers setup
            sessionManager = new SessionManager();
            ResumeManager resumeManager = new ResumeManager();
            TrackerManager trackerManager = new TrackerManager(settingsRepo);
            torrentManager = new TorrentManager(sessionManager, torrentRepo, resumeManager, trackerManager);
            TorrentMonitor torrentMonitor = new TorrentMonitor(sessionManager);

            // 3. Application Services setup
            ShareService shareService = new ShareService(torrentManager, trackerManager);
            DownloadService downloadService = new DownloadService(torrentManager);
            SettingsService settingsService = new SettingsService(sessionManager, settingsRepo);

            // 4. Bootstrap jlibtorrent session from persisted configurations
            int port = settingsService.getSettings().listeningPort();
            sessionManager.start(port);
            settingsService.bootstrapSettings();

            // 5. Restore active downloads from SQLite database
            torrentManager.restoreSavedSession();

            // 6. Setup status polling background worker
            mainWindow = new MainWindow(
                shareService,
                downloadService,
                settingsService,
                trackerManager,
                torrentMonitor
            );

            // 7. Show main window GUI
            mainWindow.start(primaryStage);

        } catch (Exception e) {
            log.error("Fatal error during application startup sequence", e);
            System.exit(1);
        }
    }

    @Override
    public void stop() {
        log.info("Received shut down directive. Exiting TorrentShare application cleanly...");
        try {
            if (mainWindow != null) {
                mainWindow.shutdownMonitor();
            }
            if (torrentManager != null) {
                // Flushes resume files and updates database
                torrentManager.prepareShutdown();
            }
            if (sessionManager != null) {
                sessionManager.stop();
            }
            log.info("Shutdown completed successfully. Goodbye!");
        } catch (Exception e) {
            log.error("Error during clean shutdown sequence", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
