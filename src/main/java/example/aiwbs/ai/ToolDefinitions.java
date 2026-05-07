package example.aiwbs.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ToolDefinitions {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static String cached;

    public static String getToolsJson() {
        if (cached != null) return cached;
        JsonArray tools = new JsonArray();
        tools.add(func("get_table_of_contents",
                "获取当前书籍的完整目录：所有分卷名称、章节名称及每章字数。"));
        tools.add(func("get_outline_tree",
                "获取当前书籍的大纲树：各节点标题及内容（每条最多 100 字摘要）。大纲是书籍的骨架情节结构。"));
        tools.add(func("get_volume_outline",
                "获取指定分卷的细纲内容。",
                obj("type", "object", "properties", obj(
                        "vol_index", obj("type", "integer", "description", "卷号，从 1 开始")),
                   "required", arr("vol_index"))));
        tools.add(func("get_chapter_outline",
                "获取指定章节的细纲内容。",
                obj("type", "object", "properties", obj(
                        "vol_index", obj("type", "integer", "description", "卷号，从 1 开始"),
                        "ch_index", obj("type", "integer", "description", "章号，从 1 开始")),
                   "required", arr("vol_index", "ch_index"))));
        tools.add(func("get_chapter_content",
                "获取指定章节的完整正文内容。注意：正文可能较长，请先通过 get_chapter_outline 了解章节内容。",
                obj("type", "object", "properties", obj(
                        "vol_index", obj("type", "integer", "description", "卷号，从 1 开始"),
                        "ch_index", obj("type", "integer", "description", "章号，从 1 开始")),
                   "required", arr("vol_index", "ch_index"))));
        cached = GSON.toJson(tools);
        return cached;
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
