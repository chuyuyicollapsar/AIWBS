package example.aiwbs.ui;

import example.aiwbs.model.AppState;
import example.aiwbs.model.Book;
import example.aiwbs.storage.StateStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class MainView {
    private final BorderPane root = new BorderPane();
    private final BookManagerView bookManagerView;
    private final AppState state;
    private final StateStore store;
    private boolean inSettingsMode;
    private boolean inAiMode;

    // Toolbar sections
    private HBox toolbarCenter;
    private HBox toolbarRight;

    // Toolbar buttons
    private Button backBtn;
    private Button forwardBtn;
    private Button outlineTreeBtn;
    private Button volChapOutlineBtn;
    private Button aiEntryBtn;

    // Left bar
    private final VBox leftBar = new VBox(6);
    private Button volToggleBtn;
    private Button chapToggleBtn;
    private Button outlineSidebarToggleBtn;

    // AI conversation view
    private AiConversationView aiConversationView;

    // Stored original left bar buttons for restoration
    private Button aiBtn;

    public MainView(AppState state, StateStore store) {
        this.state = state;
        this.store = store;
        bookManagerView = new BookManagerView(state, store);
        bookManagerView.inSelectionPageProperty().addListener((obs, oldValue, newValue) -> refreshToolbarState());

        root.setStyle("-fx-background-color: #0f1730;");
        root.setTop(buildToolbar());
        root.setLeft(leftBar);
        root.setCenter(bookManagerView.getRoot());
    }

    public Parent getRoot() {
        return root;
    }

    // ════════════════════════════════════════
    //  Top toolbar
    // ════════════════════════════════════════

    private Parent buildToolbar() {
        StackPane bar = new StackPane();
        bar.setStyle("-fx-background-color: #111a34; -fx-padding: 6 12; -fx-border-color: #223055; -fx-border-width: 0 0 1 0;");

        // ── Left group (anchored left) ──
        HBox left = new HBox(4);
        left.setAlignment(Pos.CENTER_LEFT);
        StackPane.setAlignment(left, Pos.CENTER_LEFT);

        backBtn = IconButtons.backButton();
        tip(backBtn, "返回", "bottom");
        backBtn.setOnAction(e -> {
            if (inSettingsMode) {
                exitSettings();
            } else if (inAiMode) {
                exitAiMode();
            } else if (!bookManagerView.inSelectionPageProperty().get()) {
                bookManagerView.showSelectionPage();
            }
        });

        forwardBtn = IconButtons.forwardButton();
        tip(forwardBtn, "前进", "bottom");
        forwardBtn.setOnAction(e -> bookManagerView.enterSelectedBook());

        Button settingsBtn = IconButtons.settingsButton();
        tip(settingsBtn, "设置", "bottom");
        settingsBtn.setOnAction(e -> {
            if (inSettingsMode) {
                exitSettings();
            } else {
                showSettings();
            }
        });

        left.getChildren().addAll(backBtn, forwardBtn, settingsBtn);
        left.setMaxWidth(Region.USE_PREF_SIZE);

        // ── Center group (anchored center — absolute center of toolbar) ──
        toolbarCenter = new HBox(6);
        toolbarCenter.setAlignment(Pos.CENTER);
        StackPane.setAlignment(toolbarCenter, Pos.CENTER);

        outlineTreeBtn = IconButtons.outlineEntryButton();
        tip(outlineTreeBtn, "大纲模式", "bottom");
        volChapOutlineBtn = IconButtons.volChapOutlineButton();
        tip(volChapOutlineBtn, "细纲模式", "bottom");

        outlineTreeBtn.setOnAction(e -> {
            if (bookManagerView.isOutlineVisible() && !bookManagerView.isVolChapOutlineMode()) {
                bookManagerView.exitOutline();
            } else {
                bookManagerView.setOutlineMode(false);
            }
            refreshToolbarState();
        });

        volChapOutlineBtn.setOnAction(e -> {
            if (bookManagerView.isOutlineVisible() && bookManagerView.isVolChapOutlineMode()) {
                bookManagerView.exitOutline();
            } else {
                bookManagerView.setOutlineMode(true);
            }
            refreshToolbarState();
        });

        aiEntryBtn = IconButtons.aiEntryButton();
        tip(aiEntryBtn, "AI对话", "bottom");
        aiEntryBtn.setOnAction(e -> {
            if (inAiMode) {
                exitAiMode();
            } else {
                enterAiMode();
            }
        });

        toolbarCenter.getChildren().addAll(outlineTreeBtn, volChapOutlineBtn, aiEntryBtn);
        toolbarCenter.setMaxWidth(Region.USE_PREF_SIZE);

        // ── Right group (anchored right) ──
        toolbarRight = new HBox(6);
        toolbarRight.setAlignment(Pos.CENTER_RIGHT);
        StackPane.setAlignment(toolbarRight, Pos.CENTER_RIGHT);

        Button bookMenuBtn = IconButtons.moreButton();
        tip(bookMenuBtn, "更多操作", "bottom-right");
        bookMenuBtn.setOnAction(e -> {
            if (bookManagerView.hasSelection()) {
                bookManagerView.showBookMenu(bookMenuBtn);
            }
        });
        toolbarRight.getChildren().addAll(bookMenuBtn);
        toolbarRight.setMaxWidth(Region.USE_PREF_SIZE);

        bar.getChildren().addAll(left, toolbarCenter, toolbarRight);

        buildLeftBar();
        refreshLeftBar();
        refreshToolbarState();
        return bar;
    }

    // ════════════════════════════════════════
    //  Left button bar
    // ════════════════════════════════════════

    private void buildLeftBar() {
        leftBar.setPadding(new Insets(8, 4, 8, 4));
        leftBar.setStyle("-fx-background-color: #111a34; -fx-border-color: #223055; -fx-border-width: 0 1 0 0;");
        leftBar.setAlignment(Pos.TOP_CENTER);

        boolean vc = bookManagerView.isVolumeSidebarCollapsed();
        Button volBtn = vc ? IconButtons.sidebarOpenButton() : IconButtons.sidebarActiveButton();
        tip(volBtn, vc ? "显示分卷侧边栏" : "隐藏分卷侧边栏", "right");
        volBtn.setOnAction(e -> {
            bookManagerView.toggleVolumeSidebar();
            refreshLeftBar();
        });
        volToggleBtn = volBtn;

        boolean cc = bookManagerView.isChapterSidebarCollapsed();
        Button chapBtn = cc ? IconButtons.chapterOpenButton() : IconButtons.chapterActiveButton();
        tip(chapBtn, cc ? "显示章节侧边栏" : "隐藏章节侧边栏", "right");
        chapBtn.setOnAction(e -> {
            bookManagerView.toggleChapterSidebar();
            refreshLeftBar();
        });
        chapToggleBtn = chapBtn;

        Button outlineBtn = IconButtons.outlineButton();
        tip(outlineBtn, "显示大纲树侧边栏", "right");
        outlineBtn.setOnAction(e -> {
            bookManagerView.toggleOutlineSidebar();
            refreshLeftBar();
        });
        outlineSidebarToggleBtn = outlineBtn;

        this.aiBtn = IconButtons.aiButton();
        tip(this.aiBtn, "AI助手", "right");

        leftBar.getChildren().addAll(volBtn, chapBtn, outlineBtn, this.aiBtn);
    }

    // ════════════════════════════════════════
    //  Settings
    // ════════════════════════════════════════

    private void showSettings() {
        inSettingsMode = true;
        SettingsView settingsView = new SettingsView(state, store, this::exitSettings);
        root.setCenter(settingsView.getRoot());
        refreshToolbarState();
    }

    private void exitSettings() {
        inSettingsMode = false;
        if (inAiMode) {
            root.setCenter(aiConversationView.getRoot());
        } else {
            root.setCenter(bookManagerView.getRoot());
        }
        refreshToolbarState();
    }

    // ════════════════════════════════════════
    //  AI mode
    // ════════════════════════════════════════

    private void enterAiMode() {
        inAiMode = true;
        aiConversationView = new AiConversationView(state, store, bookManagerView.getSelectedBook());
        aiConversationView.setOnPanelToggle(this::refreshToolbarState);
        root.setCenter(aiConversationView.getRoot());
        refreshToolbarState();
    }

    private void exitAiMode() {
        inAiMode = false;
        aiConversationView = null;
        root.setCenter(bookManagerView.getRoot());
        refreshToolbarState();
    }

    // ════════════════════════════════════════
    //  State refresh
    // ════════════════════════════════════════

    private void refreshToolbarState() {
        boolean inSelectionPage = bookManagerView.inSelectionPageProperty().get();
        boolean inWorkspace = !inSelectionPage && !inSettingsMode && !inAiMode;
        boolean outlineOn = bookManagerView.isOutlineVisible();
        boolean volChapMode = bookManagerView.isVolChapOutlineMode();

        // Back / forward
        backBtn.setDisable(inSelectionPage && !inSettingsMode && !inAiMode);
        forwardBtn.setDisable(!inSelectionPage || inSettingsMode || inAiMode);

        if (inAiMode && !inSettingsMode) {
            // ── AI mode toolbar ──
            toolbarCenter.setVisible(true);
            toolbarCenter.setManaged(true);
            toolbarRight.setVisible(false);
            toolbarRight.setManaged(false);

            // AI mode left bar with session/nav toggle buttons
            leftBar.setVisible(true);
            leftBar.setManaged(true);
            rebuildAiLeftBar();

            // Hide normal center buttons, show AI entry active
            setCenterVisible(outlineTreeBtn, false);
            setCenterVisible(volChapOutlineBtn, false);
            aiEntryBtn.setGraphic(new StackPane(IconButtons.aiEntryActiveButton().getGraphic()));
            return;
        }

        // ── Normal / settings mode ──
        // Restore normal left bar if was in AI mode
        restoreNormalLeftBar();

        // Restore normal AI entry icon
        aiEntryBtn.setGraphic(new StackPane(IconButtons.aiEntryButton().getGraphic()));

        // Toolbar center/right visibility
        toolbarCenter.setVisible(inWorkspace);
        toolbarCenter.setManaged(inWorkspace);
        toolbarRight.setVisible(inWorkspace);
        toolbarRight.setManaged(inWorkspace);

        // Show normal center buttons
        setCenterVisible(outlineTreeBtn, true);
        setCenterVisible(volChapOutlineBtn, true);

        // Outline buttons highlight
        updateOutlineBtn(outlineOn && !volChapMode);
        updateVolChapBtn(outlineOn && volChapMode);

        // Left bar visibility
        leftBar.setVisible(inWorkspace);
        leftBar.setManaged(inWorkspace);
        if (!inWorkspace) return;

        // ── Left bar buttons by mode ──
        if (outlineOn && !volChapMode) {
            setLeftBtn(volToggleBtn, false);
            setLeftBtn(chapToggleBtn, false);
            setLeftBtn(outlineSidebarToggleBtn, true);
            updateOutlineToggleIcon();
        } else if (outlineOn && volChapMode) {
            setLeftBtn(volToggleBtn, true);
            setLeftBtn(chapToggleBtn, true);
            setLeftBtn(outlineSidebarToggleBtn, false);
            updateVolChapIcons();
        } else {
            setLeftBtn(volToggleBtn, true);
            setLeftBtn(chapToggleBtn, true);
            setLeftBtn(outlineSidebarToggleBtn, false);
            updateVolChapIcons();
        }
    }

    private void rebuildAiLeftBar() {
        if (aiConversationView == null) return;
        leftBar.getChildren().clear();
        leftBar.setPadding(new Insets(8, 4, 8, 4));
        leftBar.setStyle("-fx-background-color: #111a34; -fx-border-color: #223055; -fx-border-width: 0 1 0 0;");
        leftBar.setAlignment(Pos.TOP_CENTER);

        boolean sessionVis = aiConversationView.isSessionPanelVisible();
        Button sBtn = sessionVis ? IconButtons.sessionToggleActiveButton() : IconButtons.sessionToggleButton();
        tip(sBtn, "会话列表", "right");
        sBtn.setOnAction(e -> {
            if (aiConversationView != null) {
                aiConversationView.toggleSessionPanel();
                rebuildAiLeftBar();
            }
        });

        boolean navVis = aiConversationView.isNavTreeVisible();
        Button nBtn = navVis ? IconButtons.navTreeToggleActiveButton() : IconButtons.navTreeToggleButton();
        tip(nBtn, "对话树", "right");
        nBtn.setOnAction(e -> {
            if (aiConversationView != null) {
                aiConversationView.toggleNavTree();
                rebuildAiLeftBar();
            }
        });

        leftBar.getChildren().addAll(sBtn, nBtn);
    }

    private void restoreNormalLeftBar() {
        // Check if left bar is empty or showing AI buttons (first child isn't volToggleBtn)
        if (leftBar.getChildren().isEmpty() || leftBar.getChildren().get(0) != volToggleBtn) {
            leftBar.getChildren().clear();
            leftBar.setPadding(new Insets(8, 4, 8, 4));
            leftBar.setStyle("-fx-background-color: #111a34; -fx-border-color: #223055; -fx-border-width: 0 1 0 0;");
            leftBar.setAlignment(Pos.TOP_CENTER);
            leftBar.getChildren().addAll(volToggleBtn, chapToggleBtn, outlineSidebarToggleBtn, aiBtn);
        }
    }

    private void setCenterVisible(Button btn, boolean visible) {
        btn.setVisible(visible);
        btn.setManaged(visible);
    }

    private void refreshLeftBar() {
        refreshToolbarState();
    }

    // ── Left bar helpers ──

    private void updateVolChapIcons() {
        if (!volToggleBtn.isVisible()) return;

        boolean volCollapsed = bookManagerView.isVolumeSidebarCollapsed();
        Button nextVol = volCollapsed ? IconButtons.sidebarOpenButton() : IconButtons.sidebarActiveButton();
        tip(nextVol, volCollapsed ? "显示分卷侧边栏" : "隐藏分卷侧边栏", "right");
        nextVol.setOnAction(e -> {
            bookManagerView.toggleVolumeSidebar();
            refreshLeftBar();
        });
        replaceLeftBtn(volToggleBtn, nextVol);
        volToggleBtn = nextVol;

        boolean chapCollapsed = bookManagerView.isChapterSidebarCollapsed();
        Button nextChap = chapCollapsed ? IconButtons.chapterOpenButton() : IconButtons.chapterActiveButton();
        tip(nextChap, chapCollapsed ? "显示章节侧边栏" : "隐藏章节侧边栏", "right");
        nextChap.setOnAction(e -> {
            bookManagerView.toggleChapterSidebar();
            refreshLeftBar();
        });
        replaceLeftBtn(chapToggleBtn, nextChap);
        chapToggleBtn = nextChap;
    }

    private void updateOutlineToggleIcon() {
        boolean oc = bookManagerView.isOutlineSidebarCollapsed();
        Button next = oc ? IconButtons.outlineButton() : IconButtons.outlineActiveButton();
        tip(next, oc ? "显示大纲树侧边栏" : "隐藏大纲树侧边栏", "right");
        next.setOnAction(e -> {
            bookManagerView.toggleOutlineSidebar();
            refreshLeftBar();
        });
        replaceLeftBtn(outlineSidebarToggleBtn, next);
        outlineSidebarToggleBtn = next;
    }

    private void replaceLeftBtn(Button oldBtn, Button newBtn) {
        int idx = leftBar.getChildren().indexOf(oldBtn);
        if (idx >= 0) leftBar.getChildren().set(idx, newBtn);
    }

    // ── Toolbar outline button helpers ──

    private void updateOutlineBtn(boolean active) {
        Button next = active ? IconButtons.outlineEntryActiveButton() : IconButtons.outlineEntryButton();
        tip(next, "大纲模式", "bottom");
        next.setOnAction(e -> {
            if (bookManagerView.isOutlineVisible() && !bookManagerView.isVolChapOutlineMode()) {
                bookManagerView.exitOutline();
            } else {
                bookManagerView.setOutlineMode(false);
            }
            refreshToolbarState();
        });
        replaceCenterBtn(outlineTreeBtn, next);
        outlineTreeBtn = next;
    }

    private void updateVolChapBtn(boolean active) {
        Button next = active ? IconButtons.volChapOutlineActiveButton() : IconButtons.volChapOutlineButton();
        tip(next, "分卷/章节细纲模式", "bottom");
        next.setOnAction(e -> {
            if (bookManagerView.isOutlineVisible() && bookManagerView.isVolChapOutlineMode()) {
                bookManagerView.exitOutline();
            } else {
                bookManagerView.setOutlineMode(true);
            }
            refreshToolbarState();
        });
        replaceCenterBtn(volChapOutlineBtn, next);
        volChapOutlineBtn = next;
    }

    private void replaceCenterBtn(Button oldBtn, Button newBtn) {
        int idx = toolbarCenter.getChildren().indexOf(oldBtn);
        if (idx >= 0) toolbarCenter.getChildren().set(idx, newBtn);
    }

    /** 自定义浮层，替换 JavaFX Tooltip 以消除底层黑框问题 */
    private static void tip(Button btn, String text, String anchor) {
        TooltipPopup tp = new TooltipPopup(text);
        btn.setOnMouseEntered(e -> {
            switch (anchor) {
                case "right" -> tp.showAtRight(btn);
                case "bottom-right" -> tp.showAtBottomRight(btn);
                default -> tp.showAtBottom(btn);
            }
        });
        btn.setOnMouseExited(e -> tp.hide());
    }

    private static void setLeftBtn(Button btn, boolean visible) {
        btn.setVisible(visible);
        btn.setManaged(visible);
    }
}
