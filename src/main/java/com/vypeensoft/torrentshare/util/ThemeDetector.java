package com.vypeensoft.torrentshare.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Utility to detect the host operating system's theme preference.
 */
public class ThemeDetector {
    private static final Logger log = LoggerFactory.getLogger(ThemeDetector.class);

    /**
     * Detects if Windows is configured to use Dark Mode.
     * Defaults to false (Light Mode) on errors or non-Windows OS.
     */
    public static boolean isDarkMode() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            return false;
        }
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                "reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme"
            });
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("AppsUseLightTheme")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length > 0) {
                            String last = parts[parts.length - 1];
                            // 0x0 or 0 indicates Dark mode
                            boolean dark = last.equals("0x0") || last.equals("0");
                            log.info("System theme detected: {}", dark ? "DARK" : "LIGHT");
                            return dark;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to detect system theme via registry query: {}", e.getMessage());
        }
        return false;
    }
}
