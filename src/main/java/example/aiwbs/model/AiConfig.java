package example.aiwbs.model;

import java.io.Serial;
import java.io.Serializable;

public class AiConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public enum OfficialProvider {
        OPENAI("OpenAI", "https://api.openai.com/v1"),
        ANTHROPIC("Anthropic", "https://api.anthropic.com"),
        GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1"),
        DEEPSEEK("DeepSeek", "https://api.deepseek.com");

        public final String label;
        public final String defaultBaseUrl;

        OfficialProvider(String label, String defaultBaseUrl) {
            this.label = label;
            this.defaultBaseUrl = defaultBaseUrl;
        }
    }

    // Third-party fields
    private String baseUrl = "";
    private String apiKey = "";
    private String modelId = "";

    // Official API fields
    private boolean useOfficialApi = false;
    private String officialProvider = OfficialProvider.OPENAI.name();
    private String officialApiKey = "";
    private String officialModelId = "";

    // ── Third-party getters/setters ──

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    // ── Official API getters/setters ──

    public boolean isUseOfficialApi() {
        return useOfficialApi;
    }

    public void setUseOfficialApi(boolean useOfficialApi) {
        this.useOfficialApi = useOfficialApi;
    }

    public String getOfficialProvider() {
        return officialProvider;
    }

    public void setOfficialProvider(String officialProvider) {
        this.officialProvider = officialProvider;
    }

    public String getOfficialApiKey() {
        return officialApiKey;
    }

    public void setOfficialApiKey(String officialApiKey) {
        this.officialApiKey = officialApiKey;
    }

    public String getOfficialModelId() {
        return officialModelId;
    }

    public void setOfficialModelId(String officialModelId) {
        this.officialModelId = officialModelId;
    }

    // ── Helpers ──

    public OfficialProvider resolveOfficialProvider() {
        try {
            return OfficialProvider.valueOf(officialProvider);
        } catch (Exception e) {
            return OfficialProvider.OPENAI;
        }
    }

    /** Get the effective API key based on active config type. */
    public String effectiveApiKey() {
        return useOfficialApi ? officialApiKey : apiKey;
    }

    /** Get the effective model ID based on active config type. */
    public String effectiveModelId() {
        return useOfficialApi ? officialModelId : modelId;
    }

    /** Get the effective base URL based on active config type. */
    public String effectiveBaseUrl() {
        if (useOfficialApi) {
            return resolveOfficialProvider().defaultBaseUrl;
        }
        return baseUrl;
    }
}
