package com.torrentshare.service;

import com.frostwire.jlibtorrent.TorrentInfo;
import com.torrentshare.torrent.TorrentCreator;
import com.torrentshare.torrent.TorrentManager;
import com.torrentshare.torrent.TrackerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Coordination service handling file scanning, torrent file output, and immediate seeding.
 */
public class ShareService {
    private static final Logger log = LoggerFactory.getLogger(ShareService.class);
    
    private final TorrentManager torrentManager;
    private final TrackerManager trackerManager;

    public ShareService(TorrentManager torrentManager, TrackerManager trackerManager) {
        this.torrentManager = torrentManager;
        this.trackerManager = trackerManager;
    }

    /**
     * Creates a local bencoded .torrent file from a source path.
     * Note: Hashing is done on this thread, should be run inside a Worker task.
     */
    public File buildTorrentFile(File sourcePath, File outputDirectory) throws IOException {
        List<String> trackers = trackerManager.getTrackers();
        byte[] torrentBytes = TorrentCreator.createTorrent(sourcePath, trackers);

        TorrentInfo ti = new TorrentInfo(torrentBytes);
        String hexHash = ti.infoHashV1().toString();

        File torrentFile = new File(outputDirectory, hexHash.toLowerCase() + ".torrent");
        Files.write(torrentFile.toPath(), torrentBytes);
        log.info("Bencoded torrent file saved: {}", torrentFile.getAbsolutePath());

        return torrentFile;
    }

    /**
     * Delegates to the TorrentManager to register the .torrent and begin seeding it.
     */
    public void seedTorrent(File torrentFile, File sourcePath) throws IOException {
        torrentManager.startSeeding(torrentFile, sourcePath);
    }
}
