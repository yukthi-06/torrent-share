package com.torrentshare.worker;

import com.torrentshare.service.ShareService;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Background worker task that executes heavy file scanning and pieces hashing.
 */
public class TorrentCreationTask extends Task<File> {
    private static final Logger log = LoggerFactory.getLogger(TorrentCreationTask.class);

    private final ShareService shareService;
    private final File sourceFile;
    private final File targetDir;

    public TorrentCreationTask(ShareService shareService, File sourceFile, File targetDir) {
        this.shareService = shareService;
        this.sourceFile = sourceFile;
        this.targetDir = targetDir;
        
        updateTitle("Torrent Creation Task");
        updateMessage("Initializing...");
        updateProgress(0, 100);
    }

    @Override
    protected File call() throws Exception {
        log.info("Executing background hashing task for source: {}", sourceFile.getAbsolutePath());
        updateMessage("Scanning files and calculating SHA-1 hashes...");
        updateProgress(-1, 100); // Indeterminate progress during deep native calculations

        File torrentFile = shareService.buildTorrentFile(sourceFile, targetDir);

        updateMessage("Seeding registration...");
        updateProgress(90, 100);

        shareService.seedTorrent(torrentFile, sourceFile);

        updateMessage("Torrent created and seeding started successfully!");
        updateProgress(100, 100);
        return torrentFile;
    }
}
