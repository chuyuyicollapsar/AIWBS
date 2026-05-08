package example.aiwbs.ui;

import example.aiwbs.model.AppState;
import example.aiwbs.model.Book;
import example.aiwbs.model.Chapter;
import example.aiwbs.model.ChapterVersion;
import example.aiwbs.model.Volume;
import example.aiwbs.storage.StateStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static example.aiwbs.ui.ViewUtils.*;

/**
 * 正文编辑界面。
 * 左侧按钮条：卷侧栏开关、章侧栏开关、AI助手入口
 * 内容区：卷/章侧栏 + 编辑器 + 历史版本
 */
public class ContentEditView {
    private final BorderPane root = new BorderPane();
    private final AppState state;
    private final StateStore store;
    private final Book book;
    private final int bookIndex;
    private final ShellView shellView;

    private Volume selectedVolume;
    private Chapter selectedChapter;
    private boolean volumeSidebarCollapsed;
    private boolean chapterSidebarCollapsed;
    private boolean historySidebarVisible;
    private double volumeSidebarWidth;
    private double chapterSidebarWidth;
    private TextArea editor;
    private ContextMenu displayedMenu;

    public ContentEditView(AppState state, StateStore store, int bookIndex, ShellView shellView) {
        this.state = state;
        this.store = store;
        this.bookIndex = bookIndex;
        this.book = state.getBooks().get(bookIndex);
        this.shellView = shellView;
        this.selectedVolume = shellView.getSelectedVolume();
        this.selectedChapter = shellView.getSelectedChapter();
        this.volumeSidebarCollapsed = shellView.isVolumeSidebarCollapsed();
        this.chapterSidebarCollapsed = shellView.isChapterSidebarCollapsed();
        this.historySidebarVisible = shellView.isHistorySidebarVisible();
        this.volumeSidebarWidth = shellView.getVolumeSidebarWidth();
        this.chapterSidebarWidth = shellView.getChapterSidebarWidth();
        buildUI();
    }

    public Parent getRoot() { return root; }

    // ════════════════════════════════════════
    //  Layout
    // ════════════════════════════════════════

    private void buildUI() {
        root.setStyle("-fx-background-color: #0f1730;");

        // Left bar on root (flush left, no padding — matches old MainView leftBar)
        root.setLeft(buildLeftBar());

        // Center wrapper with 12px padding (matches old BookManagerView page padding)
        HBox body = new HBox(0);
        Parent sidebars = buildSidebars();
        if (sidebars != null) body.getChildren().add(sidebars);
        body.getChildren().add(buildEditorArea());
        HBox.setHgrow(body.getChildren().getLast(), Priority.ALWAYS);

        BorderPane centerWrap = new BorderPane(body);
        centerWrap.setPadding(new Insets(12));
        root.setCenter(centerWrap);
    }

    /** Vertical left bar: volume toggle, chapter toggle, AI assistant. */
    private Parent buildLeftBar() {
        VBox bar = new VBox(6);
        bar.setPadding(new Insets(8, 4, 8, 4));
        bar.setStyle("-fx-background-color: #111a34; -fx-border-color: #223055; -fx-border-width: 0 1 0 0;");
        bar.setAlignment(Pos.TOP_CENTER);

        // Volume toggle
        boolean vc = volumeSidebarCollapsed;
        Button volBtn = vc ? IconButtons.sidebarOpenButton() : IconButtons.sidebarActiveButton();
        tip(volBtn, vc ? "显示分卷侧边栏" : "隐藏分卷侧边栏", "right");
        volBtn.setOnAction(e -> {
            volumeSidebarCollapsed = !volumeSidebarCollapsed;
            shellView.setVolumeSidebarCollapsed(volumeSidebarCollapsed);
            buildUI();
        });

        // Chapter toggle
        boolean cc = chapterSidebarCollapsed;
        Button chapBtn = cc ? IconButtons.chapterOpenButton() : IconButtons.chapterActiveButton();
        tip(chapBtn, cc ? "显示章节侧边栏" : "隐藏章节侧边栏", "right");
        chapBtn.setOnAction(e -> {
            chapterSidebarCollapsed = !chapterSidebarCollapsed;
            shellView.setChapterSidebarCollapsed(chapterSidebarCollapsed);
            buildUI();
        });

        // AI assistant（界面预留，暂无功能）
        Button aiBtn = IconButtons.aiButton();
        tip(aiBtn, "AI助手", "right");
        // 动作待实现

        bar.getChildren().addAll(volBtn, chapBtn, aiBtn);
        return bar;
    }

    // ════════════════════════════════════════
    //  Sidebars
    // ════════════════════════════════════════

    private Parent buildSidebars() {
        if (volumeSidebarCollapsed && chapterSidebarCollapsed) return null;
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

        VBox list = new VBox(6);
        for (Volume v : book.getVolumes()) {
            list.getChildren().add(row(v.getName(), v == selectedVolume,
                    () -> selectVolume(v), anchor -> showVolumeMenu(v, anchor)));
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        box.getChildren().add(scroll);
        return box;
    }

    private Parent buildChapterSidebar() {
        VBox box = sidebar("Chapters", chapterSidebarWidth);
        Button add = fullButton("Add Chapter");
        add.setOnAction(e -> addChapter());
        box.getChildren().add(add);

        VBox list = new VBox(6);
        if (selectedVolume != null) {
            for (Chapter ch : selectedVolume.getChapters()) {
                list.getChildren().add(row(ch.getTitle(), ch == selectedChapter,
                        () -> selectChapter(ch), anchor -> showChapterMenu(ch, anchor)));
            }
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        box.getChildren().add(scroll);
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
                shellView.setVolumeSidebarWidth(next);
            } else {
                chapterSidebarWidth = next;
                shellView.setChapterSidebarWidth(next);
            }
            buildUI();
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
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    private Parent row(String text, boolean selected, Runnable selectAction, java.util.function.Consumer<Button> menuAction) {
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

    // ════════════════════════════════════════
    //  Editor
    // ════════════════════════════════════════

    private Parent buildEditorArea() {
        HBox center = new HBox(12);
        center.getChildren().add(buildEditor());
        HBox.setHgrow(center.getChildren().get(0), Priority.ALWAYS);
        if (historySidebarVisible) {
            center.getChildren().add(buildHistorySidebar());
        }
        return center;
    }

    private Parent buildEditor() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(255,255,255,0.07); -fx-background-radius: 12;");

        Label volume = new Label(selectedVolume == null ? "No volume selected" : selectedVolume.getName());
        volume.setStyle("-fx-text-fill: rgba(255,255,255,0.78);");

        if (selectedChapter == null) {
            Label hint = new Label("没有任何章节");
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
        editor.setText(blankAsDefault(selectedChapter.getContent(), ""));
        VBox.setVgrow(editor, Priority.ALWAYS);

        Button save = new Button("Save Chapter");
        save.setOnAction(e -> {
            if (selectedChapter != null) {
                selectedChapter.setContent(editor.getText());
                selectedChapter.getVersions().add(new ChapterVersion(LocalDateTime.now(), editor.getText(), wordCount(editor.getText())));
                store.save(state);
                buildUI();
            }
        });

        Button history = new Button("History Versions");
        history.setOnAction(e -> {
            historySidebarVisible = !historySidebarVisible;
            shellView.setHistorySidebarVisible(historySidebarVisible);
            buildUI();
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
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (ChapterVersion version : selectedChapter.getVersions()) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                Button item = fullButton(fmt.format(version.getSavedAt()) + " | " + version.getWordCount() + " words");
                item.setOnAction(e -> {
                    if (editor != null) {
                        editor.setText(blankAsDefault(version.getContent(), ""));
                    }
                });
                HBox.setHgrow(item, Priority.ALWAYS);
                Button del = IconButtons.trashButton();
                del.setOnAction(e -> {
                    selectedChapter.getVersions().remove(version);
                    store.save(state);
                    buildUI();
                });
                row.getChildren().addAll(item, del);
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

    // ════════════════════════════════════════
    //  Volume / Chapter CRUD
    // ════════════════════════════════════════

    private void addVolume() {
        if (book == null) return;
        showSingleLineInput("Volume Name", "Untitled Volume").ifPresent(name -> {
            String volumeName = blankAsDefault(name, "Untitled Volume").trim();
            if (hasVolumeNamed(volumeName, null)) {
                showDuplicateName("分卷", volumeName);
                return;
            }
            Volume v = new Volume(volumeName);
            book.getVolumes().add(v);
            selectedVolume = v;
            selectedChapter = null;
            store.save(state);
            syncShellState();
            buildUI();
        });
    }

    private void addChapter() {
        if (selectedVolume == null) return;
        showSingleLineInput("Chapter Name", "Untitled Chapter").ifPresent(name -> {
            String chapterName = blankAsDefault(name, "Untitled Chapter").trim();
            if (hasChapterNamed(selectedVolume, chapterName, null)) {
                showDuplicateName("章节", chapterName);
                return;
            }
            Chapter ch = new Chapter(chapterName, "");
            selectedVolume.getChapters().add(ch);
            selectedChapter = ch;
            store.save(state);
            syncShellState();
            buildUI();
        });
    }

    private void selectVolume(Volume v) {
        selectedVolume = v;
        selectedChapter = v.getChapters().isEmpty() ? null : v.getChapters().get(0);
        syncShellState();
        buildUI();
    }

    private void selectChapter(Chapter ch) {
        selectedChapter = ch;
        syncShellState();
        buildUI();
    }

    private void showVolumeMenu(Volume v, Button anchor) {
        ContextMenu menu = buildVolumeMenu(v);
        displayedMenu = menu;
        menu.setOnHidden(e -> {
            if (displayedMenu == menu) displayedMenu = null;
        });
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private ContextMenu buildVolumeMenu(Volume v) {
        ContextMenu menu = new ContextMenu();
        MenuItem rename = new MenuItem("Rename Volume");
        rename.setOnAction(e -> renameVolume(v));
        MenuItem up = new MenuItem("Move Up");
        up.setOnAction(e -> moveVolume(v, -1));
        MenuItem down = new MenuItem("Move Down");
        down.setOnAction(e -> moveVolume(v, 1));
        MenuItem delete = new MenuItem("Delete Volume");
        delete.setOnAction(e -> {
            book.getVolumes().remove(v);
            selectedVolume = book.getVolumes().isEmpty() ? null : book.getVolumes().get(0);
            selectedChapter = selectedVolume == null || selectedVolume.getChapters().isEmpty() ? null : selectedVolume.getChapters().get(0);
            store.save(state);
            syncShellState();
            buildUI();
        });
        menu.getItems().addAll(rename, up, down, delete);
        return menu;
    }

    private void showChapterMenu(Chapter ch, Button anchor) {
        ContextMenu menu = buildChapterMenu(ch);
        displayedMenu = menu;
        menu.setOnHidden(e -> {
            if (displayedMenu == menu) displayedMenu = null;
        });
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private ContextMenu buildChapterMenu(Chapter ch) {
        ContextMenu menu = new ContextMenu();
        MenuItem rename = new MenuItem("Rename Chapter");
        rename.setOnAction(e -> renameChapter(ch));
        MenuItem up = new MenuItem("Move Up");
        up.setOnAction(e -> moveChapter(ch, -1));
        MenuItem down = new MenuItem("Move Down");
        down.setOnAction(e -> moveChapter(ch, 1));
        MenuItem delete = new MenuItem("Delete Chapter");
        delete.setOnAction(e -> {
            selectedVolume.getChapters().remove(ch);
            selectedChapter = selectedVolume.getChapters().isEmpty() ? null : selectedVolume.getChapters().get(0);
            store.save(state);
            syncShellState();
            buildUI();
        });
        menu.getItems().addAll(rename, up, down, delete);
        return menu;
    }

    private void moveVolume(Volume v, int offset) {
        List<Volume> volumes = book.getVolumes();
        int index = volumes.indexOf(v);
        int target = index + offset;
        if (index < 0 || target < 0 || target >= volumes.size()) return;
        volumes.remove(index);
        volumes.add(target, v);
        selectedVolume = v;
        store.save(state);
        syncShellState();
        buildUI();
    }

    private void moveChapter(Chapter ch, int offset) {
        if (selectedVolume == null) return;
        List<Chapter> chapters = selectedVolume.getChapters();
        int index = chapters.indexOf(ch);
        int target = index + offset;
        if (index < 0 || target < 0 || target >= chapters.size()) return;
        chapters.remove(index);
        chapters.add(target, ch);
        store.save(state);
        buildUI();
    }

    private void renameVolume(Volume v) {
        showSingleLineInput("Volume Name", v.getName()).ifPresent(name -> {
            String volumeName = blankAsDefault(name, "Untitled Volume").trim();
            if (hasVolumeNamed(volumeName, v)) {
                showDuplicateName("分卷", volumeName);
                return;
            }
            v.setName(volumeName);
            store.save(state);
            buildUI();
        });
    }

    private void renameChapter(Chapter ch) {
        showSingleLineInput("Chapter Name", ch.getTitle()).ifPresent(name -> {
            String chapterName = blankAsDefault(name, "Untitled Chapter").trim();
            if (hasChapterNamed(selectedVolume, chapterName, ch)) {
                showDuplicateName("章节", chapterName);
                return;
            }
            ch.setTitle(chapterName);
            store.save(state);
            buildUI();
        });
    }

    private boolean hasVolumeNamed(String name, Volume ignored) {
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) return false;
        for (Volume volume : book.getVolumes()) {
            if (volume == ignored) continue;
            if (normalizeName(volume.getName()).equals(normalized)) return true;
        }
        return false;
    }

    private boolean hasChapterNamed(Volume volume, String name, Chapter ignored) {
        if (volume == null) return false;
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) return false;
        for (Chapter chapter : volume.getChapters()) {
            if (chapter == ignored) continue;
            if (normalizeName(chapter.getTitle()).equals(normalized)) return true;
        }
        return false;
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private void showDuplicateName(String type, String name) {
        Alert alert = new Alert(Alert.AlertType.ERROR,
                type + "名称已存在：" + name,
                ButtonType.OK);
        alert.setTitle("名称重复");
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private void syncShellState() {
        shellView.setSelectedVolume(selectedVolume);
        shellView.setSelectedChapter(selectedChapter);
        store.save(state);
    }
}
