package example.aiwbs.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

public class ChapterVersion implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final LocalDateTime savedAt;
    private final String content;
    private final int wordCount;

    public ChapterVersion(LocalDateTime savedAt, String content, int wordCount) {
        this.savedAt = savedAt;
        this.content = content;
        this.wordCount = wordCount;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }

    public String getContent() {
        return content;
    }

    public int getWordCount() {
        return wordCount;
    }
}
