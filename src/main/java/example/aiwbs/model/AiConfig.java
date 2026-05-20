package example.aiwbs.model;

import java.io.Serial;
import java.io.Serializable;

public class AiConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public enum OfficialProvider {
        OPENAI("OpenAI", "https://api.openai.com/v1"),
        ANTHROPIC("Anthropic", "https://api.anthropic.com/v1/"),
        GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/"),
        DEEPSEEK("DeepSeek", "https://api.deepseek.com");

        public final String label;
        public final String defaultBaseUrl;

        OfficialProvider(String label, String defaultBaseUrl) {
            this.label = label;
            this.defaultBaseUrl = defaultBaseUrl;
        }
    }

    public enum ThinkingEffort {
        NONE("None"),
        LOW("Low"),
        MEDIUM("Medium"),
        HIGH("High"),
        MAX("Max");

        public final String label;

        ThinkingEffort(String label) {
            this.label = label;
        }
    }

    public enum AiProtocol {
        CHAT_COMPLETIONS("Chat Completions"),
        RESPONSES("Responses"),
        ANTHROPIC_MESSAGES("Anthropic Messages");

        public final String label;

        AiProtocol(String label) {
            this.label = label;
        }
    }

    // Third-party fields
    private String baseUrl = "";
    private String apiKey = "";
    private String modelId = "";
    private String thirdPartyProtocol = AiProtocol.CHAT_COMPLETIONS.name();

    // Official API fields
    private boolean useOfficialApi = false;
    private String officialProvider = OfficialProvider.OPENAI.name();
    private String officialApiKey = "";
    private String officialModelId = "";
    private String officialThinkingEffort = ThinkingEffort.MEDIUM.name();

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

    public String getThirdPartyProtocol() {
        return thirdPartyProtocol;
    }

    public void setThirdPartyProtocol(String thirdPartyProtocol) {
        this.thirdPartyProtocol = thirdPartyProtocol;
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

    public String getOfficialThinkingEffort() {
        return officialThinkingEffort;
    }

    public void setOfficialThinkingEffort(String officialThinkingEffort) {
        this.officialThinkingEffort = officialThinkingEffort;
    }

    // ── Helpers ──

    public OfficialProvider resolveOfficialProvider() {
        try {
            return OfficialProvider.valueOf(officialProvider);
        } catch (Exception e) {
            return OfficialProvider.OPENAI;
        }
    }

    public ThinkingEffort resolveOfficialThinkingEffort() {
        try {
            return ThinkingEffort.valueOf(officialThinkingEffort);
        } catch (Exception e) {
            return ThinkingEffort.MEDIUM;
        }
    }

    public AiProtocol resolveThirdPartyProtocol() {
        try {
            return AiProtocol.valueOf(thirdPartyProtocol);
        } catch (Exception e) {
            return AiProtocol.CHAT_COMPLETIONS;
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

    /** Get the selected reasoning depth for official API use. */
    public String effectiveThinkingEffort() {
        return useOfficialApi ? officialThinkingEffort : ThinkingEffort.MEDIUM.name();
    }

    /** Get the effective base URL based on active config type. */
    public String effectiveBaseUrl() {
        if (useOfficialApi) {
            return resolveOfficialProvider().defaultBaseUrl;
        }
        return baseUrl;
    }
}
