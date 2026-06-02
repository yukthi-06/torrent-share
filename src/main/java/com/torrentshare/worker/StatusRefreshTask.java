package com.torrentshare.worker;

import com.torrentshare.model.TorrentStatus;
import com.torrentshare.torrent.TorrentMonitor;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Periodically polls active transfer speeds and progress metrics, posting updates to JavaFX.
 */
public class StatusRefreshTask {
    private static final Logger log = LoggerFactory.getLogger(StatusRefreshTask.class);

    private final TorrentMonitor monitor;
    private final Consumer<List<TorrentStatus>> callback;
    private ScheduledExecutorService scheduler;

    public StatusRefreshTask(TorrentMonitor monitor, Consumer<List<TorrentStatus>> callback) {
        this.monitor = monitor;
        this.callback = callback;
    }

    /**
     * Spawns a scheduled background daemon thread execution loop at 1-second intervals.
     */
    public synchronized void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "torrent-status-refresh-worker");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleAtFixedRate(this::pollAndDispatch, 0, 1, TimeUnit.SECONDS);
        log.info("Status monitoring refresh thread scheduler started.");
    }

    /**
     * Shuts down the scheduled polling threads loop.
     */
    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
            log.info("Status monitoring refresh thread scheduler stopped.");
        }
    }

    private void pollAndDispatch() {
        try {
            List<TorrentStatus> statuses = monitor.getActiveStatuses();
            // Dispatch back onto JavaFX UI thread safely
            Platform.runLater(() -> callback.accept(statuses));
        } catch (Exception e) {
            log.error("Fatal error during status poll cycle", e);
        }
    }
}
