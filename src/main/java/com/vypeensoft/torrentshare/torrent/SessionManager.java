package com.vypeensoft.torrentshare.torrent;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.swig.settings_pack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the lifetime and network features of the jlibtorrent Session.
 */
public class SessionManager {
    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    private final com.frostwire.jlibtorrent.SessionManager sm;
    private boolean running = false;

    public SessionManager() {
        this.sm = new com.frostwire.jlibtorrent.SessionManager();
    }

    public synchronized void start(int listeningPort) {
        if (running) {
            return;
        }

        log.info("Starting jlibtorrent SessionManager on port {}...", listeningPort);

        SessionParams params = new SessionParams();
        SettingsPack settings = params.getSettings();

        settings.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true);
        settings.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true);
        settings.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true);
        settings.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true);
        settings.setBoolean(settings_pack.bool_types.allow_multiple_connections_per_ip.swigValue(), true);
        settings.setString(settings_pack.string_types.listen_interfaces.swigValue(), "0.0.0.0:" + listeningPort + ",[::]:" + listeningPort);

        // Standard optimization configs
        settings.activeDownloads(20);
        settings.activeSeeds(20);
        settings.setInteger(settings_pack.int_types.active_limit.swigValue(), 40);

        sm.start(params);
        running = true;
        log.info("jlibtorrent SessionManager started successfully.");
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        log.info("Stopping jlibtorrent SessionManager cleanly...");
        sm.stop();
        running = false;
        log.info("jlibtorrent SessionManager stopped.");
    }

    public com.frostwire.jlibtorrent.SessionManager getJlibtorrentSession() {
        return sm;
    }

    public void addListener(AlertListener listener) {
        sm.addListener(listener);
    }

    public void removeListener(AlertListener listener) {
        sm.removeListener(listener);
    }

    public void setUploadRateLimit(int limitInBytesPerSec) {
        sm.uploadRateLimit(limitInBytesPerSec);
        log.debug("Set session upload rate limit to {} B/s", limitInBytesPerSec);
    }

    public void setDownloadRateLimit(int limitInBytesPerSec) {
        sm.downloadRateLimit(limitInBytesPerSec);
        log.debug("Set session download rate limit to {} B/s", limitInBytesPerSec);
    }

    public void setMaxConnections(int maxConnections) {
        sm.maxConnections(maxConnections);
        log.debug("Set session max connections limit to {}", maxConnections);
    }
}
