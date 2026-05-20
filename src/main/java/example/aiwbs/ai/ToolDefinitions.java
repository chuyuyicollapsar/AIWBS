package example.aiwbs.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.List;

public class ToolDefinitions {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static String cached;
    private static List<ToolSpecification> cachedLangChainTools;

    public static String getToolsJson() {
        if (cached != null) return cached;
        JsonArray tools = new JsonArray();
        tools.add(func("get_table_of_contents",
                "获取当前书籍的完整目录：所有分卷名称、章节名称及每章字数。"));
        tools.add(func("get_outline_tree",
                "获取当前书籍的大纲树（JSON，children 表示子节点）。注意：每个节点的 content 字段是纯文本描述，其中可能包含它自己的子标题（如 ## 背景设定、## 人物关系），这些子标题属于该节点的内容，并非大纲树的子节点。父子关系仅由 children 数组表达。"));
        tools.add(func("get_volume_outline",
                "获取指定分卷的细纲内容。",
                obj("type", "object", "properties", obj(
                        "volume_name", obj("type", "string", "description", "分卷名，必须使用目录中的完整分卷名称")),
                   "required", arr("volume_name"))));
        tools.add(func("get_chapter_outline",
                "获取指定章节的细纲内容。",
                obj("type", "object", "properties", obj(
                        "volume_name", obj("type", "string", "description", "分卷名，必须使用目录中的完整分卷名称"),
                        "chapter_name", obj("type", "string", "description", "章节名，必须使用目录中的完整章节名称")),
                   "required", arr("volume_name", "chapter_name"))));
        tools.add(func("search_chapter_content",
                "Search all chapter prose/body text for a keyword or phrase. Returns matching volume and chapter names plus short snippets. Use get_chapter_content afterward when full text is needed.",
                obj("type", "object", "properties", obj(
                        "query", obj("type", "string", "description", "Keyword or phrase to search for in chapter prose/body text.")),
                   "required", arr("query"))));
        tools.add(func("get_chapter_content",
                "获取指定章节的完整正文内容。注意：正文可能较长，请先通过 get_chapter_outline 了解章节内容。",
                obj("type", "object", "properties", obj(
                        "volume_name", obj("type", "string", "description", "分卷名，必须使用目录中的完整分卷名称"),
                        "chapter_name", obj("type", "string", "description", "章节名，必须使用目录中的完整章节名称")),
                   "required", arr("volume_name", "chapter_name"))));
        cached = GSON.toJson(tools);
        return cached;
    }

    public static List<ToolSpecification> langChainTools() {
        if (cachedLangChainTools != null) return cachedLangChainTools;
        cachedLangChainTools = List.of(
                tool(
                        "get_table_of_contents",
                        "Get the current book table of contents: all volume names, chapter names, and chapter word counts.",
                        JsonObjectSchema.builder().build()
                ),
                tool(
                        "get_outline_tree",
                        "Get the current book outline tree as JSON. The children array is the only source of parent-child structure; headings inside content are plain text.",
                        JsonObjectSchema.builder().build()
                ),
                tool(
                        "get_volume_outline",
                        "Get the detailed outline for a specific volume.",
                        JsonObjectSchema.builder()
                                .addStringProperty("volume_name", "Volume name. Use the exact full name from the table of contents.")
                                .required("volume_name")
                                .build()
                ),
                tool(
                        "get_chapter_outline",
                        "Get the detailed outline for a specific chapter.",
                        JsonObjectSchema.builder()
                                .addStringProperty("volume_name", "Volume name. Use the exact full name from the table of contents.")
                                .addStringProperty("chapter_name", "Chapter name. Use the exact full name from the table of contents.")
                                .required("volume_name", "chapter_name")
                                .build()
                ),
                tool(
                        "get_chapter_content",
                        "Get the full prose content for a specific chapter. Content may be long; inspect the chapter outline first when possible.",
                        JsonObjectSchema.builder()
                                .addStringProperty("volume_name", "Volume name. Use the exact full name from the table of contents.")
                                .addStringProperty("chapter_name", "Chapter name. Use the exact full name from the table of contents.")
                                .required("volume_name", "chapter_name")
                                .build()
                ),
                tool(
                        "search_chapter_content",
                        "Search all chapter prose/body text for a keyword or phrase. Returns matching volume and chapter names plus short snippets. Use get_chapter_content afterward when full text is needed.",
                        JsonObjectSchema.builder()
                                .addStringProperty("query", "Keyword or phrase to search for in chapter prose/body text.")
                                .required("query")
                                .build()
                )
        );
        return cachedLangChainTools;
    }

    public static JsonArray openAiResponseTools() {
        JsonArray tools = new JsonArray();
        tools.add(responseTool("get_table_of_contents",
                "Get the current book table of contents: all volume names, chapter names, and chapter word counts.",
                obj("type", "object", "properties", new JsonObject(), "required", new JsonArray())));
        tools.add(responseTool("get_outline_tree",
                "Get the current book outline tree as JSON. The children array is the only source of parent-child structure; headings inside content are plain text.",
                obj("type", "object", "properties", new JsonObject(), "required", new JsonArray())));
        tools.add(responseTool("get_volume_outline",
                "Get the detailed outline for a specific volume.",
                obj("type", "object", "properties", obj(
                        "volume_name", obj("type", "string", "description", "Volume name. Use the exact full name from the table of contents.")),
                   "required", arr("volume_name"))));
        tools.add(responseTool("get_chapter_outline",
                "Get the detailed outline for a specific chapter.",
                obj("type", "object", "properties", obj(
                        "volume_name", obj("type", "string", "description", "Volume name. Use the exact full name from the table of contents."),
                        "chapter_name", obj("type", "string", "description", "Chapter name. Use the exact full name from the table of contents.")),
                   "required", arr("volume_name", "chapter_name"))));
        tools.add(responseTool("search_chapter_content",
                "Search all chapter prose/body text for a keyword or phrase. Returns matching volume and chapter names plus short snippets. Use get_chapter_content afterward when full text is needed.",
                obj("type", "object", "properties", obj(
                        "query", obj("type", "string", "description", "Keyword or phrase to search for in chapter prose/body text.")),
                   "required", arr("query"))));
        tools.add(responseTool("get_chapter_content",
                "Get the full prose content for a specific chapter. Content may be long; inspect the chapter outline first when possible.",
                obj("type", "object", "properties", obj(
                        "volume_name", obj("type", "string", "description", "Volume name. Use the exact full name from the table of contents."),
                        "chapter_name", obj("type", "string", "description", "Chapter name. Use the exact full name from the table of contents.")),
                   "required", arr("volume_name", "chapter_name"))));
        return tools;
    }

    private static ToolSpecification tool(String name, String description, JsonObjectSchema parameters) {
        return ToolSpecification.builder()
                .name(name)
                .description(description)
                .parameters(parameters)
                .build();
    }

    private static JsonObject func(String name, String description) {
        return func(name, description, obj("type", "object", "properties", new JsonObject(), "required", new JsonArray()));
    }

    private static JsonObject func(String name, String description, JsonObject parameters) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("description", description);
        f.add("parameters", parameters);
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("type", "function");
        wrapper.add("function", f);
        return wrapper;
    }

    private static JsonObject responseTool(String name, String description, JsonObject parameters) {
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.addProperty("name", name);
        tool.addProperty("description", description);
        tool.add("parameters", parameters);
        tool.addProperty("strict", false);
        return tool;
    }

    private static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < kv.length; i += 2) {
            String k = (String) kv[i];
            Object v = kv[i + 1];
            if (v instanceof String s) o.addProperty(k, s);
            else if (v instanceof Number n) o.addProperty(k, n);
            else if (v instanceof Boolean b) o.addProperty(k, b);
            else if (v instanceof JsonElement e) o.add(k, e);
        }
        return o;
    }

    private static JsonArray arr(String... items) {
        JsonArray a = new JsonArray();
        for (String s : items) a.add(s);
        return a;
    }
}
