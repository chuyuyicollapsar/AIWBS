package example.aiwbs.ai;

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

        if (config.isUseOfficialApi()) {
            return switch (config.resolveOfficialProvider()) {
                case ANTHROPIC -> testAnthropic(apiKey, modelId);
                case GEMINI -> testGemini(apiKey, modelId);
                default -> testOpenAiCompatible(config.effectiveBaseUrl(), apiKey, modelId);
            };
        }
        return testOpenAiCompatible(config.effectiveBaseUrl(), apiKey, modelId);
    }

    // ── OpenAI-compatible (OpenAI official + third-party) ──

    private String testOpenAiCompatible(String baseUrl, String apiKey, String modelId) throws Exception {
        validateNotBlank(baseUrl, "Base URL");
        validateNotBlank(apiKey, "API Key");
        validateNotBlank(modelId, "Model ID");

        String body = """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "system", "content": "You are a writing assistant."},
                    {"role": "user", "content": "Reply with exactly: OK"}
                  ],
                  "temperature": 0.2
                }
                """.formatted(escapeJson(modelId));

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

    // ── Anthropic ──

    private String testAnthropic(String apiKey, String modelId) throws Exception {
        validateNotBlank(apiKey, "API Key");
        if (isBlank(modelId)) modelId = "claude-sonnet-4-20250514";

        String body = """
                {
                  "model": "%s",
                  "max_tokens": 256,
                  "system": "You are a writing assistant.",
                  "messages": [
                    {"role": "user", "content": "Reply with exactly: OK"}
                  ]
                }
                """.formatted(escapeJson(modelId));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + trim(response.body()));
        }
        return trim(extractAnthropicContent(response.body()));
    }

    // ── Gemini ──

    private String testGemini(String apiKey, String modelId) throws Exception {
        validateNotBlank(apiKey, "API Key");
        if (isBlank(modelId)) modelId = "gemini-2.0-flash";

        String body = """
                {
                  "contents": [
                    {
                      "parts": [
                        {"text": "Reply with exactly: OK"}
                      ]
                    }
                  ],
                  "systemInstruction": {
                    "parts": [{"text": "You are a writing assistant."}]
                  }
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1/models/"
                        + escapeJson(modelId) + ":generateContent?key=" + escapeJson(apiKey)))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + trim(response.body()));
        }
        return trim(extractGeminiContent(response.body()));
    }

    // ── Response extractors ──

    private String extractOpenAiContent(String json) {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.isEmpty()) throw new IllegalStateException("API returned empty response");
        if (!trimmed.startsWith("{")) {
            throw new IllegalStateException("API returned non-JSON response (check baseUrl): " + trim(trimmed));
        }
        String marker = "\"content\"";
        int markerIndex = trimmed.indexOf(marker);
        if (markerIndex < 0) {
            throw new IllegalStateException("No content in API response (check model/API key): " + trim(trimmed));
        }
        int colon = trimmed.indexOf(':', markerIndex + marker.length());
        int quote = trimmed.indexOf('"', colon + 1);
        if (colon < 0 || quote < 0) {
            throw new IllegalStateException("Cannot parse API response: " + trim(trimmed));
        }
        return extractRawString(trimmed, quote + 1);
    }

    private String extractAnthropicContent(String json) {
        // Anthropic: {"content":[{"type":"text","text":"OK"}]}
        String marker = "\"text\"";
        int markerIndex = json.indexOf(marker);
        if (markerIndex < 0) return json;
        int colon = json.indexOf(':', markerIndex + marker.length());
        int quote = json.indexOf('"', colon + 1);
        if (colon < 0 || quote < 0) return json;
        return extractRawString(json, quote + 1);
    }

    private String extractGeminiContent(String json) {
        // Gemini: {"candidates":[{"content":{"parts":[{"text":"OK"}]}}]}
        String marker = "\"text\"";
        int markerIndex = json.indexOf(marker);
        if (markerIndex < 0) return json;
        int colon = json.indexOf(':', markerIndex + marker.length());
        int quote = json.indexOf('"', colon + 1);
        if (colon < 0 || quote < 0) return json;
        return extractRawString(json, quote + 1);
    }

    private String extractRawString(String json, int start) {
        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaping) {
                value.append(switch (c) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> c;
                });
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }
        return json;
    }

    // ════════════════════════════════════════
    //  Chat (OpenAI-compatible)
    // ════════════════════════════════════════

    public String chat(AiConfig config, String systemPrompt, List<String> messageJsonObjects) throws Exception {
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

        String body = "{\"model\":\"" + escapeJson(modelId) + "\",\"messages\":["
                + msgJson + "],\"temperature\":0.7}";

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
        return extractOpenAiContent(response.body());
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
