package com.vypeensoft.torrentshare.model;

/**
 * Immutable configuration holder for session settings and folders.
 */
public record ApplicationSettings(
    String downloadDir,
    String shareDir,
    int listeningPort,
    long uploadLimit,      // Bytes/second limit, 0 indicates no limit
    long downloadLimit,    // Bytes/second limit, 0 indicates no limit
    int maxConnections,
    int maxActiveTorrents
) {}
