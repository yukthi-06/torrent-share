package com.vypeensoft.torrentshare.torrent;

import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.vypeensoft.torrentshare.model.TorrentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Periodically polls status metrics from the active jlibtorrent session.
 */
public class TorrentMonitor {
    private static final Logger log = LoggerFactory.getLogger(TorrentMonitor.class);
    private final SessionManager sessionManager;

    public TorrentMonitor(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }


    /**
     * Obtains the live list of all torrent statuses in the session.
     */
    public List<TorrentStatus> getActiveStatuses() {
        List<TorrentStatus> statuses = new ArrayList<>();
        TorrentHandle[] handles = sessionManager.getJlibtorrentSession().getTorrentHandles();

        if (handles == null) {
            return statuses;
        }

        for (TorrentHandle th : handles) {
            if (!th.isValid()) {
                continue;
            }

            try {
                com.frostwire.jlibtorrent.TorrentStatus ts = th.status();
                String infoHash = ts.infoHash().toString();
                String name = th.name();
                if (name == null || name.isBlank()) {
                    name = "Fetching metadata...";
                }

                long size = ts.totalWanted();
                if (size == 0 && th.torrentFile() != null) {
                    size = th.torrentFile().totalSize();
                }

                double progress = ts.progress();
                
                // Determine user-friendly state
                String stateStr = mapState(ts);

                long downloadSpeed = ts.downloadRate();
                long uploadSpeed = ts.uploadRate();
                int peers = ts.numPeers();
                int seeds = ts.numSeeds();

                long downloaded = ts.totalDone();
                long uploaded = ts.allTimeUpload();
                double ratio = downloaded > 0 ? (double) uploaded / downloaded : 0.0;

                long eta = -1;
                long remaining = size - downloaded;
                if (downloadSpeed > 0 && remaining > 0) {
                    eta = remaining / downloadSpeed;
                }

                statuses.add(new TorrentStatus(
                    infoHash,
                    name,
                    size,
                    progress,
                    stateStr,
                    downloadSpeed,
                    uploadSpeed,
                    peers,
                    seeds,
                    eta,
                    downloaded,
                    uploaded,
                    ratio
                ));
            } catch (Exception e) {
                log.error("Error building status for torrent handle", e);
            }
        }

        return statuses;
    }

    private String mapState(com.frostwire.jlibtorrent.TorrentStatus ts) {
        if (ts.flags().and_(com.frostwire.jlibtorrent.TorrentFlags.PAUSED).non_zero()) {
            return "Paused";
        }

        com.frostwire.jlibtorrent.TorrentStatus.State state = ts.state();
        if (state == null) {
            return "Unknown";
        }

        if (state == com.frostwire.jlibtorrent.TorrentStatus.State.CHECKING_FILES) {
            return "Checking";
        } else if (state == com.frostwire.jlibtorrent.TorrentStatus.State.DOWNLOADING_METADATA) {
            return "Metadata";
        } else if (state == com.frostwire.jlibtorrent.TorrentStatus.State.DOWNLOADING) {
            return "Downloading";
        } else if (state == com.frostwire.jlibtorrent.TorrentStatus.State.FINISHED) {
            return "Finished";
        } else if (state == com.frostwire.jlibtorrent.TorrentStatus.State.SEEDING) {
            return "Seeding";
        } else if (state == com.frostwire.jlibtorrent.TorrentStatus.State.CHECKING_RESUME_DATA) {
            return "Checking Resume";
        } else {
            return "Queued";
        }
    }
}
