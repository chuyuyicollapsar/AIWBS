package example.aiwbs.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AiMessage {
    private final String id;
    private String title;
    private String userContent;
    private String assistantContent;
    private String assistantReasoningContent;
    private long timestamp;
    private final List<AiMessage> children;

    public AiMessage(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.timestamp = System.currentTimeMillis();
        this.children = new ArrayList<>();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUserContent() { return userContent; }
    public void setUserContent(String userContent) { this.userContent = userContent; }
    public String getAssistantContent() { return assistantContent; }
    public void setAssistantContent(String assistantContent) { this.assistantContent = assistantContent; }
    public String getAssistantReasoningContent() { return assistantReasoningContent; }
    public void setAssistantReasoningContent(String assistantReasoningContent) {
        this.assistantReasoningContent = assistantReasoningContent;
    }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public List<AiMessage> getChildren() { return children; }
}
