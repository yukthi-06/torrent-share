package com.vypeensoft.torrentshare.persistence;

import com.vypeensoft.torrentshare.util.MagnetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Manages the SQLite database lifetime, initialization, and connection pooling.
 */
public class DatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DB_DIR = "data";
    private static final String DB_FILE = DB_DIR + "/torrentshare.db";
    private static final String CONNECTION_URL = "jdbc:sqlite:" + DB_FILE;

    private static DatabaseManager instance;

    private DatabaseManager() {
        initializeDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Obtains a new connection to the SQLite database.
     * The caller is responsible for closing this connection.
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(CONNECTION_URL);
    }

    private void initializeDatabase() {
        File dir = new File(DB_DIR);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                log.info("Created database directory: {}", DB_DIR);
            }
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Create settings table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT
                )
            """);

            // Create trackers table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS trackers (
                    url TEXT PRIMARY KEY,
                    is_custom INTEGER DEFAULT 0
                )
            """);

            // Create torrents table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS torrents (
                    info_hash TEXT PRIMARY KEY,
                    name TEXT,
                    magnet_uri TEXT,
                    save_path TEXT,
                    added_date INTEGER,
                    status TEXT,
                    resume_file TEXT
                )
            """);

            // Create download history table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS download_history (
                    info_hash TEXT PRIMARY KEY,
                    name TEXT,
                    size INTEGER,
                    completed_date INTEGER
                )
            """);

            log.info("Database tables initialized successfully.");

            // Seed default trackers if empty
            seedDefaultTrackers(conn);

        } catch (SQLException e) {
            log.error("Fatal error during database schema initialization", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private void seedDefaultTrackers(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            log.info("Syncing default trackers with SQLite database...");
            List<String> defaults = MagnetUtils.loadDefaultTrackers();
            
            // Delete any existing non-custom default trackers that are no longer in the defaults list
            if (defaults != null && !defaults.isEmpty()) {
                StringBuilder sb = new StringBuilder("DELETE FROM trackers WHERE is_custom = 0 AND url NOT IN (");
                for (int i = 0; i < defaults.size(); i++) {
                    if (i > 0) {
                        sb.append(",");
                    }
                    sb.append("'").append(defaults.get(i).replace("'", "''")).append("'");
                }
                sb.append(")");
                stmt.execute(sb.toString());
            } else {
                stmt.execute("DELETE FROM trackers WHERE is_custom = 0");
            }
            
            // Insert or ignore all current default trackers
            if (defaults != null) {
                for (String url : defaults) {
                    String escapedUrl = url.replace("'", "''");
                    stmt.execute("INSERT OR IGNORE INTO trackers (url, is_custom) VALUES ('" + escapedUrl + "', 0)");
                }
            }
        }
    }
}
