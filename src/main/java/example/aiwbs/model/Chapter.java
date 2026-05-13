package example.aiwbs.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Chapter implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String title;
    private String id;
    private String content;
    private String outlineContent;
    private final List<ChapterVersion> versions = new ArrayList<>();

    public Chapter(String title, String content) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.content = content;
    }

    public String getId() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getOutlineContent() {
        return outlineContent != null ? outlineContent : "";
    }

    public void setOutlineContent(String outlineContent) {
        this.outlineContent = outlineContent;
    }

    public List<ChapterVersion> getVersions() {
        return versions;
    }

    @Override
    public String toString() {
        return title;
    }
}
