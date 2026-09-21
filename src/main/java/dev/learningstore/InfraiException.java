package dev.learningstore;

import java.util.Map;

public final class InfraiException extends RuntimeException {
    private final String code;
    private final int status;
    private final Map<String, Object> details;

    public InfraiException(String code, Map<String, Object> details, int status) {
        super(code + ": " + details);
        this.code = code;
        this.details = Map.copyOf(details);
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }
    public Map<String, Object> details() { return details; }
}
