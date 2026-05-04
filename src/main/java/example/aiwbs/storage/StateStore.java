package example.aiwbs.storage;

import example.aiwbs.model.AppState;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class StateStore {
    private final Path file = Path.of(System.getProperty("user.home"), ".aiwbs", "state.bin");

    public AppState load() {
        if (!Files.exists(file)) {
            return new AppState();
        }
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
            return (AppState) in.readObject();
        } catch (Exception e) {
            return new AppState();
        }
    }

    public void save(AppState state) {
        try {
            Files.createDirectories(file.getParent());
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file))) {
                out.writeObject(state);
            }
        } catch (Exception ignored) {
        }
    }
}
