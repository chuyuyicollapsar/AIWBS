package example.aiwbs.ui;

import example.aiwbs.ai.AiClient;
import example.aiwbs.model.AppState;
import example.aiwbs.storage.StateStore;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

public class AiConfigView {
    private final GridPane root = new GridPane();
    private final AiClient aiClient = new AiClient();

    public AiConfigView(AppState state, StateStore store) {
        TextField baseUrl = new TextField(state.getAiConfig().getBaseUrl());
        PasswordField apiKey = new PasswordField();
        apiKey.setText(state.getAiConfig().getApiKey());
        TextField modelId = new TextField(state.getAiConfig().getModelId());
        TextArea result = new TextArea();
        result.setEditable(false);
        result.setWrapText(true);
        result.setPrefRowCount(6);

        root.setPadding(new Insets(16));
        root.setHgap(10);
        root.setVgap(10);
        root.addRow(0, new Label("Base URL"), baseUrl);
        root.addRow(1, new Label("API Key"), apiKey);
        root.addRow(2, new Label("Model ID"), modelId);

        Button save = new Button("Save Config");
        save.setOnAction(e -> {
            state.getAiConfig().setBaseUrl(baseUrl.getText());
            state.getAiConfig().setApiKey(apiKey.getText());
            state.getAiConfig().setModelId(modelId.getText());
            store.save(state);
            result.setText("Saved.");
        });
        root.add(save, 1, 3);

        Button test = new Button("Test Connection");
        test.setOnAction(e -> {
            state.getAiConfig().setBaseUrl(baseUrl.getText());
            state.getAiConfig().setApiKey(apiKey.getText());
            state.getAiConfig().setModelId(modelId.getText());
            store.save(state);
            result.setText("Testing...");
            test.setDisable(true);

            Thread worker = new Thread(() -> {
                try {
                    String response = aiClient.testConnection(state.getAiConfig());
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
        root.add(test, 1, 4);
        root.add(new Label("Result"), 0, 5);
        root.add(result, 1, 5);
    }

    public Parent getRoot() {
        return root;
    }
}
