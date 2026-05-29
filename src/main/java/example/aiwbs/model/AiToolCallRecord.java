package example.aiwbs.model;

import java.util.UUID;

public class AiToolCallRecord {
    public enum Status {
        RUNNING,
        SUCCESS,
        ERROR
    }

    private String id;
    private String name;
    private String argumentsJson;
    private Status status;
    private long startedAt;
    private long endedAt;
    private long durationMs;
    private int resultSize;
    private String error;

    public AiToolCallRecord() {
        this.id = UUID.randomUUID().toString();
        this.status = Status.RUNNING;
        this.startedAt = System.currentTimeMillis();
        this.argumentsJson = "";
        this.error = "";
    }

    public AiToolCallRecord(String name, String argumentsJson) {
        this();
        this.name = name;
        this.argumentsJson = argumentsJson == null ? "" : argumentsJson;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getArgumentsJson() { return argumentsJson; }
    public void setArgumentsJson(String argumentsJson) { this.argumentsJson = argumentsJson; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
    public long getEndedAt() { return endedAt; }
    public void setEndedAt(long endedAt) { this.endedAt = endedAt; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public int getResultSize() { return resultSize; }
    public void setResultSize(int resultSize) { this.resultSize = resultSize; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public void completeSuccess(String result) {
        long now = System.currentTimeMillis();
        this.status = Status.SUCCESS;
        this.endedAt = now;
        this.durationMs = Math.max(0, now - startedAt);
        this.resultSize = result == null ? 0 : result.length();
        this.error = "";
    }

    public void completeError(String errorMessage) {
        long now = System.currentTimeMillis();
        this.status = Status.ERROR;
        this.endedAt = now;
        this.durationMs = Math.max(0, now - startedAt);
        this.resultSize = errorMessage == null ? 0 : errorMessage.length();
        this.error = preview(errorMessage);
    }

    private String preview(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        return normalized.length() > 800 ? normalized.substring(0, 800) + "..." : normalized;
    }
}
