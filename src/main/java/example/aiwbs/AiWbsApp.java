package example.aiwbs;

import example.aiwbs.model.AppState;
import example.aiwbs.storage.StateStore;
import example.aiwbs.ui.ShellView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AiWbsApp extends Application {
    @Override
    public void start(Stage stage) {
        StateStore store = new StateStore();
        AppState state = store.load();

        ShellView view = new ShellView(state, store);
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
