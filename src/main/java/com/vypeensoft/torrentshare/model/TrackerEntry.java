package com.vypeensoft.torrentshare.model;

/**
 * Represents a tracker entry with its URL and user custom classification.
 */
public record TrackerEntry(
    String url,
    boolean isCustom
) {}
