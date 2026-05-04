package example.aiwbs.model;

import java.io.Serial;
import java.io.Serializable;

public class AiConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String modelId = "gpt-4.1-mini";

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
}
