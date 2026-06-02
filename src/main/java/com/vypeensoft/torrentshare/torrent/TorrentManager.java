package com.vypeensoft.torrentshare.torrent;

import com.frostwire.jlibtorrent.AddTorrentParams;
import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.SaveResumeDataAlert;
import com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert;
import com.vypeensoft.torrentshare.persistence.TorrentRepository;
import com.vypeensoft.torrentshare.util.MagnetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates high-level torrent operations, session loading, and fastresume callbacks.
 */
public class TorrentManager {
    private static final Logger log = LoggerFactory.getLogger(TorrentManager.class);

    private final SessionManager sessionManager;
    private final TorrentRepository torrentRepository;
    private final ResumeManager resumeManager;
    private final TrackerManager trackerManager;

    public TorrentManager(SessionManager sessionManager, TorrentRepository torrentRepository,
                          ResumeManager resumeManager, TrackerManager trackerManager) {
        this.sessionManager = sessionManager;
        this.torrentRepository = torrentRepository;
        this.resumeManager = resumeManager;
        this.trackerManager = trackerManager;

        // Register Alert Listener for handling asynchronous resume alerts
        setupAlertListener();
    }

    /**
     * Bootstraps the session by reading database records and restoring resume files.
     */
    public void restoreSavedSession() {
        log.info("Restoring previous download sessions from SQLite db...");
        List<com.vypeensoft.torrentshare.model.TorrentInfo> torrents = torrentRepository.findAll();

        for (com.vypeensoft.torrentshare.model.TorrentInfo entry : torrents) {
            try {
                restoreTorrent(entry);
            } catch (Exception e) {
                log.error("Failed to restore torrent: {}", entry.infoHash(), e);
            }
        }
        log.info("Finished restoring sessions.");
    }

    private void restoreTorrent(com.vypeensoft.torrentshare.model.TorrentInfo entry) {
        log.info("Attempting to restore: {} (Hash: {})", entry.name(), entry.infoHash());

        byte[] resumeBytes = resumeManager.loadResumeData(entry.infoHash());
        if (resumeBytes != null) {
            try {
                // Restore via fastresume data
                com.frostwire.jlibtorrent.swig.error_code ec = new com.frostwire.jlibtorrent.swig.error_code();
                com.frostwire.jlibtorrent.swig.byte_vector buffer = com.frostwire.jlibtorrent.Vectors.bytes2byte_vector(resumeBytes);
                com.frostwire.jlibtorrent.swig.add_torrent_params nativeParams = com.frostwire.jlibtorrent.swig.add_torrent_params.read_resume_data(buffer, ec);
                if (ec.value() != 0) {
                    throw new IllegalArgumentException("Corrupt resume data: " + ec.message());
                }
                AddTorrentParams atp = new AddTorrentParams(nativeParams);
                atp.savePath(entry.savePath());
                
                sessionManager.getJlibtorrentSession().swig().add_torrent(atp.swig(), ec);
                log.info("Restored torrent {} successfully using fastresume.", entry.name());
                return;
            } catch (Exception e) {
                log.warn("Fastresume file for {} was corrupt or outdated, falling back to full check", entry.name(), e);
            }
        }

        // Fallback: If no resume data is available, check if we have the local .torrent file
        File torrentFile = new File("data/torrents/" + entry.infoHash().toLowerCase() + ".torrent");
        if (torrentFile.exists()) {
            try {
                byte[] torrentBytes = Files.readAllBytes(torrentFile.toPath());
                com.frostwire.jlibtorrent.TorrentInfo ti = new com.frostwire.jlibtorrent.TorrentInfo(torrentBytes);
                sessionManager.getJlibtorrentSession().download(ti, new File(entry.savePath()));
                log.info("Restored torrent {} from local .torrent file backup.", entry.name());
                return;
            } catch (IOException e) {
                log.error("Failed to read backup torrent file for {}", entry.name(), e);
            }
        }

        // Last resort fallback: Restore via magnet link if available
        if (entry.magnetUri() != null && !entry.magnetUri().isBlank()) {
            AddTorrentParams atp = AddTorrentParams.parseMagnetUri(magnetWithAllTrackers(entry.magnetUri()));
            atp.savePath(entry.savePath());
            com.frostwire.jlibtorrent.swig.error_code ec = new com.frostwire.jlibtorrent.swig.error_code();
            sessionManager.getJlibtorrentSession().swig().add_torrent(atp.swig(), ec);
            log.info("Restored torrent {} from Magnet link fallback.", entry.name());
        }
    }

    /**
     * Seeds a locally created torrent file and registers it in database.
     */
    public void startSeeding(File torrentFile, File sourcePath) throws IOException {
        byte[] torrentBytes = Files.readAllBytes(torrentFile.toPath());
        com.frostwire.jlibtorrent.TorrentInfo ti = new com.frostwire.jlibtorrent.TorrentInfo(torrentBytes);
        String infoHashStr = ti.infoHashV1().toString();

        log.info("Registering newly created torrent for seeding: {} (Hash: {})", ti.name(), infoHashStr);

        // Copy .torrent to backup dir
        File backupDir = new File("data/torrents");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        File backupFile = new File(backupDir, infoHashStr.toLowerCase() + ".torrent");
        Files.write(backupFile.toPath(), torrentBytes);

        // Build Magnet URI with direct peer address hint (x.pe=) so the receiver can connect
        // to this machine immediately without needing tracker or DHT peer discovery.
        String localIp = MagnetUtils.getLocalNetworkIP();
        int sessionPort = sessionManager.getJlibtorrentSession().swig().listen_port();
        List<String> peerHints = new ArrayList<>();
        if (localIp != null && sessionPort > 0) {
            peerHints.add(localIp + ":" + sessionPort);
            log.info("Embedding peer hint in magnet: {}:{}", localIp, sessionPort);
        }
        String magnet = MagnetUtils.generateMagnet(infoHashStr, ti.name(), trackerManager.getTrackers(), peerHints);

        // The save path for libtorrent is the directory that CONTAINS the torrent's root
        // (i.e. parent of a dropped file, or parent of a dropped folder).
        File savePath = sourcePath.getParentFile();

        // Use download() to register the torrent — libtorrent will quickly verify the already-complete
        // pieces and then transition to SEEDING state. More reliable than SEED_MODE flag manipulation.
        sessionManager.getJlibtorrentSession().download(ti, savePath);
        log.info("Torrent '{}' added to session at: {}", ti.name(), savePath.getAbsolutePath());

        // Force an IMMEDIATE announce to all trackers.
        // Without this, the first tracker announcement can be delayed 30+ minutes (tracker-defined interval).
        // Behind CG-NAT the receiver can only find us via trackers or DHT, so announcing now is critical.
        TorrentHandle th = sessionManager.getJlibtorrentSession().find(new Sha1Hash(infoHashStr));
        if (th != null && th.isValid()) {
            th.forceReannounce();
            log.info("Forced immediate tracker re-announce for: {}", ti.name());
        }

        // Persist torrent metadata
        com.vypeensoft.torrentshare.model.TorrentInfo info = new com.vypeensoft.torrentshare.model.TorrentInfo(
            infoHashStr,
            ti.name(),
            magnet,
            savePath.getAbsolutePath(),
            System.currentTimeMillis(),
            "Seeding",
            backupFile.getAbsolutePath()
        );
        torrentRepository.save(info);
    }

    /**
     * Starts downloading a magnet link.
     */
    public void startDownloading(String magnetUri, File savePath) {
        String infoHashStr = MagnetUtils.extractHash(magnetUri);
        if (infoHashStr == null) {
            log.error("Invalid magnet link, cannot start download: {}", magnetUri);
            return;
        }

        String completeMagnet = magnetWithAllTrackers(magnetUri);
        log.info("Adding download task for magnet: {} to path: {}", completeMagnet, savePath.getAbsolutePath());

        // Add to jlibtorrent session
        AddTorrentParams atp = AddTorrentParams.parseMagnetUri(completeMagnet);
        atp.savePath(savePath.getAbsolutePath());
        com.frostwire.jlibtorrent.swig.error_code ec = new com.frostwire.jlibtorrent.swig.error_code();
        sessionManager.getJlibtorrentSession().swig().add_torrent(atp.swig(), ec);

        // Persist initial state
        com.vypeensoft.torrentshare.model.TorrentInfo info = new com.vypeensoft.torrentshare.model.TorrentInfo(
            infoHashStr,
            "Downloading Metadata...",
            completeMagnet,
            savePath.getAbsolutePath(),
            System.currentTimeMillis(),
            "Downloading",
            ""
        );
        torrentRepository.save(info);
    }

    public void pauseTorrent(String infoHash) {
        TorrentHandle th = sessionManager.getJlibtorrentSession().find(new Sha1Hash(infoHash));
        if (th != null && th.isValid()) {
            th.pause();
            torrentRepository.updateStatus(infoHash, "Paused");
            // Request fastresume capture
            th.saveResumeData();
            log.info("Paused torrent: {}", infoHash);
        }
    }

    public void resumeTorrent(String infoHash) {
        TorrentHandle th = sessionManager.getJlibtorrentSession().find(new Sha1Hash(infoHash));
        if (th != null && th.isValid()) {
            th.resume();
            torrentRepository.updateStatus(infoHash, "Downloading");
            log.info("Resumed torrent: {}", infoHash);
        }
    }

    public void removeTorrent(String infoHash, boolean deleteData) {
        TorrentHandle th = sessionManager.getJlibtorrentSession().find(new Sha1Hash(infoHash));
        if (th != null && th.isValid()) {
            if (deleteData) {
                sessionManager.getJlibtorrentSession().remove(th, com.frostwire.jlibtorrent.swig.session_handle.delete_files);
            } else {
                sessionManager.getJlibtorrentSession().remove(th);
            }
            log.info("Removed torrent from jlibtorrent session: {}", infoHash);
        }
        torrentRepository.delete(infoHash);
        resumeManager.deleteResumeData(infoHash);

        // Clean up backup torrent file if exists
        File backupFile = new File("data/torrents/" + infoHash.toLowerCase() + ".torrent");
        if (backupFile.exists()) {
            backupFile.delete();
        }
    }

    /**
     * Dynamic AlertListener to automatically write fastresume when libtorrent fires it.
     */
    private void setupAlertListener() {
        sessionManager.addListener(new AlertListener() {
            @Override
            public int[] types() {
                return new int[] {
                    AlertType.SAVE_RESUME_DATA.swig(),
                    AlertType.SAVE_RESUME_DATA_FAILED.swig(),
                    AlertType.METADATA_RECEIVED.swig()
                };
            }

            @Override
            public void alert(Alert<?> alert) {
                if (alert.type() == AlertType.SAVE_RESUME_DATA) {
                    SaveResumeDataAlert rAlert = (SaveResumeDataAlert) alert;
                    String infoHash = rAlert.handle().infoHash().toString();
                    try {
                        AddTorrentParams params = rAlert.params();
                        byte[] data = AddTorrentParams.writeResumeData(params).bencode();
                        resumeManager.saveResumeData(infoHash, data);

                        // Save the actual bencoded torrent backup if we just got metadata
                        saveTorrentBackupIfNeeded(rAlert.handle());
                    } catch (Exception e) {
                        log.error("Failed to extract fastresume bytes from alert", e);
                    }
                } else if (alert.type() == AlertType.METADATA_RECEIVED) {
                    MetadataReceivedAlert mAlert = (MetadataReceivedAlert) alert;
                    TorrentHandle th = mAlert.handle();
                    log.info("Metadata loaded for magnet torrent: {}. Updating database.", th.name());
                    
                    // Update torrent entry name in database
                    String infoHash = th.infoHash().toString();
                    com.vypeensoft.torrentshare.model.TorrentInfo existing = torrentRepository.findByHash(infoHash);
                    if (existing != null) {
                        com.vypeensoft.torrentshare.model.TorrentInfo updated = new com.vypeensoft.torrentshare.model.TorrentInfo(
                            existing.infoHash(),
                            th.name(),
                            existing.magnetUri(),
                            existing.savePath(),
                            existing.addedDate(),
                            existing.status(),
                            existing.resumeFile()
                        );
                        torrentRepository.save(updated);
                    }
                    
                    // Force initial fastresume generation
                    th.saveResumeData();
                }
            }
        });
    }

    private void saveTorrentBackupIfNeeded(TorrentHandle th) {
        String infoHash = th.infoHash().toString();
        File backupFile = new File("data/torrents/" + infoHash.toLowerCase() + ".torrent");
        if (!backupFile.exists() && th.torrentFile() != null) {
            try {
                File dir = backupFile.getParentFile();
                if (!dir.exists()) dir.mkdirs();

                byte[] bencoded = th.torrentFile().bencode();
                Files.write(backupFile.toPath(), bencoded);
                torrentRepository.updateResumeFile(infoHash, backupFile.getAbsolutePath());
                log.info("Created backup .torrent file for: {}", th.name());
            } catch (Exception e) {
                log.error("Failed to create backup torrent file for {}", th.name(), e);
            }
        }
    }

    /**
     * Merges the original magnet's trackers with the local tracker list, preserves the display name,
     * and passes through direct peer address hints (x.pe=) so the receiver can connect to the
     * sender immediately on LAN without waiting for trackers or DHT.
     */
    private String magnetWithAllTrackers(String magnet) {
        String hash = MagnetUtils.extractHash(magnet);
        if (hash == null) return magnet;

        // Preserve the original display name if present
        String dn = MagnetUtils.extractDisplayName(magnet);

        // Union: original trackers first, then local DB trackers (deduplicated)
        LinkedHashSet<String> allTrackers = new LinkedHashSet<>();
        allTrackers.addAll(MagnetUtils.extractTrackers(magnet));
        allTrackers.addAll(trackerManager.getTrackers());

        // Preserve x.pe= direct peer hints (sender's LAN IP:port) — critical for LAN discovery
        List<String> peerAddresses = MagnetUtils.extractPeerAddresses(magnet);

        return MagnetUtils.generateMagnet(hash, dn != null ? dn : "", new ArrayList<>(allTrackers), peerAddresses);
    }

    /**
     * Flushes fastresume files dynamically on clean shutdown.
     */
    public void prepareShutdown() {
        log.info("Preparing for clean shutdown, flushing all fastresume states...");
        TorrentHandle[] handles = sessionManager.getJlibtorrentSession().getTorrentHandles();
        if (handles == null || handles.length == 0) {
            return;
        }

        CountDownLatch latch = new CountDownLatch(handles.length);
        
        // Add one-time listener to count down finished fastresume saves
        AlertListener shutdownListener = new AlertListener() {
            @Override
            public int[] types() {
                return new int[] {
                    AlertType.SAVE_RESUME_DATA.swig(),
                    AlertType.SAVE_RESUME_DATA_FAILED.swig()
                };
            }

            @Override
            public void alert(Alert<?> alert) {
                latch.countDown();
            }
        };
        sessionManager.addListener(shutdownListener);

        for (TorrentHandle th : handles) {
            if (th.isValid()) {
                th.saveResumeData();
            } else {
                latch.countDown();
            }
        }

        try {
            // Wait up to 3 seconds for all fastresume parameters to flush
            if (!latch.await(3, TimeUnit.SECONDS)) {
                log.warn("Shutdown timeout reached while waiting for fastresume flush.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sessionManager.removeListener(shutdownListener);
        }
    }
}
