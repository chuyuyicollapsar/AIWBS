package example.aiwbs;

import example.aiwbs.model.AppState;
import example.aiwbs.model.Book;
import example.aiwbs.model.Chapter;
import example.aiwbs.model.Volume;
import example.aiwbs.storage.StateStore;
import example.aiwbs.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AiWbsApp extends Application {
    @Override
    public void start(Stage stage) {
        StateStore store = new StateStore();
        AppState state = store.load();
        if (state.getBooks().isEmpty()) {
            Book book = new Book("Sample Book");
            book.setSummary("This is a demo summary for the first book.");
            book.setCoverPath("");
            Volume volume = new Volume("Volume 1");
            volume.getChapters().add(new Chapter("Chapter 1", "Start writing here."));
            book.getVolumes().add(volume);
            state.getBooks().add(book);
        }

        MainView view = new MainView(state, store);
        Scene scene = new Scene(view.getRoot(), 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/example/aiwbs/ui/app.css").toExternalForm());
        stage.setTitle("AI WBS");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
