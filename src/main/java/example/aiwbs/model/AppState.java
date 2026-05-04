package example.aiwbs.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AppState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<Book> books = new ArrayList<>();
    private final AiConfig aiConfig = new AiConfig();

    public List<Book> getBooks() {
        return books;
    }

    public AiConfig getAiConfig() {
        return aiConfig;
    }
}
