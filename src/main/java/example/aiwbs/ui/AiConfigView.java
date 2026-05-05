package example.aiwbs.ui;

import example.aiwbs.ai.AiClient;
import example.aiwbs.model.AiConfig;
import example.aiwbs.model.AppState;
import example.aiwbs.storage.StateStore;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

public class AiConfigView {
    private final VBox root = new VBox(16);
    private final AiClient aiClient = new AiClient();
    private final AppState state;
    private final StateStore store;

    public AiConfigView(AppState state, StateStore store) {
        this.state = state;
        this.store = store;

        root.setPadding(new Insets(20, 24, 24, 24));

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(buildThirdPartyTab(), buildOfficialTab());

        root.getChildren().add(tabs);
    }

    public Parent getRoot() {
        return root;
    }

    // ════════════════════════════════════════
    //  Third-party API tab
    // ════════════════════════════════════════

    private Tab buildThirdPartyTab() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 0, 0, 0));

        AiConfig cfg = state.getAiConfig();

        TextField baseUrl = new TextField(cfg.getBaseUrl());
        baseUrl.setPrefWidth(500);
        PasswordField apiKey = new PasswordField();
        apiKey.setText(cfg.getApiKey());
        TextField modelId = new TextField(cfg.getModelId());
        TextArea result = new TextArea();
        result.setEditable(false);
        result.setWrapText(true);
        result.setPrefRowCount(5);

        grid.addRow(0, label("Base URL"), baseUrl);
        grid.addRow(1, label("API Key"), apiKey);
        grid.addRow(2, label("Model ID"), modelId);

        HBox actions = new HBox(10);
        Button save = new Button("Save Config");
        Button test = new Button("Test Connection");
        actions.getChildren().addAll(save, test);
        grid.add(actions, 1, 3);
        grid.add(label("Result"), 0, 4);
        grid.add(result, 1, 4);

        save.setOnAction(e -> {
            cfg.setBaseUrl(baseUrl.getText());
            cfg.setApiKey(apiKey.getText());
            cfg.setModelId(modelId.getText());
            cfg.setUseOfficialApi(false);
            store.save(state);
            result.setText("Configuration saved.");
        });

        test.setOnAction(e -> {
            cfg.setBaseUrl(baseUrl.getText());
            cfg.setApiKey(apiKey.getText());
            cfg.setModelId(modelId.getText());
            cfg.setUseOfficialApi(false);
            store.save(state);
            result.setText("Testing...");
            test.setDisable(true);

            Thread worker = new Thread(() -> {
                try {
                    String response = aiClient.testConnection(cfg);
                    Platform.runLater(() -> result.setText("Success:\n" + response));
                } catch (Exception ex) {
                    Platform.runLater(() -> result.setText("Failed:\n" + ex.getMessage()));
                } finally {
                    Platform.runLater(() -> test.setDisable(false));
                }
            });
            worker.setDaemon(true);
            worker.start();
        });

        Tab tab = new Tab("Third-Party API");
        tab.setContent(grid);
        return tab;
    }

    // ════════════════════════════════════════
    //  Official API tab
    // ════════════════════════════════════════

    private static final Map<String, List<String>> OFFICIAL_MODELS = Map.of(
            "OPENAI", List.of("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano"),
            "ANTHROPIC", List.of("claude-sonnet-4-20250514", "claude-3-5-sonnet-20241022",
                    "claude-3-5-haiku-20241022", "claude-3-opus-20240229"),
            "GEMINI", List.of("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-pro", "gemini-1.5-flash"),
            "DEEPSEEK", List.of("deepseek-chat", "deepseek-reasoner")
    );

    private Tab buildOfficialTab() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 0, 0, 0));

        AiConfig cfg = state.getAiConfig();

        ComboBox<String> providerBox = new ComboBox<>(
                FXCollections.observableArrayList(
                        AiConfig.OfficialProvider.OPENAI.name(),
                        AiConfig.OfficialProvider.ANTHROPIC.name(),
                        AiConfig.OfficialProvider.GEMINI.name(),
                        AiConfig.OfficialProvider.DEEPSEEK.name()
                )
        );
        providerBox.setValue(cfg.getOfficialProvider());

        PasswordField officialApiKey = new PasswordField();
        officialApiKey.setText(cfg.getOfficialApiKey());

        ComboBox<String> modelBox = new ComboBox<>();
        modelBox.setPrefWidth(300);
        modelBox.setEditable(true);
        updateModelList(modelBox, cfg.getOfficialProvider());
        modelBox.setValue(cfg.getOfficialModelId());

        TextField baseUrlDisplay = new TextField(cfg.resolveOfficialProvider().defaultBaseUrl);
        baseUrlDisplay.setEditable(false);
        baseUrlDisplay.setStyle("-fx-opacity: 0.8;");

        TextArea result = new TextArea();
        result.setEditable(false);
        result.setWrapText(true);
        result.setPrefRowCount(5);

        grid.addRow(0, label("Provider"), providerBox);
        grid.addRow(1, label("API Key"), officialApiKey);
        grid.addRow(2, label("Model"), modelBox);
        grid.addRow(3, label("Base URL"), baseUrlDisplay);

        HBox actions = new HBox(10);
        Button save = new Button("Save Config");
        Button test = new Button("Test Connection");
        actions.getChildren().addAll(save, test);
        grid.add(actions, 1, 4);
        grid.add(label("Result"), 0, 5);
        grid.add(result, 1, 5);

        providerBox.setOnAction(e -> {
            String provider = providerBox.getValue();
            updateModelList(modelBox, provider);
            modelBox.getSelectionModel().selectFirst();
            for (AiConfig.OfficialProvider p : AiConfig.OfficialProvider.values()) {
                if (p.name().equals(provider)) {
                    baseUrlDisplay.setText(p.defaultBaseUrl);
                    break;
                }
            }
        });

        save.setOnAction(e -> {
            cfg.setOfficialProvider(providerBox.getValue());
            cfg.setOfficialApiKey(officialApiKey.getText());
            cfg.setOfficialModelId(modelBox.getValue());
            cfg.setUseOfficialApi(true);
            store.save(state);
            result.setText("Configuration saved.");
        });

        test.setOnAction(e -> {
            cfg.setOfficialProvider(providerBox.getValue());
            cfg.setOfficialApiKey(officialApiKey.getText());
            cfg.setOfficialModelId(modelBox.getValue());
            cfg.setUseOfficialApi(true);
            store.save(state);
            result.setText("Testing...");
            test.setDisable(true);

            Thread worker = new Thread(() -> {
                try {
                    String response = aiClient.testConnection(cfg);
                    Platform.runLater(() -> result.setText("Success:\n" + response));
                } catch (Exception ex) {
                    Platform.runLater(() -> result.setText("Failed:\n" + ex.getMessage()));
                } finally {
                    Platform.runLater(() -> test.setDisable(false));
                }
            });
            worker.setDaemon(true);
            worker.start();
        });

        Tab tab = new Tab("Official API");
        tab.setContent(grid);
        return tab;
    }

    private void updateModelList(ComboBox<String> modelBox, String provider) {
        List<String> models = OFFICIAL_MODELS.getOrDefault(provider, List.of());
        modelBox.setItems(FXCollections.observableArrayList(models));
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.setMinWidth(80);
        return l;
    }
}
