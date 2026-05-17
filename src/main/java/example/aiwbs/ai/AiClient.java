package example.aiwbs.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import example.aiwbs.model.AiConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class AiClient {
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration CHAT_TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_TOOL_ROUNDS = 10;

    public String testConnection(AiConfig config) throws Exception {
        validate(config);
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a writing assistant."),
                UserMessage.from("Reply with exactly: OK")
        );
        ChatResponse response = chatModel(config, TEST_TIMEOUT).chat(
                ChatRequest.builder()
                        .messages(messages)
                        .temperature(shouldSendTemperature(config) ? 0.2 : null)
                        .build()
        );
        return trim(response.aiMessage().text());
    }

    public String chat(AiConfig config, String systemPrompt, List<String> messageJsonObjects) throws Exception {
        return chatWithResult(config, systemPrompt, messageJsonObjects, null).content();
    }

    public ChatResult chatWithResult(AiConfig config, String systemPrompt, List<String> messageJsonObjects,
                                     String toolsJson) throws Exception {
        return chatWithTools(config, systemPrompt, messageJsonObjects, null);
    }

    /**
     * Kept for older callers. New code should use chatWithTools or streamWithTools so tool calls stay typed.
     */
    public String chatRaw(AiConfig config, String systemPrompt,
                          List<String> messageJsonObjects, String toolsJson) throws Exception {
        ChatResult result = chatWithResult(config, systemPrompt, messageJsonObjects, toolsJson);
        JsonObject root = new JsonObject();
        root.addProperty("content", result.content());
        root.addProperty("reasoning_content", result.reasoningContent());
        return root.toString();
    }

    public ChatResult chatWithTools(AiConfig config, String systemPrompt, List<String> messageJsonObjects,
                                    ToolExecutor toolExecutor) throws Exception {
        validate(config);
        List<ChatMessage> messages = toMessages(systemPrompt, messageJsonObjects);
        ChatModel model = chatModel(config, CHAT_TIMEOUT);
        List<ToolSpecification> tools = toolExecutor == null ? List.of() : ToolDefinitions.langChainTools();

        for (int depth = 0; depth <= MAX_TOOL_ROUNDS; depth++) {
            ChatResponse response = model.chat(request(messages, tools, config));
            AiMessage aiMessage = response.aiMessage();
            if (!aiMessage.hasToolExecutionRequests()) {
                return new ChatResult(safe(aiMessage.text()), safe(aiMessage.thinking()), response.toString());
            }
            if (toolExecutor == null) {
                return new ChatResult(safe(aiMessage.text()), safe(aiMessage.thinking()), response.toString());
            }
            if (depth == MAX_TOOL_ROUNDS) {
                return new ChatResult("[Error] Tool call loop exceeded max depth", "", response.toString());
            }
            messages.add(aiMessage);
            for (ToolExecutionRequest toolCall : aiMessage.toolExecutionRequests()) {
                String result = toolExecutor.execute(toolCall.name(), toolCall.arguments());
                messages.add(ToolExecutionResultMessage.from(toolCall, result));
            }
        }
        return new ChatResult("[Error] Tool call loop exceeded max depth", "", "");
    }

    public ChatResult streamWithTools(AiConfig config, String systemPrompt, List<String> messageJsonObjects,
                                      ToolExecutor toolExecutor, StreamListener listener) throws Exception {
        validate(config);
        List<ChatMessage> messages = toMessages(systemPrompt, messageJsonObjects);
        StreamingChatModel model = streamingChatModel(config, CHAT_TIMEOUT);
        List<ToolSpecification> tools = toolExecutor == null ? List.of() : ToolDefinitions.langChainTools();

        StringBuilder content = new StringBuilder();
        StringBuilder thinking = new StringBuilder();

        for (int depth = 0; depth <= MAX_TOOL_ROUNDS; depth++) {
            CompletableFuture<ChatResponse> future = new CompletableFuture<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            model.chat(request(messages, tools, config), new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (partialResponse == null || partialResponse.isEmpty()) return;
                    content.append(partialResponse);
                    if (listener != null) listener.onContentDelta(partialResponse);
                }

                @Override
                public void onPartialThinking(PartialThinking partialThinking) {
                    if (partialThinking == null || partialThinking.text() == null || partialThinking.text().isEmpty()) return;
                    thinking.append(partialThinking.text());
                    if (listener != null) listener.onReasoningDelta(partialThinking.text());
                }

                @Override
                public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                    if (listener != null && completeToolCall != null && completeToolCall.toolExecutionRequest() != null) {
                        listener.onToolCall(completeToolCall.toolExecutionRequest().name());
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    future.complete(response);
                }

                @Override
                public void onError(Throwable error) {
                    failure.set(error);
                    future.completeExceptionally(error);
                }
            });

            ChatResponse response;
            try {
                response = future.join();
            } catch (Exception e) {
                Throwable cause = failure.get();
                if (cause instanceof Exception ex) throw ex;
                throw e;
            }

            AiMessage aiMessage = response.aiMessage();
            if (!aiMessage.hasToolExecutionRequests()) {
                String finalContent = safe(aiMessage.text());
                String finalThinking = safe(aiMessage.thinking());
                if (!finalContent.isBlank() && !finalContent.equals(content.toString())) {
                    content.setLength(0);
                    content.append(finalContent);
                    if (listener != null) listener.onContentReplace(finalContent);
                }
                if (!finalThinking.isBlank() && !finalThinking.equals(thinking.toString())) {
                    thinking.setLength(0);
                    thinking.append(finalThinking);
                    if (listener != null) listener.onReasoningReplace(finalThinking);
                }
                return new ChatResult(content.toString(), thinking.toString(), response.toString());
            }
            if (toolExecutor == null) {
                return new ChatResult(content.toString(), thinking.toString(), response.toString());
            }
            if (depth == MAX_TOOL_ROUNDS) {
                return new ChatResult("[Error] Tool call loop exceeded max depth", "", response.toString());
            }

            messages.add(aiMessage);
            for (ToolExecutionRequest toolCall : aiMessage.toolExecutionRequests()) {
                String result = toolExecutor.execute(toolCall.name(), toolCall.arguments());
                messages.add(ToolExecutionResultMessage.from(toolCall, result));
            }
        }
        return new ChatResult("[Error] Tool call loop exceeded max depth", "", "");
    }

    private ChatRequest request(List<ChatMessage> messages, List<ToolSpecification> tools, AiConfig config) {
        ChatRequest.Builder builder = ChatRequest.builder()
                .messages(messages)
                .temperature(shouldSendTemperature(config) ? 0.7 : null);
        if (tools != null && !tools.isEmpty()) {
            builder.toolSpecifications(tools);
            builder.toolChoice(ToolChoice.AUTO);
        }
        return builder.build();
    }

    private ChatModel chatModel(AiConfig config, Duration timeout) {
        if (isAnthropicOfficial(config)) {
            AnthropicChatModel.AnthropicChatModelBuilder builder = AnthropicChatModel.builder()
                    .baseUrl(config.effectiveBaseUrl())
                    .apiKey(config.effectiveApiKey())
                    .modelName(config.effectiveModelId())
                    .maxTokens(4096)
                    .timeout(timeout)
                    .returnThinking(true)
                    .sendThinking(true);
            applyAnthropicThinking(builder, config.resolveOfficialThinkingEffort());
            return builder.build();
        }

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(config.effectiveBaseUrl())
                .apiKey(config.effectiveApiKey())
                .modelName(config.effectiveModelId())
                .timeout(timeout)
                .returnThinking(true)
                .sendThinking(true)
                .strictTools(false)
                .parallelToolCalls(false);
        applyOpenAiThinking(builder, config);
        return builder.build();
    }

    private StreamingChatModel streamingChatModel(AiConfig config, Duration timeout) {
        if (isAnthropicOfficial(config)) {
            AnthropicStreamingChatModel.AnthropicStreamingChatModelBuilder builder = AnthropicStreamingChatModel.builder()
                    .baseUrl(config.effectiveBaseUrl())
                    .apiKey(config.effectiveApiKey())
                    .modelName(config.effectiveModelId())
                    .maxTokens(4096)
                    .timeout(timeout)
                    .returnThinking(true)
                    .sendThinking(true);
            applyAnthropicThinking(builder, config.resolveOfficialThinkingEffort());
            return builder.build();
        }

        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .baseUrl(config.effectiveBaseUrl())
                .apiKey(config.effectiveApiKey())
                .modelName(config.effectiveModelId())
                .timeout(timeout)
                .returnThinking(true)
                .sendThinking(true)
                .strictTools(false)
                .parallelToolCalls(false)
                .accumulateToolCallId(true);
        applyOpenAiThinking(builder, config);
        return builder.build();
    }

    private void applyOpenAiThinking(OpenAiChatModel.OpenAiChatModelBuilder builder, AiConfig config) {
        if (!config.isUseOfficialApi()) return;
        AiConfig.ThinkingEffort effort = config.resolveOfficialThinkingEffort();
        switch (config.resolveOfficialProvider()) {
            case OPENAI -> {
                if (config.effectiveModelId().startsWith("gpt-5")) {
                    builder.reasoningEffort(openAiReasoningEffort(effort));
                }
            }
            case GEMINI -> {
                if (config.effectiveModelId().startsWith("gemini-")) {
                    builder.reasoningEffort(geminiReasoningEffort(effort, config.effectiveModelId()));
                }
            }
            case DEEPSEEK -> applyDeepSeekThinking(builder, effort);
            case ANTHROPIC -> {
            }
        }
    }

    private void applyOpenAiThinking(OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder, AiConfig config) {
        if (!config.isUseOfficialApi()) return;
        AiConfig.ThinkingEffort effort = config.resolveOfficialThinkingEffort();
        switch (config.resolveOfficialProvider()) {
            case OPENAI -> {
                if (config.effectiveModelId().startsWith("gpt-5")) {
                    builder.reasoningEffort(openAiReasoningEffort(effort));
                }
            }
            case GEMINI -> {
                if (config.effectiveModelId().startsWith("gemini-")) {
                    builder.reasoningEffort(geminiReasoningEffort(effort, config.effectiveModelId()));
                }
            }
            case DEEPSEEK -> applyDeepSeekThinking(builder, effort);
            case ANTHROPIC -> {
            }
        }
    }

    private void applyDeepSeekThinking(OpenAiChatModel.OpenAiChatModelBuilder builder, AiConfig.ThinkingEffort effort) {
        if (effort == AiConfig.ThinkingEffort.NONE) {
            builder.customParameters(new JsonObjectParameterBuilder().putThinkingType("disabled").build());
        } else {
            builder.reasoningEffort(effort == AiConfig.ThinkingEffort.MAX ? "max" : "high");
            builder.customParameters(new JsonObjectParameterBuilder().putThinkingType("enabled").build());
        }
    }

    private void applyDeepSeekThinking(OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder, AiConfig.ThinkingEffort effort) {
        if (effort == AiConfig.ThinkingEffort.NONE) {
            builder.customParameters(new JsonObjectParameterBuilder().putThinkingType("disabled").build());
        } else {
            builder.reasoningEffort(effort == AiConfig.ThinkingEffort.MAX ? "max" : "high");
            builder.customParameters(new JsonObjectParameterBuilder().putThinkingType("enabled").build());
        }
    }

    private void applyAnthropicThinking(AnthropicChatModel.AnthropicChatModelBuilder builder, AiConfig.ThinkingEffort effort) {
        if (effort == AiConfig.ThinkingEffort.NONE) return;
        builder.thinkingType("enabled");
        builder.thinkingBudgetTokens(thinkingBudgetTokens(effort));
        builder.maxTokens(thinkingBudgetTokens(effort) + 1024);
    }

    private void applyAnthropicThinking(AnthropicStreamingChatModel.AnthropicStreamingChatModelBuilder builder, AiConfig.ThinkingEffort effort) {
        if (effort == AiConfig.ThinkingEffort.NONE) return;
        builder.thinkingType("enabled");
        builder.thinkingBudgetTokens(thinkingBudgetTokens(effort));
        builder.maxTokens(thinkingBudgetTokens(effort) + 1024);
    }

    private List<ChatMessage> toMessages(String systemPrompt, List<String> messageJsonObjects) {
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        for (String raw : messageJsonObjects) {
            JsonObject original = JsonParser.parseString(raw).getAsJsonObject();
            String role = original.get("role") == null ? "" : original.get("role").getAsString();
            String content = original.get("content") == null || original.get("content").isJsonNull()
                    ? ""
                    : original.get("content").getAsString();
            if ("assistant".equals(role)) {
                String reasoning = original.has("reasoning_content") && !original.get("reasoning_content").isJsonNull()
                        ? original.get("reasoning_content").getAsString()
                        : "";
                messages.add(AiMessage.builder().text(content).thinking(reasoning).build());
            } else if ("system".equals(role)) {
                messages.add(SystemMessage.from(content));
            } else {
                messages.add(UserMessage.from(content));
            }
        }
        return messages;
    }

    private boolean isAnthropicOfficial(AiConfig config) {
        return config.isUseOfficialApi() && config.resolveOfficialProvider() == AiConfig.OfficialProvider.ANTHROPIC;
    }

    private boolean shouldSendTemperature(AiConfig config) {
        if (!config.isUseOfficialApi()) return true;
        return config.resolveOfficialProvider() != AiConfig.OfficialProvider.ANTHROPIC
                && config.resolveOfficialProvider() != AiConfig.OfficialProvider.DEEPSEEK;
    }

    private String openAiReasoningEffort(AiConfig.ThinkingEffort effort) {
        return switch (effort) {
            case NONE -> "none";
            case LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH -> "high";
            case MAX -> "xhigh";
        };
    }

    private String geminiReasoningEffort(AiConfig.ThinkingEffort effort, String modelId) {
        return switch (effort) {
            case NONE -> modelId.startsWith("gemini-2.5") && !modelId.contains("pro") ? "none" : "low";
            case LOW -> "low";
            case MEDIUM -> modelId.startsWith("gemini-3") ? "low" : "medium";
            case HIGH, MAX -> "high";
        };
    }

    private int thinkingBudgetTokens(AiConfig.ThinkingEffort effort) {
        return switch (effort) {
            case LOW -> 1024;
            case MEDIUM -> 2048;
            case HIGH -> 4096;
            case MAX -> 8192;
            case NONE -> 0;
        };
    }

    private void validate(AiConfig config) {
        validateNotBlank(config.effectiveApiKey(), "API Key");
        validateNotBlank(config.effectiveModelId(), "Model ID");
        validateNotBlank(config.effectiveBaseUrl(), "Base URL");
    }

    private void validateNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }

    private String trim(String value) {
        if (value == null) return "";
        return value.length() > 600 ? value.substring(0, 600) + "..." : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record ChatResult(String content, String reasoningContent, String rawJson) {
    }

    @FunctionalInterface
    public interface ToolExecutor {
        String execute(String name, String argsJson);
    }

    public interface StreamListener {
        void onContentDelta(String delta);

        default void onReasoningDelta(String delta) {
        }

        default void onContentReplace(String content) {
        }

        default void onReasoningReplace(String reasoning) {
        }

        default void onToolCall(String toolName) {
        }
    }

    private static final class JsonObjectParameterBuilder {
        private String thinkingType;

        JsonObjectParameterBuilder putThinkingType(String type) {
            thinkingType = type;
            return this;
        }

        java.util.Map<String, Object> build() {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            java.util.Map<String, Object> thinking = new java.util.HashMap<>();
            thinking.put("type", thinkingType);
            map.put("thinking", thinking);
            return map;
        }
    }
}
