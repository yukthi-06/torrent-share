package com.torrentshare.persistence;

import com.torrentshare.model.TorrentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles database operations for active and completed torrent entities.
 */
public class TorrentRepository {
    private static final Logger log = LoggerFactory.getLogger(TorrentRepository.class);
    private final DatabaseManager databaseManager;

    public TorrentRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void save(TorrentInfo torrent) {
        String sql = """
            INSERT OR REPLACE INTO torrents (info_hash, name, magnet_uri, save_path, added_date, status, resume_file)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, torrent.infoHash());
            pstmt.setString(2, torrent.name());
            pstmt.setString(3, torrent.magnetUri());
            pstmt.setString(4, torrent.savePath());
            pstmt.setLong(5, torrent.addedDate());
            pstmt.setString(6, torrent.status());
            pstmt.setString(7, torrent.resumeFile());
            pstmt.executeUpdate();
            log.debug("Saved torrent {} to SQLite database", torrent.infoHash());
        } catch (SQLException e) {
            log.error("Failed to save torrent to database", e);
        }
    }

    public void delete(String infoHash) {
        String sql = "DELETE FROM torrents WHERE info_hash = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, infoHash);
            pstmt.executeUpdate();
            log.debug("Deleted torrent {} from database", infoHash);
        } catch (SQLException e) {
            log.error("Failed to delete torrent from database", e);
        }
    }

    public TorrentInfo findByHash(String infoHash) {
        String sql = "SELECT * FROM torrents WHERE info_hash = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, infoHash);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query torrent by hash from database", e);
        }
        return null;
    }

    public List<TorrentInfo> findAll() {
        List<TorrentInfo> list = new ArrayList<>();
        String sql = "SELECT * FROM torrents ORDER BY added_date DESC";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to retrieve all torrents from database", e);
        }
        return list;
    }

    public void updateStatus(String infoHash, String status) {
        String sql = "UPDATE torrents SET status = ? WHERE info_hash = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, infoHash);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update status for torrent {}", infoHash, e);
        }
    }

    public void updateResumeFile(String infoHash, String resumeFile) {
        String sql = "UPDATE torrents SET resume_file = ? WHERE info_hash = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, resumeFile);
            pstmt.setString(2, infoHash);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update resume file for torrent {}", infoHash, e);
        }
    }

    private TorrentInfo mapRow(ResultSet rs) throws SQLException {
        return new TorrentInfo(
            rs.getString("info_hash"),
            rs.getString("name"),
            rs.getString("magnet_uri"),
            rs.getString("save_path"),
            rs.getLong("added_date"),
            rs.getString("status"),
            rs.getString("resume_file")
        );
    }
}
