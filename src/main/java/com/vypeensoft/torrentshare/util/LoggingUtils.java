package com.vypeensoft.torrentshare.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic helper utility for diagnostic and bootstrapping logger logging events.
 */
public class LoggingUtils {
    private static final Logger log = LoggerFactory.getLogger(LoggingUtils.class);

    private LoggingUtils() {}

    /**
     * Diagnostic bootstrap log event.
     */
    public static void logStartup() {
        log.info("========================================");
        log.info("Starting TorrentShare BitTorrent Desktop Engine");
        log.info("Runtime JVM Version: {}", System.getProperty("java.version"));
        log.info("Runtime OS Name: {}", System.getProperty("os.name"));
        log.info("========================================");
    }
}
