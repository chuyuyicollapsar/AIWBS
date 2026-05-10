package example.aiwbs.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import example.aiwbs.model.AiConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class AiClient {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public String testConnection(AiConfig config) throws Exception {
        String apiKey = config.effectiveApiKey();
        String modelId = config.effectiveModelId();

        if (!config.isUseOfficialApi()) {
            return testOpenAiCompatible(config.effectiveBaseUrl(), apiKey, modelId, null);
        }

        return switch (config.resolveOfficialProvider()) {
            case OPENAI -> testOpenAiCompatible(config.effectiveBaseUrl(), apiKey, modelId, config);
            case GEMINI -> testGemini(config.effectiveBaseUrl(), apiKey, modelId, config);
            case ANTHROPIC -> testAnthropic(config.effectiveBaseUrl(), apiKey, modelId, config);
            case DEEPSEEK -> testDeepSeek(config.effectiveBaseUrl(), apiKey, modelId, config);
        };
    }

    // ── OpenAI-compatible (OpenAI official + third-party) ──

    private String testOpenAiCompatible(String baseUrl, String apiKey,
                                        String modelId, AiConfig config) throws Exception {
        validateNotBlank(baseUrl, "Base URL");
        validateNotBlank(apiKey, "API Key");
        validateNotBlank(modelId, "Model ID");

        JsonArray messages = JsonParser.parseString("""
                [
                  {"role": "system", "content": "You are a writing assistant."},
                  {"role": "user", "content": "Reply with exactly: OK"}
                ]
                """).getAsJsonArray();
        String body = buildChatCompletionBody(config, modelId, messages, null, true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl(baseUrl)))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + trim(response.body()));
        }
        return trim(extractOpenAiContent(response.body()));
    }

    // ── Response extractors ──

    private String extractOpenAiContent(String json) {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.isEmpty()) throw new IllegalStateException("API returned empty response");
        if (!trimmed.startsWith("{")) {
            throw new IllegalStateException("API returned non-JSON response (check baseUrl): " + trim(trimmed));
        }
        JsonObject root = JsonParser.parseString(trimmed).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("No content in API response (check model/API key): " + trim(trimmed));
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null || !message.has("content") || message.get("content").isJsonNull()) {
            return "";
        }
        return contentToText(message.get("content"));
    }

    private String extractAnthropicContent(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray content = root.getAsJsonArray("content");
        if (content == null) return json;
        StringBuilder text = new StringBuilder();
        for (JsonElement partEl : content) {
            if (!partEl.isJsonObject()) continue;
            JsonObject part = partEl.getAsJsonObject();
            if (part.has("text") && !part.get("text").isJsonNull()) {
                text.append(part.get("text").getAsString());
            }
        }
        return text.toString();
    }

    private String extractGeminiContent(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) return json;
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        if (content == null) return json;
        return contentToText(content.get("parts"));
    }

    private String extractDeepSeekContent(String json) {
        return extractOpenAiContent(json);
    }

    // ════════════════════════════════════════
    //  Chat (OpenAI-compatible)
    // ════════════════════════════════════════

    public String chat(AiConfig config, String systemPrompt, List<String> messageJsonObjects) throws Exception {
        String raw = chatRaw(config, systemPrompt, messageJsonObjects, null);
        return extractOpenAiContent(raw);
    }

    /**
     * 发送聊天请求（支持 tools），返回 API 原始响应体（JSON）。
     * toolsJson 为 null 时不发送 tools 参数。
     */
    public String chatRaw(AiConfig config, String systemPrompt,
                          List<String> messageJsonObjects, String toolsJson) throws Exception {
        String apiKey = config.effectiveApiKey();
        String modelId = config.effectiveModelId();
        String baseUrl = config.effectiveBaseUrl();
        validateNotBlank(apiKey, "API Key");
        validateNotBlank(modelId, "Model ID");
        validateNotBlank(baseUrl, "Base URL");

        StringBuilder msgJson = new StringBuilder();
        String sep = "";
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            msgJson.append("{\"role\":\"system\",\"content\":").append(toJsonString(systemPrompt)).append("}");
            sep = ",";
        }
        for (String m : messageJsonObjects) {
            msgJson.append(sep).append(m);
            sep = ",";
        }

        JsonArray messages = JsonParser.parseString("[" + msgJson + "]").getAsJsonArray();
        String body = buildChatCompletionBody(config, modelId, messages, toolsJson, false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl(baseUrl)))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + trim(response.body()));
        }
        return response.body();
    }

    private String buildChatCompletionBody(AiConfig config, String modelId, JsonArray messages,
                                           String toolsJson, boolean testRequest) {
        JsonObject body = new JsonObject();
        body.addProperty("model", modelId);
        body.add("messages", messages);
        if (shouldSendTemperature(config, modelId)) {
            body.addProperty("temperature", testRequest ? 0.2 : 0.7);
        }
        if (toolsJson != null && !toolsJson.isBlank()) {
            body.add("tools", JsonParser.parseString(toolsJson));
        }
        addThinkingConfig(body, config, modelId);
        return body.toString();
    }

    private String testGemini(String baseUrl, String apiKey, String modelId, AiConfig config) throws Exception {
        validateNotBlank(baseUrl, "Base URL");
        validateNotBlank(apiKey, "API Key");
        validateNotBlank(modelId, "Model ID");

        JsonArray messages = JsonParser.parseString("""
                [
                  {"role": "system", "content": "You are a writing assistant."},
                  {"role": "user", "content": "Reply with exactly: OK"}
                ]
                """).getAsJsonArray();
        String body = buildChatCompletionBody(config, modelId, messages, null, true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl(baseUrl)))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + trim(response.body()));
        }
        return trim(extractGeminiContent(response.body()));
    }

    private String testAnthropic(String baseUrl, String apiKey, String modelId, AiConfig config) throws Exception {
        validateNotBlank(baseUrl, "Base URL");
        validateNotBlank(apiKey, "API Key");
        validateNotBlank(modelId, "Model ID");

        JsonObject body = new JsonObject();
        body.addProperty("model", modelId);
        body.addProperty("max_tokens", 4096);
        body.addProperty("system", "You are a writing assistant.");
        JsonArray messages = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", "Reply with exactly: OK");
        messages.add(user);
        body.add("messages", messages);
        addAnthropicThinking(body, modelId, config.resolveOfficialThinkingEffort());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeAnthropicMessagesUrl(baseUrl)))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + trim(response.body()));
        }
        return trim(extractAnthropicContent(response.body()));
    }

    private String testDeepSeek(String baseUrl, String apiKey, String modelId, AiConfig config) throws Exception {
        validateNotBlank(baseUrl, "Base URL");
        validateNotBlank(apiKey, "API Key");
        validateNotBlank(modelId, "Model ID");

        JsonArray messages = JsonParser.parseString("""
                [
                  {"role": "system", "content": "You are a writing assistant."},
                  {"role": "user", "content": "Reply with exactly: OK"}
                ]
                """).getAsJsonArray();
        String body = buildChatCompletionBody(config, modelId, messages, null, true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl(baseUrl)))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + trim(response.body()));
        }
        return trim(extractDeepSeekContent(response.body()));
    }

    private boolean shouldSendTemperature(AiConfig config, String modelId) {
        if (config == null || !config.isUseOfficialApi()) return true;
        AiConfig.OfficialProvider provider = config.resolveOfficialProvider();
        if (provider == AiConfig.OfficialProvider.ANTHROPIC) return false;
        if (provider == AiConfig.OfficialProvider.DEEPSEEK) return false;
        return true;
    }

    private void addThinkingConfig(JsonObject body, AiConfig config, String modelId) {
        if (config == null || !config.isUseOfficialApi()) return;

        AiConfig.ThinkingEffort effort = config.resolveOfficialThinkingEffort();
        switch (config.resolveOfficialProvider()) {
            case OPENAI -> addOpenAiThinking(body, modelId, effort);
            case GEMINI -> addGeminiThinking(body, modelId, effort);
            case ANTHROPIC -> addAnthropicThinking(body, modelId, effort);
            case DEEPSEEK -> addDeepSeekThinking(body, modelId, effort);
        }
    }

    private void addOpenAiThinking(JsonObject body, String modelId, AiConfig.ThinkingEffort effort) {
        if (modelId == null || !modelId.startsWith("gpt-5")) return;
        body.addProperty("reasoning_effort", switch (effort) {
            case NONE -> "none";
            case LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH -> "high";
            case MAX -> "xhigh";
        });
    }

    private void addGeminiThinking(JsonObject body, String modelId, AiConfig.ThinkingEffort effort) {
        if (modelId == null || !modelId.startsWith("gemini-")) return;
        String mapped = switch (effort) {
            case NONE -> modelId.startsWith("gemini-2.5") && !modelId.contains("pro") ? "none" : "low";
            case LOW -> "low";
            case MEDIUM -> modelId.startsWith("gemini-3") ? "low" : "medium";
            case HIGH, MAX -> "high";
        };
        body.addProperty("reasoning_effort", mapped);
    }

    private void addAnthropicThinking(JsonObject body, String modelId, AiConfig.ThinkingEffort effort) {
        if (effort == AiConfig.ThinkingEffort.NONE) {
            return;
        }

        int budgetTokens = switch (effort) {
            case LOW -> 1024;
            case MEDIUM -> 2048;
            case HIGH -> 4096;
            case MAX -> 8192;
            case NONE -> 0;
        };
        JsonObject thinking = new JsonObject();
        thinking.addProperty("type", "enabled");
        thinking.addProperty("budget_tokens", budgetTokens);
        body.add("thinking", thinking);
        body.addProperty("max_tokens", budgetTokens + 1024);
    }

    private void addDeepSeekThinking(JsonObject body, String modelId, AiConfig.ThinkingEffort effort) {
        if (effort == AiConfig.ThinkingEffort.NONE) {
            body.add("thinking", disabledThinkingObject());
            return;
        }

        body.add("thinking", enabledThinkingObject());
        body.addProperty("reasoning_effort", switch (effort) {
            case LOW, MEDIUM, HIGH -> "high";
            case MAX -> "max";
            case NONE -> "high";
        });
    }

    private JsonObject enabledThinkingObject() {
        JsonObject thinking = new JsonObject();
        thinking.addProperty("type", "enabled");
        return thinking;
    }

    private JsonObject disabledThinkingObject() {
        JsonObject thinking = new JsonObject();
        thinking.addProperty("type", "disabled");
        return thinking;
    }

    private String normalizeAnthropicMessagesUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/messages")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/messages";
        }
        return normalized + "/v1/messages";
    }

    private String contentToText(JsonElement content) {
        if (content == null || content.isJsonNull()) return "";
        if (content.isJsonPrimitive()) return content.getAsString();
        if (!content.isJsonArray()) return content.toString();

        StringBuilder text = new StringBuilder();
        for (JsonElement partEl : content.getAsJsonArray()) {
            if (!partEl.isJsonObject()) continue;
            JsonObject part = partEl.getAsJsonObject();
            if (part.has("text") && !part.get("text").isJsonNull()) {
                text.append(part.get("text").getAsString());
            }
        }
        return text.toString();
    }

    private String toJsonString(String value) {
        return "\"" + escapeJson(value == null ? "" : value) + "\"";
    }

    // ── Helpers ──

    private String chatCompletionsUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    private void validateNotBlank(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value) {
        if (value == null) return "";
        return value.length() > 600 ? value.substring(0, 600) + "..." : value;
    }
}
