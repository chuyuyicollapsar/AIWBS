package example.aiwbs.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import example.aiwbs.model.AiConfig;
import example.aiwbs.model.AppState;
import example.aiwbs.model.Book;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class SettingsStore {
    private final Path file;
    private final Gson gson;

    SettingsStore(Path dataDir) {
        this.file = dataDir.resolve("settings.json");
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .serializeNulls()
                .create();
    }

    SettingsData load() {
        if (!Files.exists(file)) {
            return new SettingsData();
        }
        try {
            SettingsData data = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), SettingsData.class);
            return data != null ? data.normalized() : new SettingsData();
        } catch (Exception e) {
            return new SettingsData();
        }
    }

    void save(AppState state) throws IOException {
        SettingsData data = new SettingsData();
        data.aiConfig = AiConfigData.from(state.getAiConfig());
        data.library = LibraryData.from(state.getBooks());
        writeJson(file, gson.toJson(data));
    }

    private static void writeJson(Path target, String json) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static final class SettingsData {
        int schemaVersion = 1;
        AiConfigData aiConfig = new AiConfigData();
        LibraryData library = new LibraryData();

        SettingsData normalized() {
            if (schemaVersion <= 0) schemaVersion = 1;
            if (aiConfig == null) aiConfig = new AiConfigData();
            if (library == null) library = new LibraryData();
            if (library.bookOrder == null) library.bookOrder = new ArrayList<>();
            return this;
        }

        List<String> bookOrder() {
            return library.bookOrder;
        }

        void applyTo(AiConfig target) {
            aiConfig.applyTo(target);
        }
    }

    private static final class LibraryData {
        List<String> bookOrder = new ArrayList<>();
        String selectedBookId;

        static LibraryData from(List<Book> books) {
            LibraryData data = new LibraryData();
            for (Book book : books) {
                if (book.getId() != null && !book.getId().isBlank()) {
                    data.bookOrder.add(book.getId());
                }
            }
            data.selectedBookId = data.bookOrder.isEmpty() ? null : data.bookOrder.get(0);
            return data;
        }
    }

    private static final class AiConfigData {
        String baseUrl = "";
        String apiKey = "";
        String modelId = "";
        String thirdPartyProtocol = AiConfig.AiProtocol.CHAT_COMPLETIONS.name();
        boolean useOfficialApi;
        String officialProvider = AiConfig.OfficialProvider.OPENAI.name();
        String officialApiKey = "";
        String officialModelId = "";
        String officialThinkingEffort = AiConfig.ThinkingEffort.MEDIUM.name();

        static AiConfigData from(AiConfig config) {
            AiConfigData data = new AiConfigData();
            data.baseUrl = safe(config.getBaseUrl());
            data.apiKey = safe(config.getApiKey());
            data.modelId = safe(config.getModelId());
            data.thirdPartyProtocol = safe(config.getThirdPartyProtocol(), AiConfig.AiProtocol.CHAT_COMPLETIONS.name());
            data.useOfficialApi = config.isUseOfficialApi();
            data.officialProvider = safe(config.getOfficialProvider(), AiConfig.OfficialProvider.OPENAI.name());
            data.officialApiKey = safe(config.getOfficialApiKey());
            data.officialModelId = safe(config.getOfficialModelId());
            data.officialThinkingEffort = safe(config.getOfficialThinkingEffort(), AiConfig.ThinkingEffort.MEDIUM.name());
            return data;
        }

        void applyTo(AiConfig config) {
            config.setBaseUrl(safe(baseUrl));
            config.setApiKey(safe(apiKey));
            config.setModelId(safe(modelId));
            config.setThirdPartyProtocol(safe(thirdPartyProtocol, AiConfig.AiProtocol.CHAT_COMPLETIONS.name()));
            config.setUseOfficialApi(useOfficialApi);
            config.setOfficialProvider(safe(officialProvider, AiConfig.OfficialProvider.OPENAI.name()));
            config.setOfficialApiKey(safe(officialApiKey));
            config.setOfficialModelId(safe(officialModelId));
            config.setOfficialThinkingEffort(safe(officialThinkingEffort, AiConfig.ThinkingEffort.MEDIUM.name()));
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
