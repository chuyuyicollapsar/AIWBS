package example.aiwbs.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Chapter implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String title;
    private String content;
    private String outlineContent;
    private final List<ChapterVersion> versions = new ArrayList<>();

    public Chapter(String title, String content) {
        this.title = title;
        this.content = content;
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
