package example.aiwbs.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AiSession {
    private String id;
    private String title;
    private long createdAt;
    private long updatedAt;
    private List<AiMessage> rootMessages;

    public AiSession(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.rootMessages = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public List<AiMessage> getRootMessages() { return rootMessages; }
    public void setRootMessages(List<AiMessage> rootMessages) { this.rootMessages = rootMessages; }
}
