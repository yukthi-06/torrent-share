package com.torrentshare.model;

/**
 * Immutable metadata representing a torrent inside persistence and domain layer.
 */
public record TorrentInfo(
    String infoHash,
    String name,
    String magnetUri,
    String savePath,
    long addedDate,
    String status,
    String resumeFile
) {}
