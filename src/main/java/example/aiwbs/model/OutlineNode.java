package example.aiwbs.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OutlineNode implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private String title;
    private String content;
    private int sortOrder;
    private final List<OutlineNode> children = new ArrayList<>();

    public OutlineNode(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.content = "";
    }

    public String getId() {
        return id;
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

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public List<OutlineNode> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return title;
    }
}
