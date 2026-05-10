package example.aiwbs.storage;

import example.aiwbs.model.AppState;
import example.aiwbs.model.Book;

import java.nio.file.Path;
import java.util.List;

public class StateStore {
    private final SettingsStore settingsStore;
    private final BookStore bookStore;

    public StateStore() {
        this(Path.of(System.getProperty("user.home"), ".aiwbs"));
    }

    StateStore(Path dataDir) {
        this.settingsStore = new SettingsStore(dataDir);
        this.bookStore = new BookStore(dataDir);
    }

    public AppState load() {
        AppState state = new AppState();
        SettingsStore.SettingsData settings = settingsStore.load();
        settings.applyTo(state.getAiConfig());
        List<Book> books = bookStore.loadAll(settings.bookOrder());
        state.getBooks().addAll(books);
        return state;
    }

    public void save(AppState state) {
        try {
            bookStore.saveAll(state.getBooks());
            settingsStore.save(state);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
