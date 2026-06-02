package com.torrentshare.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Common file system and size presentation helper utilities.
 */
public class FileUtils {
    private static final String[] UNITS = new String[] { "B", "KB", "MB", "GB", "TB" };

    private FileUtils() {}

    /**
     * Formats bytes into a human-readable string (e.g. 1.25 GB).
     */
    public static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.00").format(bytes / Math.pow(1024, digitGroups)) + " " + UNITS[digitGroups];
    }

    /**
     * Formats speeds in bytes/sec into readable strings (e.g. 512.40 KB/s).
     */
    public static String formatSpeed(long bytesPerSec) {
        return formatSize(bytesPerSec) + "/s";
    }

    /**
     * Recursively scans and collects all files within a directory, or returns the file itself.
     */
    public static List<File> listFilesRecursive(File root) {
        List<File> fileList = new ArrayList<>();
        scanRecursive(root, fileList);
        return fileList;
    }

    private static void scanRecursive(File file, List<File> results) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    scanRecursive(child, results);
                }
            }
        } else {
            results.add(file);
        }
    }

    /**
     * Safely creates directories if they do not exist.
     */
    public static void ensureDirectoryExists(File dir) {
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                // Success
            }
        }
    }
}
