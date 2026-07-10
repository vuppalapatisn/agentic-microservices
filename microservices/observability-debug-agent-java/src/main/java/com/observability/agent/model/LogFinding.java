package com.observability.agent.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogFinding {

    private static final Pattern DURATION_PATTERN = Pattern.compile("durationMs=(\\d+)");

    private String timestamp;
    private String service;
    private String level;
    private String message;

    public LogFinding() {}

    public LogFinding(String timestamp, String service, String level, String message) {
        this.timestamp = timestamp;
        this.service = service;
        this.level = level;
        this.message = message;
    }

    /** Parses durationMs=NNNN from the log message, mirroring Python duration_ms property. */
    public Long getDurationMs() {
        if (message == null) return null;
        Matcher m = DURATION_PATTERN.matcher(message);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String v) { this.timestamp = v; }

    public String getService() { return service; }
    public void setService(String v) { this.service = v; }

    public String getLevel() { return level; }
    public void setLevel(String v) { this.level = v; }

    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
}
