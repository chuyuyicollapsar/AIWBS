package example.aiwbs.ui;

import example.aiwbs.model.AppState;
import example.aiwbs.model.Book;
import example.aiwbs.model.Chapter;
import example.aiwbs.model.Volume;
import example.aiwbs.storage.StateStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import static example.aiwbs.ui.ViewUtils.*;

/**
 * 细纲界面。
 * 左侧按钮条：卷侧栏开关、章侧栏开关
 * 内容区：卷/章侧栏 + 细纲编辑器
 */
public class DetailOutlineView {
    private final BorderPane root = new BorderPane();
    private final AppState state;
    private final StateStore store;
    private final Book book;
    private final ShellView shellView;

    private Volume selectedVolume;
    private Chapter selectedChapter;
    private boolean volumeSidebarCollapsed;
    private boolean chapterSidebarCollapsed;
    private double volumeSidebarWidth;
    private double chapterSidebarWidth;

    public DetailOutlineView(AppState state, StateStore store, int bookIndex, ShellView shellView) {
        this.state = state;
        this.store = store;
        this.book = state.getBooks().get(bookIndex);
        this.shellView = shellView;
        this.selectedVolume = shellView.getSelectedVolume();
        this.selectedChapter = shellView.getSelectedChapter();
        this.volumeSidebarCollapsed = shellView.isVolumeSidebarCollapsed();
        this.chapterSidebarCollapsed = shellView.isChapterSidebarCollapsed();
        this.volumeSidebarWidth = shellView.getVolumeSidebarWidth();
        this.chapterSidebarWidth = shellView.getChapterSidebarWidth();
        buildUI();
    }

    public Parent getRoot() { return root; }

    private void buildUI() {
        root.setStyle("-fx-background-color: #0f1730;");

        // Left bar on root (flush left)
        root.setLeft(buildLeftBar());

        // Center wrapper with 12px padding
        HBox body = new HBox(0);
        Parent sidebars = buildSidebars();
        if (sidebars != null) body.getChildren().add(sidebars);
        body.getChildren().add(buildVolChapOutlineEditor());
        HBox.setHgrow(body.getChildren().getLast(), Priority.ALWAYS);

        BorderPane centerWrap = new BorderPane(body);
        centerWrap.setPadding(new Insets(12));
        root.setCenter(centerWrap);
    }

    /** Vertical left bar: volume toggle, chapter toggle. */
    private Parent buildLeftBar() {
        VBox bar = new VBox(6);
        bar.setPadding(new Insets(8, 4, 8, 4));
        bar.setStyle("-fx-background-color: #111a34; -fx-border-color: #223055; -fx-border-width: 0 1 0 0;");
        bar.setAlignment(Pos.TOP_CENTER);

        boolean vc = volumeSidebarCollapsed;
        Button volBtn = vc ? IconButtons.sidebarOpenButton() : IconButtons.sidebarActiveButton();
        tip(volBtn, vc ? "显示分卷侧边栏" : "隐藏分卷侧边栏", "right");
        volBtn.setOnAction(e -> {
            volumeSidebarCollapsed = !volumeSidebarCollapsed;
            shellView.setVolumeSidebarCollapsed(volumeSidebarCollapsed);
            buildUI();
        });

        boolean cc = chapterSidebarCollapsed;
        Button chapBtn = cc ? IconButtons.chapterOpenButton() : IconButtons.chapterActiveButton();
        tip(chapBtn, cc ? "显示章节侧边栏" : "隐藏章节侧边栏", "right");
        chapBtn.setOnAction(e -> {
            chapterSidebarCollapsed = !chapterSidebarCollapsed;
            shellView.setChapterSidebarCollapsed(chapterSidebarCollapsed);
            buildUI();
        });

        bar.getChildren().addAll(volBtn, chapBtn);
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
            sidebars.getChildren().add(buildSimpleVolumeSidebar());
            if (!chapterSidebarCollapsed) {
                sidebars.getChildren().add(resizeHandle(true));
            }
        }
        if (!chapterSidebarCollapsed) {
            sidebars.getChildren().add(buildSimpleChapterSidebar());
            sidebars.getChildren().add(resizeHandle(false));
        }
        return sidebars;
    }

    private VBox sidebarBox(String title, double width) {
        VBox box = new VBox(10);
        box.setPrefWidth(width);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(255,255,255,0.07); -fx-background-radius: 12;");
        Label label = new Label(title);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold;");
        box.getChildren().add(label);
        return box;
    }

    private Parent buildSimpleVolumeSidebar() {
        VBox box = sidebarBox("Volumes", volumeSidebarWidth);
        for (Volume v : book.getVolumes()) {
            Button btn = new Button(v.getName());
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setStyle(v == selectedVolume ? "-fx-background-color: #d7bb74; -fx-text-fill: #11182d;" : "");
            btn.setOnAction(e -> {
                selectedVolume = v;
                selectedChapter = v.getChapters().isEmpty() ? null : v.getChapters().get(0);
                shellView.setSelectedVolume(v);
                shellView.setSelectedChapter(selectedChapter);
                buildUI();
            });
            box.getChildren().add(btn);
        }
        return box;
    }

    private Parent buildSimpleChapterSidebar() {
        VBox box = sidebarBox("Chapters", chapterSidebarWidth);
        if (selectedVolume != null) {
            for (Chapter ch : selectedVolume.getChapters()) {
                Button btn = new Button(ch.getTitle());
                btn.setMaxWidth(Double.MAX_VALUE);
                btn.setStyle(ch == selectedChapter ? "-fx-background-color: #d7bb74; -fx-text-fill: #11182d;" : "");
                btn.setOnAction(e -> {
                    selectedChapter = ch;
                    shellView.setSelectedChapter(ch);
                    buildUI();
                });
                box.getChildren().add(btn);
            }
        }
        return box;
    }

    private Parent resizeHandle(boolean forVolume) {
        javafx.scene.layout.StackPane handle = new javafx.scene.layout.StackPane();
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

    // ════════════════════════════════════════
    //  Vol/Chapter outline editor
    // ════════════════════════════════════════

    private Parent buildVolChapOutlineEditor() {
        boolean chapterOpen = selectedChapter != null && !chapterSidebarCollapsed;
        boolean volumeOpen = selectedVolume != null && !volumeSidebarCollapsed;
        boolean editorValid = chapterOpen || volumeOpen;

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
}
