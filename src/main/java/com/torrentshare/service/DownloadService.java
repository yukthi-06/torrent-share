package com.torrentshare.service;

import com.torrentshare.torrent.TorrentManager;
import com.torrentshare.util.MagnetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Coordination service managing magnet link validations and active download lifecycles.
 */
public class DownloadService {
    private static final Logger log = LoggerFactory.getLogger(DownloadService.class);
    private final TorrentManager torrentManager;

    public DownloadService(TorrentManager torrentManager) {
        this.torrentManager = torrentManager;
    }

    /**
     * Syntactic sanity check for Magnet URIs.
     */
    public boolean validateMagnet(String magnetUri) {
        return MagnetUtils.isValidMagnet(magnetUri);
    }

    /**
     * Requests the TorrentManager to register the magnet link and begin metadata recovery.
     */
    public void downloadMagnet(String magnetUri, File downloadDirectory) {
        torrentManager.startDownloading(magnetUri, downloadDirectory);
    }

    /**
     * Pauses the active torrent download.
     */
    public void pause(String infoHash) {
        torrentManager.pauseTorrent(infoHash);
    }

    /**
     * Resumes the paused torrent download.
     */
    public void resume(String infoHash) {
        torrentManager.resumeTorrent(infoHash);
    }

    /**
     * Unregisters the torrent from the active session and deletes trackers and database tracking.
     */
    public void remove(String infoHash, boolean deleteFiles) {
        torrentManager.removeTorrent(infoHash, deleteFiles);
    }
}
