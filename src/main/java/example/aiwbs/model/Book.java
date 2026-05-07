package example.aiwbs.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Book implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String coverPath;
    private String summary;
    private final List<Volume> volumes = new ArrayList<>();
    private List<OutlineNode> outlineRoots;

    public Book(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }

    public String getId() {
        return id;
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

    /**
     * 返回格式化的目录字符串：分卷结构、章节名称及每章字数。
     */
    public String getTableOfContents() {
        if (volumes.isEmpty()) {
            return "（无分卷）";
        }
        StringBuilder sb = new StringBuilder();
        int totalChapters = 0;
        int totalWords = 0;
        for (int vi = 0; vi < volumes.size(); vi++) {
            Volume v = volumes.get(vi);
            List<Chapter> chapters = v.getChapters();
            int volWords = 0;
            for (Chapter ch : chapters) {
                volWords += simpleWordCount(ch.getContent());
            }
            totalChapters += chapters.size();
            totalWords += volWords;
            sb.append("第").append(vi + 1).append("卷：").append(v.getName())
              .append("（").append(chapters.size()).append("章，共 ").append(volWords).append(" 字）\n");
            for (int ci = 0; ci < chapters.size(); ci++) {
                Chapter ch = chapters.get(ci);
                int wc = simpleWordCount(ch.getContent());
                sb.append("  第").append(ci + 1).append("章 · ").append(ch.getTitle())
                  .append("（").append(wc).append(" 字）\n");
            }
        }
        sb.append("（共 ").append(volumes.size()).append(" 卷 ").append(totalChapters).append(" 章，合计 ").append(totalWords).append(" 字）");
        return sb.toString();
    }

    private static int simpleWordCount(String s) {
        if (s == null || s.isEmpty()) return 0;
        int n = 0;
        boolean inWord = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                inWord = false;
            } else if (Character.isIdeographic(c)) {
                n++;
                inWord = false;
            } else if (!inWord) {
                n++;
                inWord = true;
            }
        }
        return n;
    }

    /**
     * 返回格式化的完整大纲树：标题 + 内容（递归缩进）。
     */
    public String getOutlineTreeString() {
        if (outlineRoots == null || outlineRoots.isEmpty()) {
            return "（无大纲）";
        }
        StringBuilder sb = new StringBuilder();
        appendOutlineNodes(sb, outlineRoots, 0);
        return sb.toString();
    }

    private void appendOutlineNodes(StringBuilder sb, List<OutlineNode> nodes, int depth) {
        String indent = "  ".repeat(depth);
        for (OutlineNode n : nodes) {
            sb.append(indent).append("· ").append(n.getTitle());
            String c = n.getContent();
            if (c != null && !c.isEmpty()) {
                sb.append("：").append(c.length() > 100 ? c.substring(0, 100) + "…" : c);
            }
            sb.append('\n');
            if (!n.getChildren().isEmpty()) {
                appendOutlineNodes(sb, n.getChildren(), depth + 1);
            }
        }
    }

    /**
     * 获取第 volIndex 卷的细纲（从 1 开始）。
     */
    public String getVolumeOutline(int volIndex) {
        if (volIndex < 1 || volIndex > volumes.size()) {
            return "（无效卷号，共 " + volumes.size() + " 卷）";
        }
        String oc = volumes.get(volIndex - 1).getOutlineContent();
        return oc.isEmpty() ? "（该分卷无细纲）" : oc;
    }

    /**
     * 获取第 volIndex 卷第 chIndex 章的细纲（从 1 开始）。
     */
    public String getChapterOutline(int volIndex, int chIndex) {
        if (volIndex < 1 || volIndex > volumes.size()) {
            return "（无效卷号，共 " + volumes.size() + " 卷）";
        }
        List<Chapter> chapters = volumes.get(volIndex - 1).getChapters();
        if (chIndex < 1 || chIndex > chapters.size()) {
            return "（无效章号，该卷共 " + chapters.size() + " 章）";
        }
        String oc = chapters.get(chIndex - 1).getOutlineContent();
        return oc.isEmpty() ? "（该章无细纲）" : oc;
    }

    /**
     * 获取第 volIndex 卷第 chIndex 章的正文（从 1 开始）。
     */
    public String getChapterContent(int volIndex, int chIndex) {
        if (volIndex < 1 || volIndex > volumes.size()) {
            return "（无效卷号，共 " + volumes.size() + " 卷）";
        }
        List<Chapter> chapters = volumes.get(volIndex - 1).getChapters();
        if (chIndex < 1 || chIndex > chapters.size()) {
            return "（无效章号，该卷共 " + chapters.size() + " 章）";
        }
        String content = chapters.get(chIndex - 1).getContent();
        return content == null || content.isEmpty() ? "（该章无正文）" : content;
    }

    @Override
    public String toString() {
        return name;
    }
}
