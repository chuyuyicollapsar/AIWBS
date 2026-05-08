package example.aiwbs.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 返回完整的 JSON 大纲树，保留完整的父子层级结构。
     */
    public String getOutlineTreeString() {
        if (outlineRoots == null || outlineRoots.isEmpty()) {
            return "[]";
        }
        JsonArray arr = toJsonArray(outlineRoots);
        return GSON.toJson(arr);
    }

    private JsonArray toJsonArray(List<OutlineNode> nodes) {
        JsonArray arr = new JsonArray();
        for (OutlineNode n : nodes) {
            JsonObject obj = new JsonObject();
            obj.addProperty("title", n.getTitle());
            String c = n.getContent();
            if (c != null && !c.isEmpty()) {
                obj.addProperty("content", c);
            }
            if (!n.getChildren().isEmpty()) {
                obj.add("children", toJsonArray(n.getChildren()));
            }
            arr.add(obj);
        }
        return arr;
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
     * 按分卷名获取细纲。分卷名必须唯一，避免 AI 工具调用出现歧义。
     */
    public String getVolumeOutline(String volumeName) {
        Volume volume = findUniqueVolume(volumeName);
        if (volume == null) return "（未找到分卷：" + displayName(volumeName) + "）";
        if (volume == DUPLICATE_VOLUME) return "（分卷名重复，无法唯一定位：" + displayName(volumeName) + "）";
        String oc = volume.getOutlineContent();
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
     * 按分卷名和章节名获取章节细纲。
     */
    public String getChapterOutline(String volumeName, String chapterName) {
        ChapterLookup lookup = findUniqueChapter(volumeName, chapterName);
        if (lookup.error != null) return lookup.error;
        String oc = lookup.chapter.getOutlineContent();
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

    /**
     * 按分卷名和章节名获取章节正文。
     */
    public String getChapterContent(String volumeName, String chapterName) {
        ChapterLookup lookup = findUniqueChapter(volumeName, chapterName);
        if (lookup.error != null) return lookup.error;
        String content = lookup.chapter.getContent();
        return content == null || content.isEmpty() ? "（该章无正文）" : content;
    }

    private static final Volume DUPLICATE_VOLUME = new Volume("__duplicate_volume_marker__");

    private Volume findUniqueVolume(String volumeName) {
        String target = normalizeName(volumeName);
        if (target.isEmpty()) return null;
        Volume found = null;
        for (Volume volume : volumes) {
            if (!normalizeName(volume.getName()).equals(target)) continue;
            if (found != null) return DUPLICATE_VOLUME;
            found = volume;
        }
        return found;
    }

    private ChapterLookup findUniqueChapter(String volumeName, String chapterName) {
        Volume volume = findUniqueVolume(volumeName);
        if (volume == null) {
            return ChapterLookup.error("（未找到分卷：" + displayName(volumeName) + "）");
        }
        if (volume == DUPLICATE_VOLUME) {
            return ChapterLookup.error("（分卷名重复，无法唯一定位：" + displayName(volumeName) + "）");
        }

        String target = normalizeName(chapterName);
        if (target.isEmpty()) {
            return ChapterLookup.error("（章节名不能为空）");
        }
        Chapter found = null;
        for (Chapter chapter : volume.getChapters()) {
            if (!normalizeName(chapter.getTitle()).equals(target)) continue;
            if (found != null) {
                return ChapterLookup.error("（章节名重复，无法唯一定位：" + displayName(chapterName) + "）");
            }
            found = chapter;
        }
        if (found == null) {
            return ChapterLookup.error("（未找到章节：" + displayName(chapterName) + "）");
        }
        return ChapterLookup.chapter(found);
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private static String displayName(String value) {
        String normalized = normalizeName(value);
        return normalized.isEmpty() ? "空名称" : normalized;
    }

    private record ChapterLookup(Chapter chapter, String error) {
        static ChapterLookup chapter(Chapter chapter) {
            return new ChapterLookup(chapter, null);
        }

        static ChapterLookup error(String error) {
            return new ChapterLookup(null, error);
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
