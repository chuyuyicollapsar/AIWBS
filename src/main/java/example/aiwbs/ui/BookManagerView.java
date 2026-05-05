package example.aiwbs.ui;

import example.aiwbs.model.AppState;
import example.aiwbs.model.Book;
import example.aiwbs.model.Chapter;
import example.aiwbs.model.ChapterVersion;
import example.aiwbs.model.OutlineNode;
import example.aiwbs.model.Volume;
import example.aiwbs.storage.StateStore;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class BookManagerView {
    private final AppState state;
    private final StateStore store;
    private final BorderPane root = new BorderPane();
    private TextArea editor;
    private final BooleanProperty inSelectionPage = new SimpleBooleanProperty(true);

    private Book selectedBook;
    private Volume selectedVolume;
    private Chapter selectedChapter;
    private int selectedBookIndex;
    private ContextMenu displayedMenu;
    private boolean volumeSidebarCollapsed = true;
    private boolean chapterSidebarCollapsed;
    private boolean historySidebarVisible;
    private double volumeSidebarWidth = 240;
    private double chapterSidebarWidth = 260;
    private boolean outlineVisible;
    private boolean volChapOutlineMode;
    private boolean outlineSidebarCollapsed;
    private OutlineNode selectedOutlineNode;
    private TreeView<OutlineNode> outlineTreeView;
    private boolean settingOutlineSelection;
    private Consumer<Boolean> pageStateListener;

    public BookManagerView(AppState state, StateStore store) {
        this.state = state;
        this.store = store;
        selectedBookIndex = state.getBooks().isEmpty() ? -1 : 0;
        showSelectionPage();
    }

    public Parent getRoot() {
        return root;
    }

    public void showSelectionPage() {
        inSelectionPage.set(true);
        notifyPageState();
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #101a3d, #16254d);");
        VBox page = new VBox(28);
        page.setPadding(new Insets(28, 42, 34, 42));
        page.setAlignment(Pos.TOP_CENTER);
        page.getChildren().addAll(buildSelectionHeader(), buildCarousel(), buildSelectionActions());
        root.setCenter(page);
    }

    public void enterSelectedBook() {
        if (selectedBookIndex < 0 || selectedBookIndex >= state.getBooks().size()) {
            return;
        }
        selectedBook = state.getBooks().get(selectedBookIndex);
        selectedVolume = selectedBook.getVolumes().isEmpty() ? null : selectedBook.getVolumes().get(0);
        selectedChapter = selectedVolume == null || selectedVolume.getChapters().isEmpty() ? null : selectedVolume.getChapters().get(0);
        showWorkspace();
    }

    public void toggleVolumeSidebar() {
        volumeSidebarCollapsed = !volumeSidebarCollapsed;
        if (!inSelectionPage.get()) {
            showWorkspace();
        }
    }

    public void toggleChapterSidebar() {
        chapterSidebarCollapsed = !chapterSidebarCollapsed;
        if (!inSelectionPage.get()) {
            showWorkspace();
        }
    }

    public boolean isVolumeSidebarCollapsed() {
        return volumeSidebarCollapsed;
    }

    public boolean isChapterSidebarCollapsed() {
        return chapterSidebarCollapsed;
    }

    public void toggleOutline() {
        outlineVisible = !outlineVisible;
        if (!inSelectionPage.get()) {
            showWorkspace();
        }
    }

    public void setOutlineMode(boolean isVolChap) {
        outlineVisible = true;
        volChapOutlineMode = isVolChap;
        if (!inSelectionPage.get()) {
            showWorkspace();
        }
    }

    public void exitOutline() {
        outlineVisible = false;
        if (!inSelectionPage.get()) {
            showWorkspace();
        }
    }

    public boolean isOutlineVisible() {
        return outlineVisible;
    }

    public boolean isVolChapOutlineMode() {
        return volChapOutlineMode;
    }

    public void toggleOutlineSidebar() {
        outlineSidebarCollapsed = !outlineSidebarCollapsed;
        if (!inSelectionPage.get()) {
            showWorkspace();
        }
    }

    public boolean isOutlineSidebarCollapsed() {
        return outlineSidebarCollapsed;
    }

    public boolean hasSelection() {
        return selectedBook != null;
    }

    public void showBookMenu(Button anchor) {
        if (!inSelectionPage.get() && selectedBook != null) {
            buildBookMenu().show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
        }
    }

    public BooleanProperty inSelectionPageProperty() {
        return inSelectionPage;
    }

    public void setPageStateListener(Consumer<Boolean> pageStateListener) {
        this.pageStateListener = pageStateListener;
    }

    private Parent buildSelectionHeader() {
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
            int previousIndex = selectedBookIndex - 1;
            int nextIndex = selectedBookIndex + 1;
            if (previousIndex >= 0) {
                cards.getChildren().add(buildBookCard(state.getBooks().get(previousIndex), previousIndex, false));
            }
            cards.getChildren().add(buildBookCard(state.getBooks().get(selectedBookIndex), selectedBookIndex, true));
            if (nextIndex < state.getBooks().size()) {
                cards.getChildren().add(buildBookCard(state.getBooks().get(nextIndex), nextIndex, false));
            }
            shell.setCenter(cards);
        }

        shell.setLeft(previous);
        shell.setRight(next);
        BorderPane.setAlignment(previous, Pos.CENTER);
        BorderPane.setAlignment(next, Pos.CENTER);
        return shell;
    }

    private Parent buildSelectionActions() {
        VBox actions = new VBox(12);
        actions.setAlignment(Pos.CENTER);

        Label name = new Label(selectedBookIndex >= 0 ? state.getBooks().get(selectedBookIndex).getName() : "");
        name.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");

        Button enter = new Button("Enter Chapter Management");
        enter.getStyleClass().add("primary-action");
        enter.setDisable(selectedBookIndex < 0);
        enter.setOnAction(e -> enterSelectedBook());
        enter.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 12 48;");

        Button add = new Button("Add Book");
        add.setOnAction(e -> addBook());

        actions.getChildren().addAll(name, enter, add);
        return actions;
    }

    private Parent buildBookCard(Book book, int index, boolean selected) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(300);
        card.setPadding(new Insets(12));

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
                showSelectionPage();
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
        if (state.getBooks().isEmpty()) {
            return;
        }
        selectedBookIndex = Math.max(0, Math.min(state.getBooks().size() - 1, selectedBookIndex + step));
        showSelectionPage();
    }

    private void showWorkspace() {
        inSelectionPage.set(false);
        notifyPageState();
        root.setStyle("-fx-background-color: #0f1730;");
        BorderPane page = new BorderPane();
        page.setPadding(new Insets(12));
        if (outlineVisible) {
            if (volChapOutlineMode) {
                BorderPane right = new BorderPane();
                right.setLeft(buildSidebars());
                right.setCenter(buildVolChapOutlineEditor());
                page.setCenter(right);
            } else {
                if (!outlineSidebarCollapsed) {
                    HBox outlineWrapper = new HBox(buildOutlineSidebar());
                    outlineWrapper.setPadding(new Insets(0, 12, 0, 0));
                    page.setLeft(outlineWrapper);
                }
                page.setCenter(buildOutlineEditor());
            }
        } else {
            page.setLeft(buildSidebars());
            page.setCenter(buildWorkspaceCenter());
        }
        root.setCenter(page);
    }

    private Parent buildWorkspaceCenter() {
        HBox center = new HBox(12);
        center.getChildren().add(buildEditor());
        HBox.setHgrow(center.getChildren().get(0), Priority.ALWAYS);
        if (historySidebarVisible) {
            center.getChildren().add(buildHistorySidebar());
        }
        return center;
    }

    private ContextMenu buildBookMenu() {
        MenuItem rename = new MenuItem("Rename Book");
        rename.setOnAction(e -> renameBook());
        MenuItem summary = new MenuItem("Edit Summary");
        summary.setOnAction(e -> editSummary());
        MenuItem cover = new MenuItem("Upload Cover");
        cover.setOnAction(e -> chooseCover());
        MenuItem delete = new MenuItem("Delete Book");
        delete.setOnAction(e -> deleteSelectedBook());
        return new ContextMenu(rename, summary, cover, delete);
    }

    private Parent buildSidebars() {
        HBox sidebars = new HBox(0);
        sidebars.setPadding(new Insets(12, 12, 12, 0));
        if (!volumeSidebarCollapsed) {
            sidebars.getChildren().add(buildVolumeSidebar());
            if (!chapterSidebarCollapsed) {
                sidebars.getChildren().add(resizeHandle(true));
            }
        }
        if (!chapterSidebarCollapsed) {
            sidebars.getChildren().add(buildChapterSidebar());
            sidebars.getChildren().add(resizeHandle(false));
        }
        return sidebars;
    }

    private Parent buildVolumeSidebar() {
        VBox box = sidebar("Volumes", volumeSidebarWidth);
        Button add = fullButton("Add Volume");
        add.setOnAction(e -> addVolume());
        box.getChildren().add(add);
        if (selectedBook != null) {
            for (Volume volume : selectedBook.getVolumes()) {
                box.getChildren().add(row(volume.getName(), selectedVolume == volume, () -> selectVolume(volume), anchor -> showVolumeMenu(volume, anchor)));
            }
        }
        return box;
    }

    private Parent buildChapterSidebar() {
        VBox box = sidebar("Chapters", chapterSidebarWidth);
        Button add = fullButton("Add Chapter");
        add.setOnAction(e -> addChapter());
        box.getChildren().add(add);
        if (selectedVolume != null) {
            for (Chapter chapter : selectedVolume.getChapters()) {
                box.getChildren().add(row(chapter.getTitle(), selectedChapter == chapter, () -> selectChapter(chapter), anchor -> showChapterMenu(chapter, anchor)));
            }
        }
        return box;
    }

    private Parent resizeHandle(boolean forVolume) {
        StackPane handle = new StackPane();
        handle.setPrefWidth(8);
        handle.setStyle("-fx-background-color: transparent;");
        final double[] startX = new double[1];
        final double[] startWidth = new double[1];
        handle.setOnMousePressed(e -> {
            startX[0] = e.getSceneX();
            startWidth[0] = forVolume ? volumeSidebarWidth : chapterSidebarWidth;
        });
        handle.setOnMouseDragged(e -> {
            double next = startWidth[0] + e.getSceneX() - startX[0];
            next = Math.max(180, Math.min(420, next));
            if (forVolume) {
                volumeSidebarWidth = next;
            } else {
                chapterSidebarWidth = next;
            }
            showWorkspace();
        });
        return handle;
    }

    private VBox sidebar(String title, double width) {
        VBox box = new VBox(10);
        box.setPrefWidth(width);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(255,255,255,0.07); -fx-background-radius: 12;");
        Label label = new Label(title);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold;");
        box.getChildren().add(label);
        return box;
    }

    private Button fullButton(String text) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private Parent row(String text, boolean selected, Runnable selectAction, Consumer<Button> menuAction) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Button select = new Button(text);
        select.setMaxWidth(Double.MAX_VALUE);
        select.setStyle(selected ? "-fx-background-color: #d7bb74; -fx-text-fill: #11182d;" : "");
        HBox.setHgrow(select, Priority.ALWAYS);
        select.setOnAction(e -> selectAction.run());
        Button more = new Button("...");
        more.setOnAction(e -> {
            if (displayedMenu != null && displayedMenu.isShowing()) {
                displayedMenu.hide();
                displayedMenu = null;
                return;
            }
            menuAction.accept(more);
        });
        row.getChildren().addAll(select, more);
        return row;
    }

    private Parent buildEditor() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(255,255,255,0.07); -fx-background-radius: 12;");

        Label volume = new Label(selectedVolume == null ? "No volume selected" : selectedVolume.getName());
        volume.setStyle("-fx-text-fill: rgba(255,255,255,0.78);");

        if (selectedChapter == null) {
            Label hint = new Label("该分卷没有任何章节");
            hint.setStyle("-fx-text-fill: rgba(255,255,255,0.40); -fx-font-size: 18px;");
            VBox center = new VBox(hint);
            center.setAlignment(Pos.CENTER);
            VBox.setVgrow(center, Priority.ALWAYS);
            box.getChildren().addAll(volume, center);
            return box;
        }

        Label chapter = new Label(selectedChapter.getTitle() + " | " + wordCount(selectedChapter.getContent()) + " words");
        chapter.setWrapText(true);
        chapter.setMaxWidth(Double.MAX_VALUE);
        chapter.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");

        editor = new TextArea();
        editor.getStyleClass().add("chapter-editor");
        editor.setWrapText(true);
        editor.setText(selectedChapter == null ? "" : blankAsDefault(selectedChapter.getContent(), ""));
        VBox.setVgrow(editor, Priority.ALWAYS);

        Button save = new Button("Save Chapter");
        save.setOnAction(e -> {
            if (selectedChapter != null) {
                selectedChapter.setContent(editor.getText());
                selectedChapter.getVersions().add(new ChapterVersion(LocalDateTime.now(), editor.getText(), wordCount(editor.getText())));
                store.save(state);
                showWorkspace();
            }
        });

        Button history = new Button("History Versions");
        history.setOnAction(e -> {
            historySidebarVisible = !historySidebarVisible;
            showWorkspace();
        });

        HBox actions = new HBox(10, save, history);
        box.getChildren().addAll(volume, chapter, editor, actions);
        return box;
    }

    private Parent buildHistorySidebar() {
        VBox box = sidebar("History Versions", 300);
        box.setMinWidth(300);
        box.setMaxWidth(300);
        if (selectedChapter != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (ChapterVersion version : selectedChapter.getVersions()) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                Button item = fullButton(formatter.format(version.getSavedAt()) + " | " + version.getWordCount() + " words");
                item.setOnAction(e -> {
                    if (editor != null) {
                        editor.setText(blankAsDefault(version.getContent(), ""));
                    }
                });
                HBox.setHgrow(item, Priority.ALWAYS);
                Button delete = IconButtons.trashButton();
                delete.setOnAction(e -> {
                    selectedChapter.getVersions().remove(version);
                    store.save(state);
                    showWorkspace();
                });
                row.getChildren().addAll(item, delete);
                box.getChildren().add(row);
            }
            if (selectedChapter.getVersions().isEmpty()) {
                Label empty = new Label("No versions yet");
                empty.setStyle("-fx-text-fill: rgba(255,255,255,0.65);");
                box.getChildren().add(empty);
            }
        }
        return box;
    }

    private void addBook() {
        prompt("Book Name", "Untitled Book").ifPresent(name -> {
            Book book = new Book(blankAsDefault(name, "Untitled Book"));
            state.getBooks().add(book);
            selectedBookIndex = state.getBooks().size() - 1;
            store.save(state);
            showSelectionPage();
        });
    }

    private void addVolume() {
        if (selectedBook == null) return;
        prompt("Volume Name", "Untitled Volume").ifPresent(name -> {
            Volume volume = new Volume(blankAsDefault(name, "Untitled Volume"));
            selectedBook.getVolumes().add(volume);
            selectedVolume = volume;
            selectedChapter = null;
            store.save(state);
            showWorkspace();
        });
    }

    private void addChapter() {
        if (selectedVolume == null) return;
        prompt("Chapter Name", "Untitled Chapter").ifPresent(name -> {
            Chapter chapter = new Chapter(blankAsDefault(name, "Untitled Chapter"), "");
            selectedVolume.getChapters().add(chapter);
            selectedChapter = chapter;
            store.save(state);
            showWorkspace();
        });
    }

    private void selectVolume(Volume volume) {
        selectedVolume = volume;
        selectedChapter = volume.getChapters().isEmpty() ? null : volume.getChapters().get(0);
        showWorkspace();
    }

    private void selectChapter(Chapter chapter) {
        selectedChapter = chapter;
        showWorkspace();
    }

    private void showVolumeMenu(Volume volume, Button anchor) {
        ContextMenu menu = buildVolumeMenu(volume);
        displayedMenu = menu;
        menu.setOnHidden(e -> {
            if (displayedMenu == menu) displayedMenu = null;
        });
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private ContextMenu buildVolumeMenu(Volume volume) {
        ContextMenu menu = new ContextMenu();
        MenuItem rename = new MenuItem("Rename Volume");
        rename.setOnAction(e -> renameVolume(volume));
        MenuItem up = new MenuItem("Move Up");
        up.setOnAction(e -> moveVolume(volume, -1));
        MenuItem down = new MenuItem("Move Down");
        down.setOnAction(e -> moveVolume(volume, 1));
        MenuItem delete = new MenuItem("Delete Volume");
        delete.setOnAction(e -> {
            selectedBook.getVolumes().remove(volume);
            selectedVolume = selectedBook.getVolumes().isEmpty() ? null : selectedBook.getVolumes().get(0);
            selectedChapter = selectedVolume == null || selectedVolume.getChapters().isEmpty() ? null : selectedVolume.getChapters().get(0);
            store.save(state);
            showWorkspace();
        });
        menu.getItems().addAll(rename, up, down, delete);
        return menu;
    }

    private void showChapterMenu(Chapter chapter, Button anchor) {
        ContextMenu menu = buildChapterMenu(chapter);
        displayedMenu = menu;
        menu.setOnHidden(e -> {
            if (displayedMenu == menu) displayedMenu = null;
        });
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private ContextMenu buildChapterMenu(Chapter chapter) {
        ContextMenu menu = new ContextMenu();
        MenuItem rename = new MenuItem("Rename Chapter");
        rename.setOnAction(e -> renameChapter(chapter));
        MenuItem up = new MenuItem("Move Up");
        up.setOnAction(e -> moveChapter(chapter, -1));
        MenuItem down = new MenuItem("Move Down");
        down.setOnAction(e -> moveChapter(chapter, 1));
        MenuItem delete = new MenuItem("Delete Chapter");
        delete.setOnAction(e -> {
            selectedVolume.getChapters().remove(chapter);
            selectedChapter = selectedVolume.getChapters().isEmpty() ? null : selectedVolume.getChapters().get(0);
            store.save(state);
            showWorkspace();
        });
        menu.getItems().addAll(rename, up, down, delete);
        return menu;
    }

    private void moveVolume(Volume volume, int offset) {
        List<Volume> volumes = selectedBook.getVolumes();
        int index = volumes.indexOf(volume);
        int target = index + offset;
        if (index < 0 || target < 0 || target >= volumes.size()) return;
        volumes.remove(index);
        volumes.add(target, volume);
        selectedVolume = volume;
        store.save(state);
        showWorkspace();
    }

    private void moveChapter(Chapter chapter, int offset) {
        List<Chapter> chapters = selectedVolume.getChapters();
        int index = chapters.indexOf(chapter);
        int target = index + offset;
        if (index < 0 || target < 0 || target >= chapters.size()) return;
        chapters.remove(index);
        chapters.add(target, chapter);
        store.save(state);
        showWorkspace();
    }

    public void renameBook() {
        prompt("Book Name", selectedBook.getName()).ifPresent(name -> {
            selectedBook.setName(blankAsDefault(name, "Untitled Book"));
            store.save(state);
            showWorkspace();
        });
    }

    private void editSummary() {
        TextInputDialog dialog = new TextInputDialog(blankAsDefault(selectedBook.getSummary(), ""));
        dialog.setTitle("Edit Summary");
        dialog.setHeaderText("Book Summary");
        dialog.showAndWait().ifPresent(summary -> {
            selectedBook.setSummary(summary);
            store.save(state);
        });
    }

    public void chooseCover() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file != null) {
            selectedBook.setCoverPath(file.getAbsolutePath());
            store.save(state);
        }
    }

    private void deleteSelectedBook() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete this book?", ButtonType.OK, ButtonType.CANCEL);
        alert.showAndWait().filter(ButtonType.OK::equals).ifPresent(ok -> {
            state.getBooks().remove(selectedBook);
            selectedBookIndex = Math.min(selectedBookIndex, state.getBooks().size() - 1);
            selectedBook = null;
            selectedVolume = null;
            selectedChapter = null;
            store.save(state);
            showSelectionPage();
        });
    }

    private void renameVolume(Volume volume) {
        prompt("Volume Name", volume.getName()).ifPresent(name -> {
            volume.setName(blankAsDefault(name, "Untitled Volume"));
            store.save(state);
            showWorkspace();
        });
    }

    private void renameChapter(Chapter chapter) {
        prompt("Chapter Name", chapter.getTitle()).ifPresent(name -> {
            chapter.setTitle(blankAsDefault(name, "Untitled Chapter"));
            store.save(state);
            showWorkspace();
        });
    }

    // ── Outline methods ──

    private Parent buildOutlineSidebar() {
        VBox box = new VBox(8);
        box.setPrefWidth(260);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(255,255,255,0.07); -fx-background-radius: 12;");

        Label title = new Label("Outline");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold;");
        box.getChildren().add(title);

        if (volChapOutlineMode) {
            Label hint = new Label("请使用右侧分卷和章节\n侧边栏选择要编辑的内容");
            hint.setWrapText(true);
            hint.setStyle("-fx-text-fill: rgba(255,255,255,0.50); -fx-font-size: 13px;");
            box.getChildren().add(hint);
        } else {
            Button add = fullButton("Add Root Node");
            add.setOnAction(e -> addRootOutlineNode());
            box.getChildren().add(add);

            TreeItem<OutlineNode> rootItem = new TreeItem<>(null);
            rootItem.setExpanded(true);
            if (selectedBook != null) {
                for (OutlineNode node : selectedBook.getOutlineRoots()) {
                    rootItem.getChildren().add(buildTreeItem(node));
                }
            }

            TreeView<OutlineNode> treeView = new TreeView<>(rootItem);
            treeView.setShowRoot(false);
            treeView.setStyle("-fx-background-color: transparent; -fx-control-inner-background: transparent; -fx-background-radius: 8;");
            outlineTreeView = treeView;

            treeView.setCellFactory(tv -> new OutlineTreeCell());
            treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (!settingOutlineSelection && newVal != null && newVal.getValue() != null) {
                    selectedOutlineNode = newVal.getValue();
                    showWorkspace();
                }
            });

            if (selectedOutlineNode != null) {
                settingOutlineSelection = true;
                selectTreeNode(treeView.getRoot(), selectedOutlineNode.getId());
                settingOutlineSelection = false;
            }

            VBox.setVgrow(treeView, Priority.ALWAYS);
            box.getChildren().add(treeView);
        }
        return box;
    }

    private TreeItem<OutlineNode> buildTreeItem(OutlineNode node) {
        TreeItem<OutlineNode> item = new TreeItem<>(node);
        item.setExpanded(true);
        for (OutlineNode child : node.getChildren()) {
            item.getChildren().add(buildTreeItem(child));
        }
        return item;
    }

    private void selectTreeNode(TreeItem<OutlineNode> parent, String id) {
        for (TreeItem<OutlineNode> child : parent.getChildren()) {
            if (child.getValue() != null && child.getValue().getId().equals(id)) {
                outlineTreeView.getSelectionModel().select(child);
                return;
            }
            selectTreeNode(child, id);
        }
    }

    private class OutlineTreeCell extends TreeCell<OutlineNode> {
        @Override
        protected void updateItem(OutlineNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setContextMenu(null);
                setStyle("");
            } else {
                setText(item.getTitle());
                setPadding(new Insets(4, 8, 4, 8));
                if (item == selectedOutlineNode) {
                    setStyle("-fx-background-color: #d7bb74; -fx-text-fill: #11182d; -fx-background-radius: 6;");
                } else {
                    setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
                }
                setContextMenu(buildOutlineNodeMenu(item));
            }
        }
    }

    private ContextMenu buildOutlineNodeMenu(OutlineNode node) {
        MenuItem addChild = new MenuItem("Add Child");
        addChild.setOnAction(e -> addChildOutlineNode(node));
        MenuItem addSibling = new MenuItem("Add Sibling");
        addSibling.setOnAction(e -> addSiblingOutlineNode(node));
        MenuItem rename = new MenuItem("Rename");
        rename.setOnAction(e -> renameOutlineNode(node));
        MenuItem up = new MenuItem("Move Up");
        up.setOnAction(e -> moveOutlineNode(node, -1));
        MenuItem down = new MenuItem("Move Down");
        down.setOnAction(e -> moveOutlineNode(node, 1));
        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(e -> deleteOutlineNode(node));
        return new ContextMenu(addChild, addSibling, rename, up, down, delete);
    }

    private Parent buildOutlineEditor() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(255,255,255,0.07); -fx-background-radius: 12;");

        if (selectedOutlineNode == null) {
            Label empty = new Label("Select an outline node to edit");
            empty.setStyle("-fx-text-fill: rgba(255,255,255,0.65); -fx-font-size: 16px;");
            box.getChildren().add(empty);
            return box;
        }

        Label header = new Label("Outline Node");
        header.setStyle("-fx-text-fill: rgba(255,255,255,0.78);");

        TextField titleField = new TextField(selectedOutlineNode.getTitle());
        titleField.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-background-color: #223154; -fx-text-fill: white; -fx-padding: 8;");

        Label contentLabel = new Label("Content");
        contentLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.78);");

        TextArea contentArea = new TextArea(selectedOutlineNode.getContent());
        contentArea.setWrapText(true);
        contentArea.setStyle("-fx-font-size: 14px; -fx-background-color: #223154; -fx-text-fill: white;");
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        Button save = new Button("Save");
        save.getStyleClass().add("primary-action");
        save.setOnAction(e -> {
            selectedOutlineNode.setTitle(titleField.getText());
            selectedOutlineNode.setContent(contentArea.getText());
            store.save(state);
            showWorkspace();
        });

        box.getChildren().addAll(header, titleField, contentLabel, contentArea, save);
        return box;
    }

    // ════════════════════════════════════════
    //  Vol/Chapter outline editor
    // ════════════════════════════════════════

    private Parent buildVolChapOutlineEditor() {
        boolean chapterOpen = selectedChapter != null && !chapterSidebarCollapsed;
        boolean volumeOpen = selectedVolume != null && !volumeSidebarCollapsed;
        boolean editorValid = false;

        if (chapterOpen) {
            // Chapter outline (chapter sidebar open, or both open)
            editorValid = true;
        } else if (volumeOpen) {
            // Volume outline (only volume sidebar open)
            editorValid = true;
        }

        if (!editorValid) {
            VBox empty = new VBox();
            empty.setAlignment(Pos.CENTER);
            Label msg = new Label("请选择分卷或章节");
            msg.setStyle("-fx-text-fill: rgba(255,255,255,0.50); -fx-font-size: 16px;");
            empty.getChildren().add(msg);
            return empty;
        }

        VBox box = new VBox(12);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(255,255,255,0.07); -fx-background-radius: 12;");

        boolean isChapter = chapterOpen;
        String labelText = isChapter
                ? selectedChapter.getTitle() + " — 章节细纲"
                : selectedVolume.getName() + " — 分卷细纲";
        Label header = new Label(labelText);
        header.setWrapText(true);
        header.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");

        String content = isChapter ? selectedChapter.getOutlineContent() : selectedVolume.getOutlineContent();
        TextArea editor = new TextArea(content);
        editor.setWrapText(true);
        editor.setStyle("-fx-font-size: 14px; -fx-background-color: #223154; -fx-text-fill: white; -fx-control-inner-background: #223154;");
        VBox.setVgrow(editor, Priority.ALWAYS);

        Button save = new Button("Save");
        save.getStyleClass().add("primary-action");
        save.setOnAction(e -> {
            String text = editor.getText();
            if (isChapter) {
                selectedChapter.setOutlineContent(text);
            } else {
                selectedVolume.setOutlineContent(text);
            }
            store.save(state);
            header.setText(labelText + "  (saved)");
        });

        box.getChildren().addAll(header, editor, save);
        return box;
    }

    private void addRootOutlineNode() {
        if (selectedBook == null) return;
        prompt("Outline Node Title", "New Outline Node").ifPresent(name -> {
            OutlineNode node = new OutlineNode(name);
            selectedBook.getOutlineRoots().add(node);
            selectedOutlineNode = node;
            store.save(state);
            showWorkspace();
        });
    }

    private void addChildOutlineNode(OutlineNode parent) {
        prompt("Outline Node Title", "New Outline Node").ifPresent(name -> {
            OutlineNode node = new OutlineNode(name);
            parent.getChildren().add(node);
            selectedOutlineNode = node;
            store.save(state);
            showWorkspace();
        });
    }

    private void addSiblingOutlineNode(OutlineNode node) {
        prompt("Outline Node Title", "New Outline Node").ifPresent(name -> {
            OutlineNode newNode = new OutlineNode(name);
            OutlineNode parent = findOutlineParent(selectedBook.getOutlineRoots(), node.getId());
            if (parent != null) {
                int index = findChildIndex(parent.getChildren(), node.getId());
                parent.getChildren().add(index + 1, newNode);
            } else {
                int index = findChildIndex(selectedBook.getOutlineRoots(), node.getId());
                selectedBook.getOutlineRoots().add(index + 1, newNode);
            }
            selectedOutlineNode = newNode;
            store.save(state);
            showWorkspace();
        });
    }

    private void renameOutlineNode(OutlineNode node) {
        prompt("Rename", node.getTitle()).ifPresent(name -> {
            node.setTitle(name);
            store.save(state);
            showWorkspace();
        });
    }

    private void deleteOutlineNode(OutlineNode node) {
        OutlineNode parent = findOutlineParent(selectedBook.getOutlineRoots(), node.getId());
        if (parent != null) {
            parent.getChildren().remove(node);
        } else {
            selectedBook.getOutlineRoots().remove(node);
        }
        selectedOutlineNode = null;
        store.save(state);
        showWorkspace();
    }

    private void moveOutlineNode(OutlineNode node, int offset) {
        OutlineNode parent = findOutlineParent(selectedBook.getOutlineRoots(), node.getId());
        List<OutlineNode> siblings = parent != null ? parent.getChildren() : selectedBook.getOutlineRoots();
        int index = findChildIndex(siblings, node.getId());
        int target = index + offset;
        if (index < 0 || target < 0 || target >= siblings.size()) return;
        siblings.remove(index);
        siblings.add(target, node);
        store.save(state);
        showWorkspace();
    }

    private OutlineNode findOutlineParent(List<OutlineNode> roots, String childId) {
        for (OutlineNode n : roots) {
            if (n.getChildren().stream().anyMatch(c -> c.getId().equals(childId))) {
                return n;
            }
            OutlineNode found = findOutlineParent(n.getChildren(), childId);
            if (found != null) return found;
        }
        return null;
    }

    private int findChildIndex(List<OutlineNode> list, String childId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(childId)) return i;
        }
        return -1;
    }

    private Optional<String> prompt(String title, String initialValue) {
        TextInputDialog dialog = new TextInputDialog(initialValue);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        return dialog.showAndWait();
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

    private String blankAsDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int wordCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        int count = 0;
        boolean inToken = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c)) {
                inToken = false;
            } else if (isCjk(c)) {
                count++;
                inToken = false;
            } else if (!inToken) {
                count++;
                inToken = true;
            }
        }
        return count;
    }

    private boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private void notifyPageState() {
        if (pageStateListener != null) {
            pageStateListener.accept(!inSelectionPage.get());
        }
    }
}
