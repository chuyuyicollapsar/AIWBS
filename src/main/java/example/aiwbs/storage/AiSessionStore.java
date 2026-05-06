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
    private final Path sessionsDir;
    private final Gson gson;

    public AiSessionStore(String bookId) {
        this.sessionsDir = Path.of(System.getProperty("user.home"), ".aiwbs", "ai", "books", bookId, "sessions");
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
}
