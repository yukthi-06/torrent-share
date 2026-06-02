package com.torrentshare.torrent;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.TorrentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * High-performance builder class that parses files and creates .torrent metadata using TorrentBuilder.
 */
public class TorrentCreator {
    private static final Logger log = LoggerFactory.getLogger(TorrentCreator.class);

    private TorrentCreator() {}

    /**
     * Creates a bencoded torrent representation for a single file or a directory.
     * Note: This is an expensive synchronous call and MUST be run on a background thread.
     */
    public static byte[] createTorrent(File inputPath, List<String> trackers) throws IOException {
        log.info("Preparing metadata generation using TorrentBuilder for: {}", inputPath.getAbsolutePath());

        TorrentBuilder builder = new TorrentBuilder();
        builder.path(inputPath);

        // Add trackers with tier priority
        int tier = 0;
        for (String tr : trackers) {
            if (!tr.isBlank()) {
                builder.addTracker(tr, tier++);
            }
        }

        builder.creator("TorrentShare 1.0");

        log.info("Calculating SHA-1 piece hashes for files...");
        TorrentBuilder.Result result = builder.generate();
        log.info("Successfully finished hashing all pieces.");

        Entry torrentEntry = result.entry();
        return torrentEntry.bencode();
    }
}
