package example.aiwbs.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import example.aiwbs.model.AiToolCallRecord;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AiToolCallStore {
    private static final Path AI_BOOKS_DIR = Path.of(System.getProperty("user.home"), ".aiwbs", "ai", "books");
    private static final Type LIST_TYPE = new TypeToken<List<AiToolCallRecord>>() {}.getType();

    private final Path cacheDir;
    private final Gson gson;

    public AiToolCallStore(String bookId) {
        this.cacheDir = AI_BOOKS_DIR.resolve(bookId).resolve("tool-calls");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public List<AiToolCallRecord> load(String sessionId, String messageId) {
        if (sessionId == null || messageId == null) return new ArrayList<>();
        Path file = file(sessionId, messageId);
        if (!Files.isRegularFile(file)) return new ArrayList<>();
        try {
            List<AiToolCallRecord> calls = gson.fromJson(Files.readString(file), LIST_TYPE);
            return calls == null ? new ArrayList<>() : new ArrayList<>(calls);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    public void save(String sessionId, String messageId, List<AiToolCallRecord> calls) {
        if (sessionId == null || messageId == null || calls == null) return;
        try {
            Files.createDirectories(cacheDir);
            Files.writeString(file(sessionId, messageId), gson.toJson(calls));
        } catch (IOException ignored) {
        }
    }

    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || !Files.isDirectory(cacheDir)) return;
        try (var files = Files.list(cacheDir)) {
            String prefix = safe(sessionId) + "__";
            files.filter(file -> file.getFileName().toString().startsWith(prefix))
                    .forEach(file -> {
                        try {
                            Files.deleteIfExists(file);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    public void deleteMessage(String sessionId, String messageId) {
        if (sessionId == null || messageId == null) return;
        try {
            Files.deleteIfExists(file(sessionId, messageId));
        } catch (IOException ignored) {
        }
    }

    private Path file(String sessionId, String messageId) {
        return cacheDir.resolve(safe(sessionId) + "__" + safe(messageId) + ".json");
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
