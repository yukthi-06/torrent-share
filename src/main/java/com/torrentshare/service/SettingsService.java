package com.torrentshare.service;

import com.torrentshare.model.ApplicationSettings;
import com.torrentshare.persistence.SettingsRepository;
import com.torrentshare.torrent.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages runtime updates of connection settings and folder locations in persistence.
 */
public class SettingsService {
    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final SessionManager sessionManager;
    private final SettingsRepository settingsRepository;

    public SettingsService(SessionManager sessionManager, SettingsRepository settingsRepository) {
        this.sessionManager = sessionManager;
        this.settingsRepository = settingsRepository;
    }

    /**
     * Loads current application settings from the database.
     */
    public ApplicationSettings getSettings() {
        return settingsRepository.getSettings();
    }

    /**
     * Dynamically updates limits in jlibtorrent and saves options to database.
     */
    public void updateSettings(ApplicationSettings settings) {
        settingsRepository.saveSettings(settings);
        applyLimits(settings);
        log.info("Successfully updated and applied global settings.");
    }

    /**
     * Bootstraps settings by mapping database parameters into the active session.
     */
    public void bootstrapSettings() {
        ApplicationSettings settings = getSettings();
        applyLimits(settings);
    }

    private void applyLimits(ApplicationSettings settings) {
        // Cast limits safely from long (database) to int (jlibtorrent threshold inputs)
        sessionManager.setUploadRateLimit((int) settings.uploadLimit());
        sessionManager.setDownloadRateLimit((int) settings.downloadLimit());
        sessionManager.setMaxConnections(settings.maxConnections());
    }
}
