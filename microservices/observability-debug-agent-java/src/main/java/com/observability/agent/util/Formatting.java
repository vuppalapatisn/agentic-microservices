package com.observability.agent.util;

/**
 * Formats raw Prometheus values into human-readable strings.
 * Mirrors Python app/util/formatting.py.
 */
public final class Formatting {

    private Formatting() {}

    public static String formatBytes(double value) {
        if (value < 0) value = 0;
        if (value >= 1_073_741_824) return String.format("%.2f GB", value / 1_073_741_824);
        if (value >= 1_048_576)     return String.format("%.2f MB", value / 1_048_576);
        if (value >= 1_024)         return String.format("%.2f KB", value / 1_024);
        return String.format("%.2f B", value);
    }

    public static String formatRps(double value) {
        if (value < 0) value = 0;
        if (value >= 100) return String.format("%.0f rps", value);
        if (value >= 10)  return String.format("%.1f rps", value);
        return String.format("%.2f rps", value);
    }

    public static String formatPercent(double value) {
        if (value < 0) value = 0;
        return String.format("%.1f%%", value);
    }

    public static String formatCount(double value) {
        return String.valueOf((long) Math.round(value));
    }
}
