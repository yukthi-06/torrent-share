package com.vypeensoft.torrentshare.torrent;

import com.vypeensoft.torrentshare.persistence.SettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Manages default and custom user tracker lists.
 */
public class TrackerManager {
    private static final Logger log = LoggerFactory.getLogger(TrackerManager.class);
    private final SettingsRepository settingsRepository;

    public TrackerManager(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    /**
     * Gets the full list of active tracker URLs.
     */
    public List<String> getTrackers() {
        return settingsRepository.getTrackers();
    }

    /**
     * Registers a custom tracker.
     */
    public void addTracker(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        settingsRepository.addTracker(url.trim());
    }

    /**
     * Removes an active tracker.
     */
    public void removeTracker(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        settingsRepository.deleteTracker(url.trim());
    }
}
