package example.aiwbs.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import example.aiwbs.model.AiConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OpenAiResponsesClient {
    private static final int MAX_TOOL_ROUNDS = 10;
    private static final Duration CHAT_TIMEOUT = Duration.ofMinutes(5);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    String testConnection(AiConfig config) throws Exception {
        JsonArray input = new JsonArray();
        input.add(message("user", "Reply with exactly: OK"));
        JsonObject body = baseBody(config, "You are a writing assistant.", input, false, false, null);
        JsonObject response = postJson(config, responsesUrl(config.effectiveBaseUrl()), body);
        return trim(extractContent(response));
    }

    AiClient.ChatResult chat(AiConfig config, String systemPrompt, List<String> messageJsonObjects,
                             AiClient.ToolExecutor toolExecutor) throws Exception {
        JsonArray input = buildInput(messageJsonObjects);
        String lastRaw = "";
        for (int depth = 0; depth <= MAX_TOOL_ROUNDS; depth++) {
            JsonObject body = baseBody(config, systemPrompt, input, false, toolExecutor != null, null);
            JsonObject response = postJson(config, responsesUrl(config.effectiveBaseUrl()), body);
            lastRaw = response.toString();
            List<ResponseToolCall> toolCalls = extractToolCalls(response);
            if (toolExecutor == null || toolCalls.isEmpty()) {
                return new AiClient.ChatResult(extractContent(response), "", lastRaw);
            }
            if (depth == MAX_TOOL_ROUNDS) {
                return new AiClient.ChatResult("[Error] Tool call loop exceeded max depth", "", lastRaw);
            }
            addOutputItems(input, response, List.of());
            int toolRound = depth + 1;
            for (ResponseToolCall toolCall : toolCalls) {
                input.add(toolResult(toolCall, toolExecutor.execute(toolCall.name(), toolCall.arguments(), toolRound)));
            }
        }
        return new AiClient.ChatResult("[Error] Tool call loop exceeded max depth", "", lastRaw);
    }

    AiClient.ChatResult stream(AiConfig config, String systemPrompt, List<String> messageJsonObjects,
                               AiClient.ToolExecutor toolExecutor, AiClient.StreamListener listener) throws Exception {
        JsonArray input = buildInput(messageJsonObjects);
        StringBuilder totalContent = new StringBuilder();
        String lastRaw = "";

        for (int depth = 0; depth <= MAX_TOOL_ROUNDS; depth++) {
            JsonObject body = baseBody(config, systemPrompt, input, true, toolExecutor != null, null);
            String streamBody = postStream(config, responsesUrl(config.effectiveBaseUrl()), body);
            lastRaw = streamBody;

            StreamRound round = parseStream(streamBody, totalContent, listener);
            if (round.error != null && !round.error.isBlank()) {
                throw new IllegalStateException(round.error);
            }
            JsonObject response = round.completedResponse;
            if (response == null) {
                return new AiClient.ChatResult(totalContent.toString(), "", lastRaw);
            }

            List<ResponseToolCall> responseToolCalls = extractToolCalls(response);
            List<ResponseToolCall> streamedToolCalls = round.toolCalls();
            boolean useStreamed = responseToolCalls.stream().anyMatch(call -> call.arguments().isBlank())
                    && !streamedToolCalls.isEmpty();
            List<ResponseToolCall> toolCalls = useStreamed
                    ? streamedToolCalls
                    : (!responseToolCalls.isEmpty() ? responseToolCalls : streamedToolCalls);

            if (toolExecutor == null || toolCalls.isEmpty()) {
                String finalContent = totalContent.isEmpty() ? extractContent(response) : totalContent.toString();
                return new AiClient.ChatResult(finalContent, "", lastRaw);
            }
            if (depth == MAX_TOOL_ROUNDS) {
                return new AiClient.ChatResult("[Error] Tool call loop exceeded max depth", "", lastRaw);
            }

            if (useStreamed) {
                for (ResponseToolCall toolCall : toolCalls) {
                    input.add(toolCall.item().deepCopy());
                }
            } else {
                addOutputItems(input, response, round.outputItems.values());
            }
            int toolRound = depth + 1;
            for (ResponseToolCall toolCall : toolCalls) {
                if (listener != null) listener.onToolCall(toolCall.name());
                input.add(toolResult(toolCall, toolExecutor.execute(toolCall.name(), toolCall.arguments(), toolRound)));
            }
        }
        return new AiClient.ChatResult(totalContent.toString(), "", lastRaw);
    }

    private JsonObject baseBody(AiConfig config, String instructions, JsonArray input,
                                boolean stream, boolean withTools, String forcedText) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.effectiveModelId());
        if (instructions != null && !instructions.isBlank()) body.addProperty("instructions", instructions);
        body.add("input", input);
        if (stream) body.addProperty("stream", true);
        if (withTools) {
            body.add("tools", ToolDefinitions.openAiResponseTools());
            body.addProperty("tool_choice", "auto");
            body.addProperty("parallel_tool_calls", true);
        }
        addReasoning(body, config);
        if (forcedText != null) body.addProperty("text", forcedText);
        return body;
    }

    private JsonObject postJson(AiConfig config, String url, JsonObject body) throws Exception {
        HttpRequest request = requestBuilder(config, url)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + trim(response.body()));
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private String postStream(AiConfig config, String url, JsonObject body) throws Exception {
        HttpRequest request = requestBuilder(config, url)
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + trim(response.body()));
        }
        return response.body();
    }

    private HttpRequest.Builder requestBuilder(AiConfig config, String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(CHAT_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.effectiveApiKey());
    }

    private JsonArray buildInput(List<String> messageJsonObjects) {
        JsonArray input = new JsonArray();
        for (String raw : messageJsonObjects) {
            JsonObject original = JsonParser.parseString(raw).getAsJsonObject();
            String role = original.has("role") && !original.get("role").isJsonNull()
                    ? original.get("role").getAsString()
                    : "";
            if ("system".equals(role)) continue;
            input.add(message("assistant".equals(role) ? "assistant" : "user",
                    original.has("content") ? original.get("content") : JsonParser.parseString("\"\"")));
        }
        return input;
    }

    private JsonObject message(String role, String content) {
        return message(role, JsonParser.parseString("\"" + escapeJson(content) + "\""));
    }

    private JsonObject message(String role, JsonElement content) {
        JsonObject item = new JsonObject();
        item.addProperty("role", role);
        item.add("content", content);
        return item;
    }

    private String extractContent(JsonObject response) {
        JsonElement outputText = response.get("output_text");
        if (outputText != null && !outputText.isJsonNull()) {
            if (outputText.isJsonPrimitive()) return outputText.getAsString();
            if (outputText.isJsonArray()) {
                StringBuilder text = new StringBuilder();
                for (JsonElement part : outputText.getAsJsonArray()) {
                    if (!part.isJsonNull()) text.append(part.getAsString());
                }
                return text.toString();
            }
            return outputText.toString();
        }
        JsonArray output = response.getAsJsonArray("output");
        if (output == null) return "";
        StringBuilder text = new StringBuilder();
        for (JsonElement itemElement : output) {
            JsonObject item = itemElement.getAsJsonObject();
            if (!"message".equals(string(item, "type"))) continue;
            JsonArray content = item.getAsJsonArray("content");
            if (content == null) continue;
            for (JsonElement blockElement : content) {
                JsonObject block = blockElement.getAsJsonObject();
                String type = string(block, "type");
                if ("output_text".equals(type) || "text".equals(type)) {
                    text.append(string(block, "text"));
                }
            }
        }
        return text.toString();
    }

    private List<ResponseToolCall> extractToolCalls(JsonObject response) {
        JsonArray output = response.getAsJsonArray("output");
        if (output == null) return List.of();
        java.util.ArrayList<ResponseToolCall> calls = new java.util.ArrayList<>();
        for (JsonElement itemElement : output) {
            JsonObject item = itemElement.getAsJsonObject();
            if (!"function_call".equals(string(item, "type"))) continue;
            String callId = !string(item, "call_id").isBlank() ? string(item, "call_id") : string(item, "id");
            calls.add(new ResponseToolCall(callId, string(item, "name"), string(item, "arguments"), item));
        }
        return calls;
    }

    private void addOutputItems(JsonArray input, JsonObject response, Collection<JsonObject> fallbackOutputItems) {
        JsonArray output = response.getAsJsonArray("output");
        if (output != null && !output.isEmpty()) {
            for (JsonElement item : output) input.add(item.deepCopy());
            return;
        }
        for (JsonObject item : fallbackOutputItems) input.add(item.deepCopy());
    }

    private JsonObject toolResult(ResponseToolCall toolCall, String output) {
        JsonObject item = new JsonObject();
        item.addProperty("type", "function_call_output");
        item.addProperty("call_id", toolCall.callId());
        item.addProperty("output", output);
        return item;
    }

    private StreamRound parseStream(String body, StringBuilder totalContent, AiClient.StreamListener listener) throws IOException {
        StreamRound round = new StreamRound();
        try (BufferedReader reader = new BufferedReader(new StringReader(body))) {
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    consumeEvent(data, round, totalContent, listener);
                    data.setLength(0);
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).trim()).append('\n');
                }
            }
            consumeEvent(data, round, totalContent, listener);
        }
        return round;
    }

    private void consumeEvent(StringBuilder data, StreamRound round, StringBuilder totalContent,
                              AiClient.StreamListener listener) {
        if (data.isEmpty()) return;
        String payload = data.toString().trim();
        if (payload.isBlank() || "[DONE]".equals(payload)) return;
        JsonObject event;
        try {
            event = JsonParser.parseString(payload).getAsJsonObject();
        } catch (Exception ignored) {
            return;
        }
        int outputIndex = event.has("output_index") && !event.get("output_index").isJsonNull()
                ? event.get("output_index").getAsInt()
                : round.outputItems.size();
        switch (string(event, "type")) {
            case "response.output_item.added", "response.output_item.done" -> {
                JsonObject item = event.getAsJsonObject("item");
                if (item == null) return;
                JsonObject copy = item.deepCopy();
                round.outputItems.put(outputIndex, copy);
                if ("function_call".equals(string(copy, "type"))) {
                    round.toolBuilders.computeIfAbsent(outputIndex, key -> new ResponseToolCallBuilder()).mergeItem(copy);
                }
            }
            case "response.output_text.delta" -> {
                String delta = string(event, "delta");
                if (!delta.isEmpty()) {
                    totalContent.append(delta);
                    if (listener != null) listener.onContentDelta(delta);
                }
            }
            case "response.function_call_arguments.delta" -> {
                String delta = string(event, "delta");
                if (!delta.isEmpty()) {
                    round.toolBuilders.computeIfAbsent(outputIndex, key -> new ResponseToolCallBuilder()).arguments.append(delta);
                }
            }
            case "response.function_call_arguments.done" -> {
                ResponseToolCallBuilder builder = round.toolBuilders.computeIfAbsent(outputIndex, key -> new ResponseToolCallBuilder());
                builder.itemId = string(event, "item_id");
                builder.name = string(event, "name");
                builder.setArguments(string(event, "arguments"));
                JsonObject item = round.outputItems.computeIfAbsent(outputIndex, key -> new JsonObject());
                if (!item.has("type")) item.addProperty("type", "function_call");
                if (!builder.itemId.isBlank() && !item.has("id")) item.addProperty("id", builder.itemId);
                if (!builder.name.isBlank()) item.addProperty("name", builder.name);
                item.addProperty("arguments", builder.arguments.toString());
            }
            case "response.completed" -> round.completedResponse = event.getAsJsonObject("response");
            case "response.failed", "response.incomplete" -> {
                JsonObject response = event.getAsJsonObject("response");
                if (response != null && response.has("error") && response.get("error").isJsonObject()) {
                    round.error = string(response.getAsJsonObject("error"), "message");
                } else if (response != null && response.has("incomplete_details") && response.get("incomplete_details").isJsonObject()) {
                    round.error = string(response.getAsJsonObject("incomplete_details"), "reason");
                }
            }
            case "error" -> {
                round.error = string(event, "message");
                if (round.error.isBlank() && event.has("error") && event.get("error").isJsonObject()) {
                    round.error = string(event.getAsJsonObject("error"), "message");
                }
            }
            default -> {
            }
        }
    }

    private void addReasoning(JsonObject body, AiConfig config) {
        if (!config.effectiveModelId().startsWith("gpt-5")) return;
        JsonObject reasoning = new JsonObject();
        reasoning.addProperty("effort", openAiResponseEffort(config.resolveOfficialThinkingEffort()));
        body.add("reasoning", reasoning);
    }

    private String openAiResponseEffort(AiConfig.ThinkingEffort effort) {
        return switch (effort) {
            case NONE -> "minimal";
            case LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH, MAX -> "high";
        };
    }

    private String responsesUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        return normalized.endsWith("/responses") ? normalized : normalized + "/responses";
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static String trim(String value) {
        if (value == null) return "";
        return value.length() > 600 ? value.substring(0, 600) + "..." : value;
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private record ResponseToolCall(String callId, String name, String arguments, JsonObject item) {
    }

    private static final class StreamRound {
        final Map<Integer, JsonObject> outputItems = new LinkedHashMap<>();
        final Map<Integer, ResponseToolCallBuilder> toolBuilders = new LinkedHashMap<>();
        JsonObject completedResponse;
        String error;

        List<ResponseToolCall> toolCalls() {
            java.util.ArrayList<ResponseToolCall> calls = new java.util.ArrayList<>();
            for (ResponseToolCallBuilder builder : toolBuilders.values()) {
                ResponseToolCall call = builder.toToolCall();
                if (call != null) calls.add(call);
            }
            return calls;
        }
    }

    private static final class ResponseToolCallBuilder {
        String itemId = "";
        String callId = "";
        String name = "";
        StringBuilder arguments = new StringBuilder();
        JsonObject item = new JsonObject();

        void mergeItem(JsonObject value) {
            item = value;
            itemId = string(value, "id");
            callId = string(value, "call_id");
            name = string(value, "name");
            setArguments(string(value, "arguments"));
        }

        void setArguments(String value) {
            if (value == null || value.isBlank()) return;
            arguments.setLength(0);
            arguments.append(value);
        }

        ResponseToolCall toToolCall() {
            if (name.isBlank()) return null;
            JsonObject normalized = item.deepCopy();
            if (!normalized.has("type")) normalized.addProperty("type", "function_call");
            if (!itemId.isBlank() && !normalized.has("id")) normalized.addProperty("id", itemId);
            if (!callId.isBlank() && !normalized.has("call_id")) normalized.addProperty("call_id", callId);
            normalized.addProperty("name", name);
            normalized.addProperty("arguments", arguments.toString());
            String resolvedCallId = callId.isBlank() ? itemId : callId;
            return new ResponseToolCall(resolvedCallId, name, arguments.isEmpty() ? "{}" : arguments.toString(), normalized);
        }
    }
}
