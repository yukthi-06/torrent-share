package com.vypeensoft.torrentshare.torrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Handles physical storage of jlibtorrent fastresume binary blobs.
 */
public class ResumeManager {
    private static final Logger log = LoggerFactory.getLogger(ResumeManager.class);
    private static final String RESUME_DIR = "data/resume";

    public ResumeManager() {
        File dir = new File(RESUME_DIR);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                log.info("Created resume directory: {}", RESUME_DIR);
            }
        }
    }

    /**
     * Persists binary fastresume data to a local file.
     */
    public synchronized void saveResumeData(String infoHash, byte[] data) {
        if (infoHash == null || data == null) {
            return;
        }
        File file = getResumeFile(infoHash);
        try {
            Files.write(file.toPath(), data);
            log.debug("Successfully saved fastresume data for: {}", infoHash);
        } catch (IOException e) {
            log.error("Failed to persist fastresume data for: {}", infoHash, e);
        }
    }

    /**
     * Reads fastresume data from a local file. Returns null if not found.
     */
    public synchronized byte[] loadResumeData(String infoHash) {
        if (infoHash == null) {
            return null;
        }
        File file = getResumeFile(infoHash);
        if (!file.exists()) {
            return null;
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            log.error("Failed to load fastresume bytes for: {}", infoHash, e);
            return null;
        }
    }

    /**
     * Removes fastresume data file from disk.
     */
    public synchronized void deleteResumeData(String infoHash) {
        if (infoHash == null) {
            return;
        }
        File file = getResumeFile(infoHash);
        if (file.exists()) {
            if (file.delete()) {
                log.info("Deleted fastresume file for: {}", infoHash);
            } else {
                log.warn("Failed to delete fastresume file for: {}", infoHash);
            }
        }
    }

    private File getResumeFile(String infoHash) {
        return new File(RESUME_DIR, infoHash.toLowerCase() + ".fastresume");
    }
}
