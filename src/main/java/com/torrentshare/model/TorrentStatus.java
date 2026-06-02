package com.torrentshare.model;

/**
 * Holds real-time performance and state metrics for a torrent session.
 */
public class TorrentStatus {
    private final String infoHash;
    private final String name;
    private final long size;
    private final double progress; // 0.0 to 1.0
    private final String state;
    private final long downloadSpeed; // Bytes/sec
    private final long uploadSpeed; // Bytes/sec
    private final int peers;
    private final int seeds;
    private final long eta; // ETA in seconds
    private final long downloaded;
    private final long uploaded;
    private final double ratio;

    public TorrentStatus(String infoHash, String name, long size, double progress, String state,
                         long downloadSpeed, long uploadSpeed, int peers, int seeds, long eta,
                         long downloaded, long uploaded, double ratio) {
        this.infoHash = infoHash;
        this.name = name;
        this.size = size;
        this.progress = progress;
        this.state = state;
        this.downloadSpeed = downloadSpeed;
        this.uploadSpeed = uploadSpeed;
        this.peers = peers;
        this.seeds = seeds;
        this.eta = eta;
        this.downloaded = downloaded;
        this.uploaded = uploaded;
        this.ratio = ratio;
    }

    public String getInfoHash() { return infoHash; }
    public String getName() { return name; }
    public long getSize() { return size; }
    public double getProgress() { return progress; }
    public String getState() { return state; }
    public long getDownloadSpeed() { return downloadSpeed; }
    public long getUploadSpeed() { return uploadSpeed; }
    public int getPeers() { return peers; }
    public int getSeeds() { return seeds; }
    public long getEta() { return eta; }
    public long getDownloaded() { return downloaded; }
    public long getUploaded() { return uploaded; }
    public double getRatio() { return ratio; }
}
