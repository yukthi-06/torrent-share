package com.torrentshare.persistence;

import com.torrentshare.model.ApplicationSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists and fetches global application limits, paths, and tracker lists.
 */
public class SettingsRepository {
    private static final Logger log = LoggerFactory.getLogger(SettingsRepository.class);
    private final DatabaseManager databaseManager;

    public SettingsRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public String get(String key, String defaultValue) {
        String sql = "SELECT value FROM settings WHERE key = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query settings key {}", key, e);
        }
        return defaultValue;
    }

    public void set(String key, String value) {
        String sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save settings key {}", key, e);
        }
    }

    public ApplicationSettings getSettings() {
        // Enforce sensible defaults in the current folder
        String defaultDownload = new File("downloads").getAbsolutePath();
        String defaultShare = new File("shares").getAbsolutePath();

        String dlDir = get("downloadDir", defaultDownload);
        String shDir = get("shareDir", defaultShare);
        int port = Integer.parseInt(get("listeningPort", "6881"));
        long ulLimit = Long.parseLong(get("uploadLimit", "0"));
        long dlLimit = Long.parseLong(get("downloadLimit", "0"));
        int connections = Integer.parseInt(get("maxConnections", "200"));
        int torrents = Integer.parseInt(get("maxActiveTorrents", "20"));

        return new ApplicationSettings(dlDir, shDir, port, ulLimit, dlLimit, connections, torrents);
    }

    public void saveSettings(ApplicationSettings settings) {
        set("downloadDir", settings.downloadDir());
        set("shareDir", settings.shareDir());
        set("listeningPort", String.valueOf(settings.listeningPort()));
        set("uploadLimit", String.valueOf(settings.uploadLimit()));
        set("downloadLimit", String.valueOf(settings.downloadLimit()));
        set("maxConnections", String.valueOf(settings.maxConnections()));
        set("maxActiveTorrents", String.valueOf(settings.maxActiveTorrents()));
        log.info("Saved settings to database: {}", settings);
    }

    public List<String> getTrackers() {
        List<String> trackers = new ArrayList<>();
        String sql = "SELECT url FROM trackers";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                trackers.add(rs.getString("url"));
            }
        } catch (SQLException e) {
            log.error("Failed to fetch trackers list", e);
        }
        return trackers;
    }

    public void addTracker(String url) {
        String sql = "INSERT OR IGNORE INTO trackers (url, is_custom) VALUES (?, 1)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, url);
            pstmt.executeUpdate();
            log.info("Added tracker: {}", url);
        } catch (SQLException e) {
            log.error("Failed to add tracker {}", url, e);
        }
    }

    public void deleteTracker(String url) {
        String sql = "DELETE FROM trackers WHERE url = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, url);
            pstmt.executeUpdate();
            log.info("Deleted tracker: {}", url);
        } catch (SQLException e) {
            log.error("Failed to delete tracker {}", url, e);
        }
    }
}
