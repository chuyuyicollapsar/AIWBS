package example.aiwbs.ui;

import example.aiwbs.model.AppState;
import example.aiwbs.storage.StateStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class SettingsView {
    private final BorderPane root = new BorderPane();
    private final VBox sidebar = new VBox(2);
    private final StackPane contentArea = new StackPane();
    private Button activeNav;
    private final AppState state;
    private final StateStore store;
    private final Runnable onBack;

    public SettingsView(AppState state, StateStore store, Runnable onBack) {
        this.state = state;
        this.store = store;
        this.onBack = onBack;

        root.setStyle("-fx-background-color: #0f1730;");

        // ── Sidebar ──
        sidebar.setPrefWidth(220);
        sidebar.setMaxWidth(220);
        sidebar.setPadding(new Insets(16, 8, 16, 8));
        sidebar.setStyle("-fx-background-color: #111a34;");

        // Back to Books
        Button backBtn = navButton("←  Back to Books");
        backBtn.setOnAction(e -> onBack.run());
        sidebar.getChildren().add(backBtn);

        sidebar.getChildren().add(sep());

        // Nav items
        Button apiConfig = navButton("  API Config");
        apiConfig.setOnAction(e -> selectNav(apiConfig, buildApiConfigContent()));
        sidebar.getChildren().add(apiConfig);

        // Spacer to push items to top
        VBox spacer = new VBox();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);

        // ── Content area ──
        contentArea.setPadding(Insets.EMPTY);

        root.setLeft(sidebar);
        root.setCenter(contentArea);

        // Select first item
        selectNav(apiConfig, buildApiConfigContent());
    }

    public Parent getRoot() {
        return root;
    }

    // ── Navigation ──

    private void selectNav(Button button, Node content) {
        if (activeNav != null) {
            activeNav.setStyle(null);
        }
        activeNav = button;
        activeNav.setStyle("-fx-background-color: #22335f; -fx-text-fill: #d7bb74;");
        contentArea.getChildren().setAll(content);
    }

    // ── Content builders ──

    private Node buildApiConfigContent() {
        VBox page = new VBox(16);
        page.setPadding(new Insets(20, 24, 24, 24));

        Label header = new Label("API Configuration");
        header.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");

        AiConfigView configView = new AiConfigView(state, store);
        VBox.setVgrow(configView.getRoot(), Priority.ALWAYS);

        page.getChildren().addAll(header, configView.getRoot());
        return page;
    }

    // ── Helpers ──

    private static Button navButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(10, 12, 10, 12));
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #e7ecff; -fx-background-radius: 8; -fx-border-color: transparent; -fx-font-size: 14px;");
        return btn;
    }

    private static Separator sep() {
        Separator s = new Separator();
        s.setPadding(new Insets(8, 0, 8, 0));
        return s;
    }
}
