package example.aiwbs.ui;

import example.aiwbs.model.AppState;
import example.aiwbs.model.Book;
import example.aiwbs.storage.StateStore;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.io.File;
import java.util.function.Consumer;

/**
 * 选书轮播界面。无侧栏，默认显示。
 */
public class BookCarouselView {
    private final BorderPane root = new BorderPane();
    private final AppState state;
    private final StateStore store;
    private int selectedBookIndex;
    private final Consumer<Integer> onEnterBook;
    private final Runnable onAddBook;

    public BookCarouselView(AppState state, StateStore store, int selectedBookIndex,
                            Consumer<Integer> onEnterBook, Runnable onAddBook) {
        this.state = state;
        this.store = store;
        this.selectedBookIndex = selectedBookIndex;
        this.onEnterBook = onEnterBook;
        this.onAddBook = onAddBook;
        buildPage();
    }

    public Parent getRoot() {
        return root;
    }

    public int getSelectedBookIndex() {
        return selectedBookIndex;
    }

    private void buildPage() {
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #101a3d, #16254d);");
        VBox page = new VBox(28);
        page.setPadding(new javafx.geometry.Insets(28, 42, 34, 42));
        page.setAlignment(Pos.TOP_CENTER);
        page.getChildren().addAll(buildHeader(), buildCarousel(), buildActions());
        root.setCenter(page);
    }

    private Parent buildHeader() {
        VBox header = new VBox(10);
        header.setAlignment(Pos.CENTER);
        Label eyebrow = new Label("NOVEL CHAPTER MANAGEMENT");
        eyebrow.setStyle("-fx-text-fill: #d7bb74; -fx-font-size: 14px;");
        Label title = new Label("Select a Book");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 34px; -fx-font-weight: bold;");
        header.getChildren().addAll(eyebrow, title);
        return header;
    }

    private Parent buildCarousel() {
        BorderPane shell = new BorderPane();
        shell.setMaxWidth(1320);
        shell.setPrefHeight(560);

        Button previous = navButton("<");
        Button next = navButton(">");
        previous.setOnAction(e -> moveSelection(-1));
        next.setOnAction(e -> moveSelection(1));

        HBox cards = new HBox(-56);
        cards.setAlignment(Pos.CENTER);
        if (state.getBooks().isEmpty()) {
            Label empty = new Label("No books yet");
            empty.setStyle("-fx-text-fill: white; -fx-font-size: 20px;");
            StackPane center = new StackPane(empty);
            center.setMinHeight(460);
            shell.setCenter(center);
        } else {
            int prevIdx = selectedBookIndex - 1;
            int nextIdx = selectedBookIndex + 1;
            if (prevIdx >= 0) {
                cards.getChildren().add(buildBookCard(state.getBooks().get(prevIdx), prevIdx, false));
            }
            cards.getChildren().add(buildBookCard(state.getBooks().get(selectedBookIndex), selectedBookIndex, true));
            if (nextIdx < state.getBooks().size()) {
                cards.getChildren().add(buildBookCard(state.getBooks().get(nextIdx), nextIdx, false));
            }
            shell.setCenter(cards);
        }

        shell.setLeft(previous);
        shell.setRight(next);
        BorderPane.setAlignment(previous, Pos.CENTER);
        BorderPane.setAlignment(next, Pos.CENTER);
        return shell;
    }

    private Parent buildBookCard(Book book, int index, boolean selected) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(300);
        card.setPadding(new javafx.geometry.Insets(12));

        ImageView cover = new ImageView(loadCover(book));
        cover.setFitWidth(selected ? 250 : 220);
        cover.setFitHeight(selected ? 334 : 294);
        cover.setPreserveRatio(false);

        StackPane frame = new StackPane(cover);
        frame.setStyle("-fx-background-radius: 16; -fx-background-color: rgba(255,255,255,0.08);");

        Label title = new Label(book.getName());
        title.setWrapText(true);
        title.setMaxWidth(240);
        title.setStyle("-fx-text-fill: white; -fx-font-size: 17px; -fx-font-weight: bold;");

        card.getChildren().addAll(frame, title);
        card.setOpacity(selected ? 1.0 : 0.66);
        card.setTranslateY(selected ? -18 : 28);
        card.setViewOrder(selected ? -1 : 0);
        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                selectedBookIndex = index;
                buildPage();
            }
        });
        return card;
    }

    private Button navButton(String text) {
        Button button = new Button(text);
        button.setPrefSize(58, 58);
        button.setStyle("-fx-background-radius: 999; -fx-border-radius: 999; -fx-border-color: rgba(255,255,255,0.22); -fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: white; -fx-font-size: 24px;");
        return button;
    }

    private void moveSelection(int step) {
        if (state.getBooks().isEmpty()) return;
        selectedBookIndex = Math.max(0, Math.min(state.getBooks().size() - 1, selectedBookIndex + step));
        buildPage();
    }

    private Parent buildActions() {
        VBox actions = new VBox(12);
        actions.setAlignment(Pos.CENTER);

        Label name = new Label(selectedBookIndex >= 0 ? state.getBooks().get(selectedBookIndex).getName() : "");
        name.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");

        Button enter = new Button("Enter Chapter Management");
        enter.getStyleClass().add("primary-action");
        enter.setDisable(selectedBookIndex < 0);
        enter.setOnAction(e -> onEnterBook.accept(selectedBookIndex));
        enter.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 12 48;");

        Button add = new Button("Add Book");
        add.setOnAction(e -> onAddBook.run());

        actions.getChildren().addAll(name, enter, add);
        return actions;
    }

    private Image loadCover(Book book) {
        try {
            if (book.getCoverPath() != null && !book.getCoverPath().isBlank()) {
                return new Image(new File(book.getCoverPath()).toURI().toString(), 250, 334, false, true);
            }
        } catch (Exception ignored) {
        }
        return createDefaultCover();
    }

    private Image createDefaultCover() {
        WritableImage image = new WritableImage(250, 334);
        var writer = image.getPixelWriter();
        for (int y = 0; y < 334; y++) {
            for (int x = 0; x < 250; x++) {
                boolean edge = x < 4 || y < 4 || x > 245 || y > 329;
                writer.setColor(x, y, edge ? Color.web("#6d7a8a") : Color.web("#d9dee7"));
            }
        }
        return image;
    }
}
