package example.aiwbs.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import example.aiwbs.model.AiSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AiSessionStore {
    private static final Path AI_BOOKS_DIR = Path.of(System.getProperty("user.home"), ".aiwbs", "ai", "books");

    private final Path sessionsDir;
    private final Gson gson;

    public AiSessionStore(String bookId) {
        this.sessionsDir = AI_BOOKS_DIR.resolve(bookId).resolve("sessions");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public List<AiSession> loadAll() {
        if (!Files.isDirectory(sessionsDir)) return new ArrayList<>();
        List<AiSession> sessions = new ArrayList<>();
        try (var files = Files.list(sessionsDir)) {
            files.filter(f -> f.toString().endsWith(".json"))
                 .forEach(f -> {
                     AiSession s = load(f);
                     if (s != null) sessions.add(s);
                 });
        } catch (IOException ignored) {}
        sessions.sort(Comparator.comparingLong(AiSession::getUpdatedAt).reversed());
        return sessions;
    }

    public AiSession load(String sessionId) {
        return load(sessionsDir.resolve(sessionId + ".json"));
    }

    private AiSession load(Path file) {
        try {
            String json = Files.readString(file);
            return gson.fromJson(json, AiSession.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void save(AiSession session) {
        try {
            session.setUpdatedAt(System.currentTimeMillis());
            Files.createDirectories(sessionsDir);
            String json = gson.toJson(session);
            Files.writeString(sessionsDir.resolve(session.getId() + ".json"), json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void delete(AiSession session) {
        try {
            Files.deleteIfExists(sessionsDir.resolve(session.getId() + ".json"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteBookData(String bookId) {
        if (bookId == null || bookId.isBlank() || bookId.contains("/") || bookId.contains("\\")) return;

        Path root = AI_BOOKS_DIR.toAbsolutePath().normalize();
        Path target = root.resolve(bookId).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) return;

        try (var paths = Files.walk(target)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
