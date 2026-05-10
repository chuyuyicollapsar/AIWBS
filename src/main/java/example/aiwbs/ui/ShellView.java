package example.aiwbs.ui;

import example.aiwbs.model.AppState;
import example.aiwbs.model.Book;
import example.aiwbs.model.Chapter;
import example.aiwbs.model.OutlineNode;
import example.aiwbs.model.Volume;
import example.aiwbs.storage.AiSessionStore;
import example.aiwbs.storage.StateStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static example.aiwbs.ui.ViewUtils.*;

/**
 * 应用外壳。
 * 1. 顶栏（左侧固定按钮 + 按中央区域类型决定显示的其他按钮）
 * 2. 中央区域路由 + 共享选择状态
 */
public class ShellView {
    private final BorderPane root = new BorderPane();
    private final AppState state;
    private final StateStore store;

    // ── Toolbar ──
    private HBox toolbarCenter;
    private HBox toolbarRight;
    private Button backBtn;
    private Button forwardBtn;
    private Button outlineTreeBtn;
    private Button volChapOutlineBtn;
    private Button aiEntryBtn;
    private Button bookMenuBtn;

    // ── Workspace routing ──
    private WorkspaceType currentType = WorkspaceType.BOOK_CAROUSEL;

    // ── Shared selection state ──
    private int selectedBookIndex = -1;
    private Book selectedBook;
    private Volume selectedVolume;
    private Chapter selectedChapter;
    private OutlineNode selectedOutlineNode;

    // ── Sidebar state (shared between sub-views) ──
    private boolean volumeSidebarCollapsed = true;
    private boolean chapterSidebarCollapsed;
    private boolean historySidebarVisible;
    private boolean outlineSidebarCollapsed;
    private final Set<String> collapsedOutlineNodeIds = new HashSet<>();
    private double volumeSidebarWidth = 240;
    private double chapterSidebarWidth = 260;

    // ── Cached ──
    private AiChatView aiChatView;
    private String aiChatBookId;

    // ── Navigation history ──
    private WorkspaceType previousTypeBeforeSettings;

    public ShellView(AppState state, StateStore store) {
        this.state = state;
        this.store = store;
        if (!state.getBooks().isEmpty()) {
            selectedBookIndex = 0;
        }
        root.setStyle("-fx-background-color: #0f1730;");
        root.setTop(buildToolbar());
        switchTo(WorkspaceType.BOOK_CAROUSEL);
    }

    public Parent getRoot() { return root; }

    // ════════════════════════════════════════
    //  Routing
    // ════════════════════════════════════════

    public WorkspaceType getCurrentType() { return currentType; }

    public void switchTo(WorkspaceType type) {
        currentType = type;
        root.setCenter(createView(type));
        refreshToolbarState();
    }

    private Parent createView(WorkspaceType type) {
        return switch (type) {
            case BOOK_CAROUSEL -> createBookCarouselView().getRoot();
            case CONTENT_EDIT -> createContentEditView().getRoot();
            case OUTLINE -> createOutlineView().getRoot();
            case DETAIL_OUTLINE -> createDetailOutlineView().getRoot();
            case AI_CHAT -> getAiChatView().getRoot();
            case SETTINGS -> createSettingsView().getRoot();
        };
    }

    private BookCarouselView createBookCarouselView() {
        return new BookCarouselView(state, store, selectedBookIndex,
                bookIndex -> {
                    selectedBookIndex = bookIndex;
                    selectedBook = state.getBooks().get(bookIndex);
                    selectedVolume = selectedBook.getVolumes().isEmpty() ? null : selectedBook.getVolumes().get(0);
                    selectedChapter = selectedVolume == null || selectedVolume.getChapters().isEmpty()
                            ? null : selectedVolume.getChapters().get(0);
                    switchTo(WorkspaceType.CONTENT_EDIT);
                },
                this::addBook
        );
    }

    private ContentEditView createContentEditView() {
        return new ContentEditView(state, store, selectedBookIndex, this);
    }

    private OutlineView createOutlineView() {
        return new OutlineView(state, store, selectedBookIndex, this);
    }

    private DetailOutlineView createDetailOutlineView() {
        return new DetailOutlineView(state, store, selectedBookIndex, this);
    }

    private AiChatView getAiChatView() {
        Book contextBook = getAiContextBook();
        String contextBookId = contextBook.getId();
        if (aiChatView == null || !Objects.equals(aiChatBookId, contextBookId)) {
            aiChatView = new AiChatView(state, store, contextBook);
            aiChatBookId = contextBookId;
        }
        return aiChatView;
    }

    private Book getAiContextBook() {
        if (selectedBook != null) return selectedBook;
        if (!state.getBooks().isEmpty()) return state.getBooks().get(0);
        return state.getBooks().get(0);
    }

    private SettingsView createSettingsView() {
        return new SettingsView(state, store, () -> switchTo(previousTypeBeforeSettings));
    }

    // ════════════════════════════════════════
    //  Toolbar
    // ════════════════════════════════════════

    private Parent buildToolbar() {
        StackPane bar = new StackPane();
        bar.setStyle("-fx-background-color: #111a34; -fx-padding: 6 12; -fx-border-color: #223055; -fx-border-width: 0 0 1 0;");

        // ── Left group ──
        HBox left = new HBox(4);
        left.setAlignment(Pos.CENTER_LEFT);
        StackPane.setAlignment(left, Pos.CENTER_LEFT);

        backBtn = IconButtons.backButton();
        tip(backBtn, "返回", "bottom");
        backBtn.setOnAction(e -> {
            switch (currentType) {
                case SETTINGS -> switchTo(previousTypeBeforeSettings);
                case AI_CHAT -> switchTo(WorkspaceType.CONTENT_EDIT);
                case CONTENT_EDIT -> switchTo(WorkspaceType.BOOK_CAROUSEL);
                case OUTLINE, DETAIL_OUTLINE -> switchTo(WorkspaceType.CONTENT_EDIT);
                default -> {}
            }
        });

        forwardBtn = IconButtons.forwardButton();
        tip(forwardBtn, "前进", "bottom");
        forwardBtn.setOnAction(e -> {
            if (currentType == WorkspaceType.BOOK_CAROUSEL && selectedBook != null) {
                switchTo(WorkspaceType.CONTENT_EDIT);
            }
        });

        Button settingsBtn = IconButtons.settingsButton();
        tip(settingsBtn, "设置", "bottom");
        settingsBtn.setOnAction(e -> {
            if (currentType == WorkspaceType.SETTINGS) {
                switchTo(previousTypeBeforeSettings);
            } else {
                previousTypeBeforeSettings = currentType;
                switchTo(WorkspaceType.SETTINGS);
            }
        });

        left.getChildren().addAll(backBtn, forwardBtn, settingsBtn);
        left.setMaxWidth(Region.USE_PREF_SIZE);

        // ── Center group ──
        toolbarCenter = new HBox(6);
        toolbarCenter.setAlignment(Pos.CENTER);
        StackPane.setAlignment(toolbarCenter, Pos.CENTER);

        outlineTreeBtn = IconButtons.outlineEntryButton();
        tip(outlineTreeBtn, "大纲模式", "bottom");
        outlineTreeBtn.setOnAction(e -> {
            if (currentType == WorkspaceType.OUTLINE) {
                switchTo(WorkspaceType.CONTENT_EDIT);
            } else {
                switchTo(WorkspaceType.OUTLINE);
            }
        });

        volChapOutlineBtn = IconButtons.volChapOutlineButton();
        tip(volChapOutlineBtn, "细纲模式", "bottom");
        volChapOutlineBtn.setOnAction(e -> {
            if (currentType == WorkspaceType.DETAIL_OUTLINE) {
                switchTo(WorkspaceType.CONTENT_EDIT);
            } else {
                switchTo(WorkspaceType.DETAIL_OUTLINE);
            }
        });

        aiEntryBtn = IconButtons.aiEntryButton();
        tip(aiEntryBtn, "AI对话", "bottom");
        aiEntryBtn.setOnAction(e -> {
            if (currentType == WorkspaceType.AI_CHAT) {
                switchTo(WorkspaceType.CONTENT_EDIT);
            } else {
                switchTo(WorkspaceType.AI_CHAT);
            }
        });

        toolbarCenter.getChildren().addAll(outlineTreeBtn, volChapOutlineBtn, aiEntryBtn);
        toolbarCenter.setMaxWidth(Region.USE_PREF_SIZE);

        // ── Right group ──
        toolbarRight = new HBox(6);
        toolbarRight.setAlignment(Pos.CENTER_RIGHT);
        StackPane.setAlignment(toolbarRight, Pos.CENTER_RIGHT);

        bookMenuBtn = IconButtons.moreButton();
        tip(bookMenuBtn, "更多操作", "bottom-right");
        bookMenuBtn.setOnAction(e -> showBookMenu());
        toolbarRight.getChildren().addAll(bookMenuBtn);
        toolbarRight.setMaxWidth(Region.USE_PREF_SIZE);

        bar.getChildren().addAll(left, toolbarCenter, toolbarRight);
        refreshToolbarState();
        return bar;
    }

    private void refreshToolbarState() {
        boolean hasSelection = selectedBook != null;

        // Back / forward
        backBtn.setDisable(currentType == WorkspaceType.BOOK_CAROUSEL);
        forwardBtn.setDisable(!(currentType == WorkspaceType.BOOK_CAROUSEL && hasSelection));

        // ── AI chat mode (matches old AI mode behavior) ──
        if (currentType == WorkspaceType.AI_CHAT) {
            toolbarCenter.setVisible(true);
            toolbarCenter.setManaged(true);
            toolbarRight.setVisible(false);
            toolbarRight.setManaged(false);
            setCenterVisible(outlineTreeBtn, false);
            setCenterVisible(volChapOutlineBtn, false);
            aiEntryBtn.setGraphic(new StackPane(IconButtons.aiEntryActiveButton().getGraphic()));
            return;
        }

        // ── Normal / settings / carousel ──
        aiEntryBtn.setGraphic(new StackPane(IconButtons.aiEntryButton().getGraphic()));

        boolean inWorkspace = currentType == WorkspaceType.CONTENT_EDIT
                || currentType == WorkspaceType.OUTLINE
                || currentType == WorkspaceType.DETAIL_OUTLINE;
        toolbarCenter.setVisible(inWorkspace);
        toolbarCenter.setManaged(inWorkspace);
        toolbarRight.setVisible(inWorkspace);
        toolbarRight.setManaged(inWorkspace);

        if (inWorkspace) {
            setCenterVisible(outlineTreeBtn, true);
            setCenterVisible(volChapOutlineBtn, true);
            updateOutlineBtn(currentType == WorkspaceType.OUTLINE);
            updateVolChapBtn(currentType == WorkspaceType.DETAIL_OUTLINE);
        }
    }

    // ── Toolbar helpers ──

    private void setCenterVisible(Button btn, boolean visible) {
        btn.setVisible(visible);
        btn.setManaged(visible);
    }

    private void updateOutlineBtn(boolean active) {
        Button next = active ? IconButtons.outlineEntryActiveButton() : IconButtons.outlineEntryButton();
        tip(next, "大纲模式", "bottom");
        next.setOnAction(e -> {
            if (currentType == WorkspaceType.OUTLINE) {
                switchTo(WorkspaceType.CONTENT_EDIT);
            } else {
                switchTo(WorkspaceType.OUTLINE);
            }
        });
        replaceCenterBtn(outlineTreeBtn, next);
        outlineTreeBtn = next;
    }

    private void updateVolChapBtn(boolean active) {
        Button next = active ? IconButtons.volChapOutlineActiveButton() : IconButtons.volChapOutlineButton();
        tip(next, "细纲模式", "bottom");
        next.setOnAction(e -> {
            if (currentType == WorkspaceType.DETAIL_OUTLINE) {
                switchTo(WorkspaceType.CONTENT_EDIT);
            } else {
                switchTo(WorkspaceType.DETAIL_OUTLINE);
            }
        });
        replaceCenterBtn(volChapOutlineBtn, next);
        volChapOutlineBtn = next;
    }

    private void replaceCenterBtn(Button oldBtn, Button newBtn) {
        int idx = toolbarCenter.getChildren().indexOf(oldBtn);
        if (idx >= 0) toolbarCenter.getChildren().set(idx, newBtn);
    }

    // ════════════════════════════════════════
    //  Book menu
    // ════════════════════════════════════════

    private void showBookMenu() {
        if (selectedBook == null) return;
        ContextMenu menu = new ContextMenu();
        MenuItem rename = new MenuItem("Rename Book");
        rename.setOnAction(e -> renameBook());
        MenuItem summary = new MenuItem("Edit Summary");
        summary.setOnAction(e -> editSummary());
        MenuItem cover = new MenuItem("Upload Cover");
        cover.setOnAction(e -> chooseCover());
        MenuItem delete = new MenuItem("Delete Book");
        delete.setOnAction(e -> deleteSelectedBook());
        menu.getItems().addAll(rename, summary, cover, delete);
        menu.show(bookMenuBtn, Side.BOTTOM, 0, 0);
    }

    // ════════════════════════════════════════
    //  Book operations
    // ════════════════════════════════════════

    private void addBook() {
        showSingleLineInput("Book Name", "Untitled Book").ifPresent(name -> {
            Book book = new Book(blankAsDefault(name, "Untitled Book"));
            state.getBooks().add(book);
            selectedBookIndex = state.getBooks().size() - 1;
            selectedBook = book;
            store.save(state);
            if (currentType == WorkspaceType.BOOK_CAROUSEL) {
                switchTo(WorkspaceType.BOOK_CAROUSEL);
            }
        });
    }

    private void renameBook() {
        if (selectedBook == null) return;
        showSingleLineInput("Book Name", selectedBook.getName()).ifPresent(name -> {
            selectedBook.setName(blankAsDefault(name, "Untitled Book"));
            store.save(state);
            refreshCurrentView();
        });
    }

    private void editSummary() {
        if (selectedBook == null) return;
        Dialog<String> d = multiLineInputDialog("Edit Summary", blankAsDefault(selectedBook.getSummary(), ""));
        d.showAndWait().ifPresent(summary -> {
            selectedBook.setSummary(summary);
            store.save(state);
        });
    }

    private void chooseCover() {
        if (selectedBook == null) return;
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file != null) {
            selectedBook.setCoverPath(file.getAbsolutePath());
            store.save(state);
        }
    }

    private void deleteSelectedBook() {
        if (selectedBook == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete this book?", ButtonType.OK, ButtonType.CANCEL);
        styleDialog(alert.getDialogPane());
        alert.showAndWait().filter(ButtonType.OK::equals).ifPresent(ok -> {
            Book deletedBook = selectedBook;
            state.getBooks().remove(deletedBook);
            selectedBookIndex = Math.min(selectedBookIndex, state.getBooks().size() - 1);
            selectedBook = selectedBookIndex >= 0 ? state.getBooks().get(selectedBookIndex) : null;
            selectedVolume = null;
            selectedChapter = null;
            selectedOutlineNode = null;
            aiChatView = null;
            aiChatBookId = null;
            store.save(state);
            AiSessionStore.deleteBookData(deletedBook.getId());
            switchTo(WorkspaceType.BOOK_CAROUSEL);
        });
    }

    // ════════════════════════════════════════
    //  Shared state accessors (called by sub-views)
    // ════════════════════════════════════════

    void setSelectedVolume(Volume v) { this.selectedVolume = v; }
    void setSelectedChapter(Chapter ch) { this.selectedChapter = ch; }
    void setSelectedOutlineNode(OutlineNode node) { this.selectedOutlineNode = node; }

    Book getSelectedBook() { return selectedBook; }
    Volume getSelectedVolume() { return selectedVolume; }
    Chapter getSelectedChapter() { return selectedChapter; }
    OutlineNode getSelectedOutlineNode() { return selectedOutlineNode; }
    int getSelectedBookIndex() { return selectedBookIndex; }

    boolean isVolumeSidebarCollapsed() { return volumeSidebarCollapsed; }
    boolean isChapterSidebarCollapsed() { return chapterSidebarCollapsed; }
    boolean isHistorySidebarVisible() { return historySidebarVisible; }
    boolean isOutlineSidebarCollapsed() { return outlineSidebarCollapsed; }
    boolean isOutlineNodeCollapsed(String nodeId) { return collapsedOutlineNodeIds.contains(nodeId); }
    double getVolumeSidebarWidth() { return volumeSidebarWidth; }
    double getChapterSidebarWidth() { return chapterSidebarWidth; }

    void setVolumeSidebarCollapsed(boolean v) { this.volumeSidebarCollapsed = v; }
    void setChapterSidebarCollapsed(boolean v) { this.chapterSidebarCollapsed = v; }
    void setHistorySidebarVisible(boolean v) { this.historySidebarVisible = v; }
    void setOutlineSidebarCollapsed(boolean v) { this.outlineSidebarCollapsed = v; }
    void setOutlineNodeCollapsed(String nodeId, boolean collapsed) {
        if (collapsed) {
            collapsedOutlineNodeIds.add(nodeId);
        } else {
            collapsedOutlineNodeIds.remove(nodeId);
        }
    }
    void setVolumeSidebarWidth(double w) { this.volumeSidebarWidth = w; }
    void setChapterSidebarWidth(double w) { this.chapterSidebarWidth = w; }

    void saveState() { store.save(state); }

    private void refreshCurrentView() {
        switchTo(currentType);
    }
}
