package com.vypeensoft.torrentshare.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and builds standard BitTorrent Magnet URIs.
 */
public class MagnetUtils {
    private static final Logger log = LoggerFactory.getLogger(MagnetUtils.class);
    private static final Pattern BTIH_PATTERN = Pattern.compile("xt=urn:btih:([a-zA-Z0-9]{32,40})");

    private MagnetUtils() {}

    /**
     * Loads default trackers from trackers.properties (plain text, one URL per line).
     * Lines starting with '#' and blank lines are ignored.
     */
    public static List<String> loadDefaultTrackers() {
        try (InputStream input = MagnetUtils.class.getResourceAsStream("/trackers.properties")) {
            if (input != null) {
                List<String> list = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                            list.add(trimmed);
                        }
                    }
                }
                if (!list.isEmpty()) {
                    log.info("Loaded {} default trackers from trackers.properties", list.size());
                    return list;
                }
            } else {
                log.warn("trackers.properties not found on classpath — using built-in fallback list");
            }
        } catch (Exception e) {
            log.error("Failed to load trackers.properties — using built-in fallback list", e);
        }
        return List.of(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://tracker.dler.org:6969/announce"
        );
    }

    /**
     * Validates if the string is a valid Magnet Link format.
     */
    public static boolean isValidMagnet(String magnet) {
        if (magnet == null || !magnet.trim().startsWith("magnet:?")) {
            return false;
        }
        Matcher matcher = BTIH_PATTERN.matcher(magnet);
        return matcher.find();
    }

    /**
     * Extracts the Info Hash hex string from a magnet link.
     */
    public static String extractHash(String magnet) {
        if (magnet == null) return null;
        Matcher matcher = BTIH_PATTERN.matcher(magnet);
        if (matcher.find()) {
            String hash = matcher.group(1);
            if (hash.length() == 32) {
                // Base32 encoded (legacy)
                return hash.toUpperCase();
            }
            return hash.toLowerCase();
        }
        return null;
    }

    /**
     * Generates a Magnet Link with trackers, display name, and optional direct peer addresses (x.pe=).
     * Peer addresses allow the receiver to connect directly to the sender without needing trackers or DHT.
     * Format: IP:port for IPv4 (e.g. "192.168.1.10:6881")
     */
    public static String generateMagnet(String infoHash, String displayName,
                                        List<String> trackers, List<String> peerAddresses) {
        StringBuilder sb = new StringBuilder("magnet:?");
        sb.append("xt=urn:btih:").append(infoHash.toLowerCase());

        if (displayName != null && !displayName.isBlank()) {
            sb.append("&dn=").append(URLEncoder.encode(displayName, StandardCharsets.UTF_8));
        }

        if (trackers != null) {
            for (String tr : trackers) {
                if (!tr.isBlank()) {
                    sb.append("&tr=").append(URLEncoder.encode(tr, StandardCharsets.UTF_8));
                }
            }
        }

        if (peerAddresses != null) {
            for (String pe : peerAddresses) {
                if (!pe.isBlank()) {
                    sb.append("&x.pe=").append(URLEncoder.encode(pe, StandardCharsets.UTF_8));
                }
            }
        }

        return sb.toString();
    }

    /**
     * Generates a fully formatted Magnet Link containing trackers and name (no peer hints).
     */
    public static String generateMagnet(String infoHash, String displayName, List<String> trackers) {
        return generateMagnet(infoHash, displayName, trackers, null);
    }

    /**
     * Extracts the display name (dn=) from a magnet link, URL-decoded.
     * Returns null if not present.
     */
    public static String extractDisplayName(String magnet) {
        if (magnet == null) return null;
        for (String param : magnet.split("[?&]")) {
            if (param.startsWith("dn=")) {
                return URLDecoder.decode(param.substring(3), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * Extracts all tracker announce URLs (tr=) from a magnet link, URL-decoded.
     */
    public static List<String> extractTrackers(String magnet) {
        List<String> trackers = new ArrayList<>();
        if (magnet == null) return trackers;
        for (String param : magnet.split("[?&]")) {
            if (param.startsWith("tr=")) {
                String tr = URLDecoder.decode(param.substring(3), StandardCharsets.UTF_8);
                if (!tr.isBlank()) {
                    trackers.add(tr);
                }
            }
        }
        return trackers;
    }
    /**
     * Extracts all direct peer address hints (x.pe=) from a magnet link, URL-decoded.
     * These are in "IP:port" format and allow direct connections without needing trackers.
     */
    public static List<String> extractPeerAddresses(String magnet) {
        List<String> peers = new ArrayList<>();
        if (magnet == null) return peers;
        for (String param : magnet.split("[?&]")) {
            if (param.startsWith("x.pe=")) {
                String pe = URLDecoder.decode(param.substring(5), StandardCharsets.UTF_8);
                if (!pe.isBlank()) {
                    peers.add(pe);
                }
            }
        }
        return peers;
    }

    /**
     * Detects the machine's primary LAN IPv4 address (non-loopback, non-virtual).
     * Returns null if no suitable address is found.
     */
    public static String getLocalNetworkIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()
                            && !addr.isLinkLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not determine local network IP address", e);
        }
        return null;
    }
}
