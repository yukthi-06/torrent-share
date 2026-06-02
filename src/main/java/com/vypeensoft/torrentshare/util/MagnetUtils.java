package com.vypeensoft.torrentshare.util;

import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and builds standard BitTorrent Magnet URIs.
 */
public class MagnetUtils {
    private static final Pattern BTIH_PATTERN = Pattern.compile("xt=urn:btih:([a-zA-Z0-9]{32,40})");

    private MagnetUtils() {}

    /**
     * Loads default trackers from properties resource file.
     */
    public static List<String> loadDefaultTrackers() {
        Properties props = new Properties();
        try (InputStream input = MagnetUtils.class.getResourceAsStream("/trackers.properties")) {
            if (input != null) {
                props.load(input);
                String trackersVal = props.getProperty("trackers");
                if (trackersVal != null && !trackersVal.isBlank()) {
                    String[] split = trackersVal.split(",");
                    List<String> list = new ArrayList<>();
                    for (String s : split) {
                        String trimmed = s.trim();
                        if (!trimmed.isEmpty()) {
                            list.add(trimmed);
                        }
                    }
                    return list;
                }
            }
        } catch (Exception e) {
            // fallback handled below
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
     * Generates a fully formatted Magnet Link containing trackers and name.
     */
    public static String generateMagnet(String infoHash, String displayName, List<String> trackers) {
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
        return sb.toString();
    }
}
