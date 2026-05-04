package example.aiwbs.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Book implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private String coverPath;
    private String summary;
    private final List<Volume> volumes = new ArrayList<>();
    private List<OutlineNode> outlineRoots;

    public Book(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCoverPath() {
        return coverPath;
    }

    public void setCoverPath(String coverPath) {
        this.coverPath = coverPath;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<Volume> getVolumes() {
        return volumes;
    }

    public List<OutlineNode> getOutlineRoots() {
        if (outlineRoots == null) {
            outlineRoots = new ArrayList<>();
        }
        return outlineRoots;
    }

    @Override
    public String toString() {
        return name;
    }
}
