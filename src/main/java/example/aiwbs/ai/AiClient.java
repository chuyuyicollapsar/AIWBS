package example.aiwbs.ai;

import example.aiwbs.model.AiConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AiClient {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public String testConnection(AiConfig config) throws Exception {
        validate(config);
        String body = """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "system", "content": "You are a writing assistant."},
                    {"role": "user", "content": "Reply with exactly: OK"}
                  ],
                  "temperature": 0.2
                }
                """.formatted(escapeJson(config.getModelId()));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl(config.getBaseUrl())))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + trim(response.body()));
        }
        return trim(extractFirstContent(response.body()));
    }

    private void validate(AiConfig config) {
        if (isBlank(config.getBaseUrl())) {
            throw new IllegalArgumentException("Base URL is required.");
        }
        if (isBlank(config.getApiKey())) {
            throw new IllegalArgumentException("API Key is required.");
        }
        if (isBlank(config.getModelId())) {
            throw new IllegalArgumentException("Model ID is required.");
        }
    }

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

    private String extractFirstContent(String json) {
        String marker = "\"content\"";
        int markerIndex = json.indexOf(marker);
        if (markerIndex < 0) {
            return json;
        }
        int colon = json.indexOf(':', markerIndex + marker.length());
        int quote = json.indexOf('"', colon + 1);
        if (colon < 0 || quote < 0) {
            return json;
        }
        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int i = quote + 1; i < json.length(); i++) {
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

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 600 ? value.substring(0, 600) + "..." : value;
    }
}
