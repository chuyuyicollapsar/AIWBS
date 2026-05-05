package example.aiwbs.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Volume implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private String outlineContent;
    private final List<Chapter> chapters = new ArrayList<>();

    public Volume(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOutlineContent() {
        return outlineContent != null ? outlineContent : "";
    }

    public void setOutlineContent(String outlineContent) {
        this.outlineContent = outlineContent;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }

    @Override
    public String toString() {
        return name;
    }
}
