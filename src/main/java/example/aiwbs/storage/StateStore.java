package example.aiwbs.storage;

import example.aiwbs.model.AppState;
import example.aiwbs.model.Book;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class StateStore {
    private final SettingsStore settingsStore;
    private final BookStore bookStore;
    private final PackageStore packageStore;

    public StateStore() {
        this(Path.of(System.getProperty("user.home"), ".aiwbs"));
    }

    StateStore(Path dataDir) {
        this.settingsStore = new SettingsStore(dataDir);
        this.bookStore = new BookStore(dataDir);
        this.packageStore = new PackageStore(dataDir, bookStore);
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

    public String importCover(Book book, Path source) throws IOException {
        return bookStore.importCover(book, source);
    }

    public Path resolveProjectFile(String path) {
        return bookStore.resolveProjectFile(path);
    }

    public PackageTransferResult exportPackage(AppState state, Path target) throws IOException {
        save(state);
        return packageStore.exportPackage(state, target);
    }

    public PackageTransferResult importPackage(AppState state, Path source) throws IOException {
        PackageTransferResult result = packageStore.importPackage(state, source);
        save(state);
        return result;
    }
}
