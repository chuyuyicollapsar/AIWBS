package example.aiwbs.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import example.aiwbs.ai.AiClient;
import example.aiwbs.ai.ToolDefinitions;
import example.aiwbs.model.AiConfig;
import example.aiwbs.model.AiMessage;
import example.aiwbs.model.AiSession;
import example.aiwbs.model.AppState;
import example.aiwbs.model.Book;
import example.aiwbs.storage.AiSessionStore;
import example.aiwbs.storage.StateStore;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI chat session view.
 */
public class AiChatView {
    private static final String SYSTEM_PROMPT =
            "You are a professional writing assistant. You have access to the user's book via tools "
            + "(get_table_of_contents, get_outline_tree, get_volume_outline, get_chapter_outline, get_chapter_content). "
            + "First get the table of contents or outline to understand the book, then drill into details as needed. "
            + "IMPORTANT: get_outline_tree returns a JSON tree. The `children` array is the ONLY indicator of parent-child "
            + "relationships. A node's `content` is plain text that may contain its own internal sub-headings "
            + "(e.g. ## 閼冲本娅欑拋鎯х暰), but those are NOT separate nodes in the tree. "
            + "Provide creative suggestions, plot ideas, character development, and editing advice.";

    private final BorderPane root = new BorderPane();
    private final AiSessionStore store;
    private final AppState appState;
    private final StateStore stateStore;
    private final AiClient aiClient = new AiClient();
    private final Book book;

    // Panels
    private final VBox sessionPanel = new VBox(8);
    private final VBox navPanel = new VBox(8);
    private final VBox messageArea = new VBox(12);
    private VBox leftBar;
    private ScrollPane messageScroll;
    private TextArea inputField;
    private Button sendButton;
    private Label sessionTitle;
    private boolean bottomScrollScheduled;
    private int pendingBottomScrollPulses;

    // State
    private AiSession currentSession;
    private final Map<String, String> branchSel = new HashMap<>();
    private final Set<String> pendingAssistantNodes = new HashSet<>();
    private List<AiMessage> currentPath = new ArrayList<>();
    private boolean sessionVis = true;
    private boolean navVis = true;
    private boolean pathNodeVis = true;
    private int pathNodeFocusedIdx = -1;
    private VBox pathNodePanel;
    private Runnable onToggle;

    private static final DateTimeFormatter TTL = DateTimeFormatter.ofPattern("yy.MM.dd.HH.mm");

    public AiChatView(AppState appState, StateStore stateStore, Book book) {
        this.appState = appState;
        this.stateStore = stateStore;
        this.book = book;
        this.store = new AiSessionStore(book.getId());
        buildUI();
        loadSessions();
    }

    public Parent getRoot() { return root; }
    public void setOnPanelToggle(Runnable r) { onToggle = r; }
    public boolean isSessionPanelVisible() { return sessionVis; }
    public boolean isNavTreeVisible() { return navVis; }

    public void toggleSessionPanel() {
        sessionVis = !sessionVis;
        sessionPanel.setVisible(sessionVis);
        sessionPanel.setManaged(sessionVis);
        refreshLeftBar();
        if (onToggle != null) onToggle.run();
    }

    public void toggleNavTree() {
        navVis = !navVis;
        navPanel.setVisible(navVis);
        navPanel.setManaged(navVis);
        refreshLeftBar();
        if (onToggle != null) onToggle.run();
    }

    public boolean isPathNodeVisible() { return pathNodeVis; }

    public void togglePathNodePanel() {
        pathNodeVis = !pathNodeVis;
        pathNodePanel.setVisible(pathNodeVis);
        pathNodePanel.setManaged(pathNodeVis);
        refreshLeftBar();
        if (onToggle != null) onToggle.run();
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜
    //  Layout
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜

    private void buildUI() {
        root.setStyle("-fx-background-color: #0f1730;");

        buildSessionPanel();
        buildNavPanel();
        buildPathNodePanel();
        BorderPane conv = buildConvPanel();
        leftBar = (VBox) buildLeftBar();

        HBox center = new HBox(0);
        center.getChildren().addAll(leftBar, sessionPanel, navPanel, pathNodePanel, conv);
        HBox.setHgrow(conv, Priority.ALWAYS);
        root.setCenter(center);
    }

    /** Vertical left bar with session and nav-tree toggle buttons. */
    private Parent buildLeftBar() {
        VBox bar = new VBox(6);
        bar.setPadding(new Insets(8, 4, 8, 4));
        bar.setStyle("-fx-background-color: #111a34; -fx-border-color: #223055; -fx-border-width: 0 1 0 0;");
        bar.setAlignment(javafx.geometry.Pos.TOP_CENTER);

        boolean sVis = sessionVis;
        Button sBtn = sVis ? IconButtons.sessionToggleActiveButton() : IconButtons.sessionToggleButton();
        ViewUtils.tip(sBtn, "Session list", "right");
        sBtn.setOnAction(e -> { toggleSessionPanel(); refreshLeftBar(); });

        boolean nVis = navVis;
        Button nBtn = nVis ? IconButtons.branchNavigatorActiveButton() : IconButtons.branchNavigatorButton();
        ViewUtils.tip(nBtn, "Branch navigator", "right");
        nBtn.setOnAction(e -> { toggleNavTree(); refreshLeftBar(); });

        boolean pVis = pathNodeVis;
        Button pBtn = pVis ? IconButtons.pathNodeNavigatorActiveButton() : IconButtons.pathNodeNavigatorButton();
        ViewUtils.tip(pBtn, "Path navigator", "right");
        pBtn.setOnAction(e -> { togglePathNodePanel(); refreshLeftBar(); });

        bar.getChildren().addAll(sBtn, nBtn, pBtn);
        return bar;
    }

    private void refreshLeftBar() {
        if (leftBar == null) return;
        leftBar.getChildren().clear();
        boolean sVis = sessionVis;
        Button sBtn = sVis ? IconButtons.sessionToggleActiveButton() : IconButtons.sessionToggleButton();
        ViewUtils.tip(sBtn, "Session list", "right");
        sBtn.setOnAction(e -> { toggleSessionPanel(); refreshLeftBar(); });
        boolean nVis = navVis;
        Button nBtn = nVis ? IconButtons.branchNavigatorActiveButton() : IconButtons.branchNavigatorButton();
        ViewUtils.tip(nBtn, "Branch navigator", "right");
        nBtn.setOnAction(e -> { toggleNavTree(); refreshLeftBar(); });
        boolean pVis = pathNodeVis;
        Button pBtn = pVis ? IconButtons.pathNodeNavigatorActiveButton() : IconButtons.pathNodeNavigatorButton();
        ViewUtils.tip(pBtn, "Path navigator", "right");
        pBtn.setOnAction(e -> { togglePathNodePanel(); refreshLeftBar(); });
        leftBar.getChildren().addAll(sBtn, nBtn, pBtn);
    }

    private void buildSessionPanel() {
        sessionPanel.setPrefWidth(200);
        sessionPanel.setMinWidth(0);
        sessionPanel.setPadding(new Insets(12));
        sessionPanel.setStyle("-fx-background-color: #111a34; -fx-border-color: #223055; -fx-border-width: 0 1 0 0;");

        Label title = new Label("Session list - " + book.getName());
        title.setWrapText(true);
        title.setStyle("-fx-text-fill: #fff; -fx-font-size: 15px; -fx-font-weight: bold;");

        Button newBtn = new Button("+ New Session");
        newBtn.setMaxWidth(Double.MAX_VALUE);
        newBtn.setOnAction(e -> promptNew());

        VBox.setVgrow(sessionPanel, Priority.ALWAYS);
        sessionPanel.getChildren().addAll(title, newBtn);
    }

    private void buildNavPanel() {
        navPanel.setPrefWidth(200);
        navPanel.setMinWidth(0);
        navPanel.setPadding(new Insets(12));
        navPanel.setStyle("-fx-background-color: #111a34; -fx-border-color: #223055; -fx-border-width: 0 1 0 0;");
    }

    private void buildPathNodePanel() {
        pathNodePanel = new VBox(8);
        pathNodePanel.setPrefWidth(200);
        pathNodePanel.setMinWidth(0);
        pathNodePanel.setPadding(new Insets(12));
        pathNodePanel.setStyle("-fx-background-color: #111a34; -fx-border-color: #223055; -fx-border-width: 0 1 0 0;");
    }

    private BorderPane buildConvPanel() {
        BorderPane area = new BorderPane();

        sessionTitle = new Label("AI Conversation");
        sessionTitle.setStyle("-fx-text-fill: #fff; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 12 16 4 16;");
        sessionTitle.setMaxWidth(Double.MAX_VALUE);

        messageArea.setPadding(new Insets(16));
        messageArea.setFillWidth(true);
        messageArea.setStyle("-fx-background-color: #0f1730;");

        messageScroll = new ScrollPane(messageArea);
        messageScroll.setFitToWidth(true);
        messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScroll.setStyle("-fx-background: transparent; -fx-background-color: #0f1730; -fx-border-color: transparent; -fx-background-insets: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        messageScroll.setFocusTraversable(false);
        messageArea.heightProperty().addListener((obs, oldHeight, newHeight) -> {
            if (pendingBottomScrollPulses > 0) {
                pendingBottomScrollPulses = Math.max(pendingBottomScrollPulses, 2);
                scheduleBottomScroll();
            }
        });

        StackPane frame = new StackPane(messageScroll);
        frame.setStyle("-fx-background-color: #0f1730;");
        frame.setFocusTraversable(false);
        VBox.setVgrow(messageScroll, Priority.ALWAYS);

        Label ph = new Label("Select or create a session to start");
        ph.setStyle("-fx-text-fill: rgba(255,255,255,0.40); -fx-font-size: 16px;");
        ph.setAlignment(Pos.CENTER);
        StackPane php = new StackPane(ph);
        php.setPrefHeight(400);
        messageArea.getChildren().add(php);

        HBox input = buildInput();
        input.setPadding(new Insets(12, 16, 16, 16));

        area.setTop(sessionTitle);
        area.setCenter(frame);
        area.setBottom(input);
        return area;
    }

    private HBox buildInput() {
        inputField = new TextArea();
        inputField.setPromptText("Type a message閳?(Enter to send)");
        inputField.setWrapText(true);
        inputField.setPrefRowCount(1);
        inputField.setMaxHeight(120);
        inputField.setStyle("-fx-font-size: 14px; -fx-padding: 8 14; -fx-background-radius: 12;");

        sendButton = new Button("Send");
        sendButton.setStyle("-fx-font-size: 14px; -fx-padding: 8 20;");
        sendButton.setOnAction(e -> send());

        inputField.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                if (e.isShiftDown()) {
                    inputField.insertText(inputField.getCaretPosition(), "\n");
                } else {
                    send();
                }
            }
        });

        HBox.setHgrow(inputField, Priority.ALWAYS);
        HBox box = new HBox(10, inputField, sendButton);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜
    //  Session Management
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜

    private void loadSessions() {
        while (sessionPanel.getChildren().size() > 2) {
            sessionPanel.getChildren().remove(sessionPanel.getChildren().size() - 1);
        }

        List<AiSession> sessions = store.loadAll();
        ScrollPane sp = new ScrollPane();
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);

        VBox list = new VBox(4);
        for (AiSession s : sessions) {
            Button btn = new Button(s.getTitle());
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            boolean active = currentSession != null && currentSession.getId().equals(s.getId());
            btn.setStyle(active
                    ? "-fx-background-color: #d7bb74; -fx-text-fill: #11182d;"
                    : "-fx-background-color: transparent; -fx-text-fill: #e7ecff;");

            btn.setOnAction(e -> selectSession(s));
            btn.setOnContextMenuRequested(ev -> {
                ContextMenu m = new ContextMenu();
                MenuItem rn = new MenuItem("Rename");
                rn.setOnAction(x -> rename(s));
                MenuItem del = new MenuItem("Delete");
                del.setOnAction(x -> delete(s));
                m.getItems().addAll(rn, del);
                m.show(btn, ev.getScreenX(), ev.getScreenY());
            });
            list.getChildren().add(btn);
        }
        sp.setContent(list);
        sessionPanel.getChildren().add(sp);

        if (currentSession == null && !sessions.isEmpty()) {
            selectSession(sessions.getFirst());
        } else if (currentSession != null) {
            refreshNav();
            refreshMessages();
        }
    }

    private void selectSession(AiSession s) {
        currentSession = s;
        branchSel.clear();
        pendingAssistantNodes.clear();
        pathNodeFocusedIdx = 0;
        loadSessions();
        scrollToMessageNode(0);
    }

    private void promptNew() {
        Dialog<String> d = ViewUtils.singleLineInputDialog("New Session", "New Session");
        d.showAndWait().ifPresent(name -> {
            if (name.isBlank()) name = "New Session";
            AiSession s = new AiSession(name);
            store.save(s);
            selectSession(s);
        });
    }

    private void rename(AiSession s) {
        Dialog<String> d = ViewUtils.singleLineInputDialog("Rename", s.getTitle());
        d.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                s.setTitle(name);
                store.save(s);
                loadSessions();
            }
        });
    }

    private void delete(AiSession s) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete \"" + s.getTitle() + "\"?",
                ButtonType.OK, ButtonType.CANCEL);
        ViewUtils.styleDialog(a.getDialogPane());
        a.showAndWait().filter(ButtonType.OK::equals).ifPresent(ok -> {
            store.delete(s);
            if (currentSession != null && currentSession.getId().equals(s.getId())) {
                currentSession = null;
                branchSel.clear();
                pendingAssistantNodes.clear();
                List<AiSession> rem = store.loadAll();
                if (!rem.isEmpty()) selectSession(rem.getFirst());
                else { loadSessions(); refreshNav(); refreshMessages(); }
            } else loadSessions();
        });
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜
    //  Branch Navigator
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜

    private void refreshNav() {
        navPanel.getChildren().clear();
        Label title = new Label("Branch Navigator");
        title.setStyle("-fx-text-fill: #fff; -fx-font-size: 15px; -fx-font-weight: bold;");
        navPanel.getChildren().add(title);
        if (currentSession == null) return;

        initDefaults(currentSession.getRootMessages());
        refreshPath();

        VBox list = new VBox(4);
        for (AiMessage n : currentPath) {
            if (n.getChildren().size() >= 2) {
                list.getChildren().add(buildBranchGroup(n));
            }
        }
        if (list.getChildren().isEmpty()) {
            Label e = new Label("(no branches yet)");
            e.setStyle("-fx-text-fill: rgba(255,255,255,0.35); -fx-font-size: 12px; -fx-padding: 8 0;");
            list.getChildren().add(e);
        }

        ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);
        navPanel.getChildren().add(sp);
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜
    //  Path Node Navigator
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜

    private void refreshPathNodePanel() {
        pathNodePanel.getChildren().clear();
        Label title = new Label("Path Navigator");
        title.setStyle("-fx-text-fill: #fff; -fx-font-size: 15px; -fx-font-weight: bold;");
        pathNodePanel.getChildren().add(title);

        if (currentPath.isEmpty()) {
            Label e = new Label("(no messages yet)");
            e.setStyle("-fx-text-fill: rgba(255,255,255,0.35); -fx-font-size: 12px; -fx-padding: 8 0;");
            pathNodePanel.getChildren().add(e);
            return;
        }

        VBox list = new VBox(4);
        for (int i = 0; i < currentPath.size(); i++) {
            AiMessage node = currentPath.get(i);
            int idx = i;

            boolean isActive = pathNodeFocusedIdx == idx;

            String nodeTitle = node.getTitle() == null || node.getTitle().isBlank() ? "(unnamed)" : node.getTitle();

            // Fork marker: this node has multiple children -> "->n"
            String forkMarker = node.getChildren().size() >= 2 ? "->" + node.getChildren().size() : null;

            // Branch marker: parent has multiple children -> "pos/total"
            String branchMarker = null;
            if (i > 0) {
                AiMessage parent = currentPath.get(i - 1);
                int total = parent.getChildren().size();
                if (total >= 2) {
                    int pos = parent.getChildren().indexOf(node) + 1;
                    branchMarker = pos + "/" + total;
                }
            }

            String btnBg = isActive ? "#d7bb74" : "transparent";
            String btnFg = isActive ? "#11182d" : "#aab";
            String btnBd = isActive ? "#d7bb74" : "#334";
            String markFg = isActive ? "rgba(0,0,0,0.45)" : "rgba(255,255,255,0.55)";
            String btnStyle = "-fx-background-color: " + btnBg + "; -fx-text-fill: " + btnFg
                    + "; -fx-font-size: 11px; -fx-padding: 4 6; -fx-background-radius: 4;"
                    + "; -fx-border-color: " + btnBd + "; -fx-border-radius: 4;";

            Button b = new Button();
            b.setMaxWidth(Double.MAX_VALUE);
            b.setStyle(btnStyle);

            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);

            Label titleLbl = new Label(nodeTitle);
            titleLbl.setStyle("-fx-text-fill: " + btnFg + "; -fx-font-size: 11px;");
            titleLbl.setPrefWidth(95);
            titleLbl.setMinWidth(95);
            titleLbl.setMaxWidth(95);
            titleLbl.setTextOverrun(OverrunStyle.ELLIPSIS);
            row.getChildren().add(titleLbl);

            if (forkMarker != null) {
                Label forkLbl = new Label(forkMarker);
                forkLbl.setStyle("-fx-text-fill: " + markFg + "; -fx-font-size: 10px;");
                row.getChildren().add(forkLbl);
            }

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().add(spacer);

            if (branchMarker != null) {
                Label branchLbl = new Label(branchMarker);
                branchLbl.setStyle("-fx-text-fill: " + markFg + "; -fx-font-size: 10px;");
                row.getChildren().add(branchLbl);
            }

            b.setGraphic(row);

            b.setOnAction(ev -> {
                pathNodeFocusedIdx = idx;
                refreshPathNodePanel();
                scrollToMessageNode(idx);
            });

            ContextMenu ctxMenu = new ContextMenu();
            MenuItem renameItem = new MenuItem("Rename label");
            renameItem.setOnAction(ev -> renameTurnTitle(node));
            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setOnAction(ev -> {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete this node and its children?",
                        ButtonType.YES, ButtonType.NO);
                alert.setTitle("Delete confirmation");
                alert.setHeaderText(null);
                ViewUtils.styleDialog(alert.getDialogPane());
                alert.showAndWait().ifPresent(btn -> {
                    if (btn == ButtonType.YES) deleteTurn(node);
                });
            });
            ctxMenu.getItems().addAll(renameItem, deleteItem);
            b.setOnContextMenuRequested(ev -> {
                ctxMenu.show(b, Side.RIGHT, 0, 0);
                ev.consume();
            });

            list.getChildren().add(b);
        }

        ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);
        pathNodePanel.getChildren().add(sp);
    }

    private void scrollToMessageNode(int index) {
        Platform.runLater(() -> {
            if (index < 0 || index >= messageArea.getChildren().size()) return;
            Node target = messageArea.getChildren().get(index);
            double y = target.getBoundsInParent().getMinY();
            double viewH = messageScroll.getViewportBounds().getHeight();
            double contentH = messageArea.getHeight();
            if (contentH <= viewH) return;
            messageScroll.setVvalue(y / (contentH - viewH));
        });
    }

    private Node buildBranchGroup(AiMessage bp) {
        VBox g = new VBox(3);
        g.setPadding(new Insets(6, 2, 6, 2));

        Label l = new Label(bp.getTitle() == null || bp.getTitle().isBlank() ? "(branch)" : bp.getTitle());
        l.setStyle("-fx-text-fill: #d7bb74; -fx-font-size: 11px; -fx-font-weight: bold;");

        String sel = branchSel.get(bp.getId());
        String bpId = bp.getId();
        for (AiMessage ch : bp.getChildren()) {
            String cid = ch.getId();
            boolean s = cid.equals(sel);
            String d = ch.getTitle() == null || ch.getTitle().isBlank() ? "(unnamed)" : ch.getTitle();
            if (d.length() > 18) d = d.substring(0, 18) + "...";

            Button b = new Button((s ? "* " : "  ") + d);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            b.setStyle(s
                    ? "-fx-background-color: #d7bb74; -fx-text-fill: #11182d; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4;"
                    : "-fx-background-color: transparent; -fx-text-fill: #aab; -fx-font-size: 11px; -fx-padding: 2 6; -fx-border-color: #334; -fx-border-radius: 4;");
            b.setOnAction(ev -> {
                branchSel.put(bpId, cid);
                pathNodeFocusedIdx = 0;
                refreshNav();
                refreshMessages();
                scrollToMessageNode(0);
            });
            g.getChildren().add(b);
        }
        g.getChildren().addFirst(l);
        return g;
    }

    private void initDefaults(List<AiMessage> roots) {
        for (AiMessage r : roots) initDefaults(r);
    }

    private void initDefaults(AiMessage n) {
        if (n.getChildren().size() >= 2 && !branchSel.containsKey(n.getId())) {
            branchSel.put(n.getId(), n.getChildren().getFirst().getId());
        }
        for (AiMessage c : n.getChildren()) initDefaults(c);
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜
    //  Path Building
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜

    private List<AiMessage> selectedPath() {
        List<AiMessage> p = new ArrayList<>();
        for (AiMessage r : currentSession.getRootMessages()) {
            followPath(r, p);
        }
        return p;
    }

    private void refreshPath() {
        currentPath = selectedPath();
    }

    private void followPath(AiMessage n, List<AiMessage> out) {
        out.add(n);
        List<AiMessage> ch = n.getChildren();
        if (ch.isEmpty()) return;
        if (ch.size() == 1) { followPath(ch.getFirst(), out); return; }
        String picked = branchSel.get(n.getId());
        for (AiMessage c : ch) {
            if (c.getId().equals(picked)) { followPath(c, out); return; }
        }
        followPath(ch.getFirst(), out);
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜
    //  Message Display
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜

    private void refreshMessages() {
        messageArea.getChildren().clear();
        if (currentSession == null) {
            Label ph = new Label("Select or create a session to start");
            ph.setStyle("-fx-text-fill: rgba(255,255,255,0.40); -fx-font-size: 16px;");
            ph.setAlignment(Pos.CENTER);
            StackPane p = new StackPane(ph);
            p.setPrefHeight(400);
            messageArea.getChildren().add(p);
            sessionTitle.setText("AI Conversation");
            inputField.setDisable(true);
            sendButton.setDisable(true);
            currentPath.clear();
            refreshPathNodePanel();
            return;
        }
        sessionTitle.setText(currentSession.getTitle());
        updateComposerState();

        List<AiMessage> path = currentPath;
        if (path.isEmpty()) {
            Label h = new Label("Type a message below to start.");
            h.setStyle("-fx-text-fill: rgba(255,255,255,0.40); -fx-font-size: 14px; -fx-padding: 20;");
            h.setWrapText(true);
            messageArea.getChildren().add(h);
        } else {
            for (int i = 0; i < path.size(); i++) {
                AiMessage n = path.get(i);

                Label hdr = new Label(n.getTitle() == null || n.getTitle().isBlank() ? "(unnamed)" : n.getTitle());
                hdr.setStyle("-fx-text-fill: #d7bb74; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 0 2 0;");

                Separator sep = new Separator();
                sep.setStyle("-fx-background: rgba(215,187,116,0.3);");

                VBox block = new VBox(6);
                block.getChildren().addAll(hdr, sep);
                block.setUserData(i);

                if (n.getUserContent() != null && !n.getUserContent().isBlank()) {
                    block.getChildren().add(createBubble(n.getUserContent(), true, n));
                }
                if (n.getAssistantContent() != null && !n.getAssistantContent().isBlank()) {
                    block.getChildren().add(createAssistantBubble(n));
                } else if (pendingAssistantNodes.contains(n.getId())) {
                    block.getChildren().add(createBubble("AI thinking...", false, n));
                }
                messageArea.getChildren().add(block);
            }
        }
        refreshPathNodePanel();
    }

    private void updateComposerState() {
        boolean disabled = currentSession == null || !pendingAssistantNodes.isEmpty();
        inputField.setDisable(disabled);
        sendButton.setDisable(disabled);
        sendButton.setText(!pendingAssistantNodes.isEmpty() ? "AI thinking..." : "Send");
    }

    private Node createBubble(String text, boolean isUser, AiMessage node) {
        String bg = isUser ? "rgba(36, 60, 124, 0.85)" : "rgba(25, 35, 70, 0.85)";
        String fg = isUser ? "#edf2ff" : "#e7ecff";
        Color c = Color.web(fg);

        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(560);
        label.setStyle("-fx-text-fill: " + fg + "; -fx-font-size: 14px;");

        StackPane bubble = new StackPane(label);
        bubble.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 12; -fx-padding: 10 14;");
        bubble.setMaxWidth(600);

        HBox bar = new HBox();
        bar.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        bar.setPadding(new Insets(2, 0, 0, 0));
        if (isUser) {
            bar.getChildren().add(iconBtn(IconButtons.insertButton(c), e -> insertCustomTurn(node)));
            boolean isRoot = currentSession.getRootMessages().contains(node);
            if (!isRoot && node.getUserContent() != null && !node.getUserContent().isBlank()) {
                bar.getChildren().add(iconBtn(IconButtons.editButton(c), e -> editAndResendTurn(node)));
            }
            bar.getChildren().add(iconBtn(IconButtons.copyButton(c), e -> {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(text);
                Clipboard.getSystemClipboard().setContent(cc);
            }));
        } else {
            bar.getChildren().add(iconBtn(IconButtons.copyButton(c), e -> {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(text);
                Clipboard.getSystemClipboard().setContent(cc);
            }));
            bar.getChildren().add(iconBtn(IconButtons.insertButton(c), e -> insertCustomTurn(node)));
        }

        VBox col = new VBox(0, bubble, bar);

        HBox wrap = new HBox(col);
        wrap.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        wrap.setPadding(new Insets(2, 16, 2, 16));
        HBox.setHgrow(col, Priority.NEVER);
        return wrap;
    }

    private static Button iconBtn(Button btn, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        btn.setOpacity(0.35);
        btn.setOnMouseEntered(e -> btn.setOpacity(1));
        btn.setOnMouseExited(e -> btn.setOpacity(0.35));
        btn.setOnAction(handler);
        return btn;
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜
    //  Send Message
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜

    private void send() {
        String text = inputField.getText();
        if (text == null || text.isBlank() || currentSession == null) return;

        AiSession session = currentSession;
        List<AiMessage> ctx = new ArrayList<>(currentPath);
        AiMessage node = new AiMessage(makeTitle());
        node.setUserContent(text);
        pendingAssistantNodes.add(node.getId());
        inputField.clear();
        if (!ctx.isEmpty()) {
            AiMessage p = ctx.getLast();
            p.getChildren().add(node);
            if (p.getChildren().size() >= 2) branchSel.put(p.getId(), node.getId());
        } else {
            currentSession.getRootMessages().add(node);
        }

        refreshPath();
        pathNodeFocusedIdx = currentPath.size() - 1;
        List<AiMessage> apiCtx = new ArrayList<>(ctx);
        apiCtx.add(node);

        refreshNav();
        refreshMessages();
        scrollToBottom();

        AiConfig cfg = appState.getAiConfig();
        List<String> messages = flatten(apiCtx, cfg);
        String toolsJson = ToolDefinitions.getToolsJson();

        new Thread(() -> {
            try {
                AssistantReply resp = callWithTools(cfg, SYSTEM_PROMPT, messages, toolsJson, 0);
                Platform.runLater(() -> {
                    node.setAssistantContent(resp.content());
                    node.setAssistantReasoningContent(resp.reasoningContent());
                    pendingAssistantNodes.remove(node.getId());
                    store.save(session);
                    if (isCurrentSession(session)) {
                        refreshNav();
                        refreshMessages();
                        scrollToBottom();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    String m = e.getMessage();
                    node.setAssistantContent("[Error] " + (m != null ? m : "unknown"));
                    pendingAssistantNodes.remove(node.getId());
                    store.save(session);
                    if (isCurrentSession(session)) {
                        refreshNav();
                        refreshMessages();
                        scrollToBottom();
                    }
                });
            }
        }).start();
    }

    /**
     * Recursive chat loop with tool calls, max 10 rounds.
    */
    private AssistantReply callWithTools(AiConfig cfg, String sys, List<String> messages,
                                         String toolsJson, int depth) throws Exception {
        if (depth > 10) return new AssistantReply("[Error] Tool call loop exceeded max depth", "");

        String resp = aiClient.chatRaw(cfg, sys, messages, toolsJson);
        JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
        JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
        String finishReason = choice.get("finish_reason").getAsString();
        JsonObject msg = choice.getAsJsonObject("message");

        if (!"tool_calls".equals(finishReason)) {
            return new AssistantReply(extractMessageContent(msg.get("content")), extractReasoningContent(msg));
        }

        messages.add(msg.toString());

        JsonArray toolCalls = msg.getAsJsonArray("tool_calls");
        for (JsonElement tc : toolCalls) {
            JsonObject toolCall = tc.getAsJsonObject();
            String toolCallId = toolCall.get("id").getAsString();
            String funcName = toolCall.getAsJsonObject("function").get("name").getAsString();
            String argsStr = toolCall.getAsJsonObject("function").get("arguments").getAsString();
            String result = executeTool(funcName, argsStr);
            messages.add("{\"role\":\"tool\",\"tool_call_id\":\"" + toolCallId
                    + "\",\"content\":" + jsonStr(result) + "}");
        }

        return callWithTools(cfg, sys, messages, toolsJson, depth + 1);
    }


    /**
     * Execute a book tool call and return the result string.
     */
    private String executeTool(String name, String argsJson) {
        try {
            JsonObject args = JsonParser.parseString(argsJson).getAsJsonObject();
            return switch (name) {
                case "get_table_of_contents" -> book.getTableOfContents();
                case "get_outline_tree" -> book.getOutlineTreeString();
                case "get_volume_outline" ->
                    book.getVolumeOutline(requiredStringArg(args, "volume_name"));
                case "get_chapter_outline" ->
                    book.getChapterOutline(requiredStringArg(args, "volume_name"),
                            requiredStringArg(args, "chapter_name"));
                case "get_chapter_content" ->
                    book.getChapterContent(requiredStringArg(args, "volume_name"),
                            requiredStringArg(args, "chapter_name"));
                default -> "[Error] Unknown tool: " + name;
            };
        } catch (Exception e) {
            return "[Error executing " + name + "] " + e.getMessage();
        }
    }

    private String requiredStringArg(JsonObject args, String name) {
        if (!args.has(name) || args.get(name).isJsonNull()) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return args.get(name).getAsString();
    }


    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜
    //  Branch Operations
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜

    private void editAndResendTurn(AiMessage node) {
        Dialog<String> d = ViewUtils.multiLineInputDialog("Edit & Resend", node.getUserContent());
        d.showAndWait().ifPresent(newText -> {
            if (newText.isBlank()) return;

            AiSession session = currentSession;
            AiMessage sib = new AiMessage(makeTitle());
            sib.setUserContent(newText);
            pendingAssistantNodes.add(sib.getId());

            AiMessage parent = findParent(node);
            if (parent != null) {
                parent.getChildren().add(sib);
                if (parent.getChildren().size() >= 2) branchSel.put(parent.getId(), sib.getId());
            } else {
                int idx = currentSession.getRootMessages().indexOf(node);
                currentSession.getRootMessages().add(idx + 1, sib);
            }

            refreshPath();
            pathNodeFocusedIdx = currentPath.size() - 1;
            refreshNav();
            refreshMessages();
            scrollToBottom();

            AiConfig cfg = appState.getAiConfig();
            List<AiMessage> ctx = currentPath;
            List<String> json = flatten(ctx, cfg);
            String toolsJson = ToolDefinitions.getToolsJson();
            new Thread(() -> {
                try {
                    AssistantReply resp = callWithTools(cfg, SYSTEM_PROMPT, json, toolsJson, 0);
                    Platform.runLater(() -> {
                        sib.setAssistantContent(resp.content());
                        sib.setAssistantReasoningContent(resp.reasoningContent());
                        pendingAssistantNodes.remove(sib.getId());
                        store.save(session);
                        if (isCurrentSession(session)) {
                            refreshNav();
                            refreshMessages();
                            scrollToBottom();
                        }
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        String m = e.getMessage();
                        sib.setAssistantContent("[Error] " + (m != null ? m : "unknown"));
                        pendingAssistantNodes.remove(sib.getId());
                        store.save(session);
                        if (isCurrentSession(session)) {
                            refreshNav();
                            refreshMessages();
                        }
                    });
                }
            }).start();
        });
    }

    private void insertCustomTurn(AiMessage node) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Insert Custom Pair");
        d.setHeaderText("Insert Custom Pair");
        TextArea uf = new TextArea();
        uf.setPromptText("User message");
        uf.setPrefRowCount(3);
        uf.setPrefColumnCount(40);
        uf.setWrapText(true);
        TextArea af = new TextArea();
        af.setPromptText("Assistant response");
        af.setPrefRowCount(3);
        af.setPrefColumnCount(40);
        af.setWrapText(true);
        for (TextArea t : new TextArea[]{uf, af}) {
            ViewUtils.installDialogTextAreaKeys(d, t);
        }
        d.getDialogPane().setContent(new VBox(8, new Label("User:"), uf, new Label("Assistant:"), af));
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ViewUtils.styleDialog(d.getDialogPane());
        d.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            String u = uf.getText();
            String a = af.getText();
            if (u.isBlank() && a.isBlank()) return;
            AiMessage c = new AiMessage(makeTitle());
            if (!u.isBlank()) c.setUserContent(u);
            if (!a.isBlank()) c.setAssistantContent(a);
            node.getChildren().add(c);
            if (node.getChildren().size() >= 2) branchSel.put(node.getId(), c.getId());
            store.save(currentSession);
            refreshNav();
            pathNodeFocusedIdx = currentPath.size() - 1;
            refreshMessages();
            scrollToBottom();
        });
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜
    //  Turn Operations
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜

    private void deleteTurn(AiMessage node) {
        if (currentSession == null) return;
        AiMessage parent = findParent(node);
        if (parent != null) {
            parent.getChildren().remove(node);
            if (parent.getChildren().size() <= 1) {
                branchSel.remove(parent.getId());
            }
        } else {
            currentSession.getRootMessages().remove(node);
        }
        branchSel.remove(node.getId());
        store.save(currentSession);
        refreshNav();
        pathNodeFocusedIdx = Math.min(pathNodeFocusedIdx, currentPath.size() - 1);
        if (pathNodeFocusedIdx < 0 && !currentPath.isEmpty()) pathNodeFocusedIdx = 0;
        refreshMessages();
    }

    private void renameTurnTitle(AiMessage node) {
        if (currentSession == null) return;
        Dialog<String> d = ViewUtils.singleLineInputDialog("Rename Turn", node.getTitle());
        d.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                node.setTitle(name);
                store.save(currentSession);
                refreshNav();
                refreshMessages();
            }
        });
    }

    private AiMessage findParent(AiMessage target) {
        if (currentSession == null) return null;
        for (AiMessage r : currentSession.getRootMessages()) {
            if (r == target) return null;
            AiMessage f = findParent(r, target);
            if (f != null) return f;
        }
        return null;
    }

    private AiMessage findParent(AiMessage n, AiMessage target) {
        for (AiMessage c : n.getChildren()) {
            if (c == target) return n;
            AiMessage f = findParent(c, target);
            if (f != null) return f;
        }
        return null;
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜
    //  Helpers
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜

    private List<String> flatten(List<AiMessage> nodes, AiConfig cfg) {
        List<String> r = new ArrayList<>();
        for (AiMessage n : nodes) {
            if (n.getUserContent() != null && !n.getUserContent().isBlank()) {
                r.add("{\"role\":\"user\",\"content\":" + jsonStr(n.getUserContent()) + "}");
            }
            if (n.getAssistantContent() != null && !n.getAssistantContent().isBlank()
                    && !n.getAssistantContent().startsWith("[Error]")) {
                r.add(assistantMessageJson(n, cfg));
            }
        }
        return r;
    }

    private String assistantMessageJson(AiMessage node, AiConfig cfg) {
        StringBuilder b = new StringBuilder();
        b.append("{\"role\":\"assistant\",\"content\":").append(jsonStr(node.getAssistantContent()));
        if (isDeepSeek(cfg) && node.getAssistantReasoningContent() != null && !node.getAssistantReasoningContent().isBlank()) {
            b.append(",\"reasoning_content\":").append(jsonStr(node.getAssistantReasoningContent()));
        }
        b.append("}");
        return b.toString();
    }

    private boolean isDeepSeek(AiConfig cfg) {
        return cfg != null && cfg.isUseOfficialApi()
                && cfg.resolveOfficialProvider() == AiConfig.OfficialProvider.DEEPSEEK;
    }

    private String extractMessageContent(JsonElement contentEl) {
        if (contentEl == null || contentEl.isJsonNull()) return "";
        if (contentEl.isJsonPrimitive()) return contentEl.getAsString();
        return contentEl.toString();
    }

    private String extractReasoningContent(JsonObject message) {
        if (message == null || !message.has("reasoning_content") || message.get("reasoning_content").isJsonNull()) {
            return "";
        }
        return message.get("reasoning_content").getAsString();
    }

    private Node createAssistantBubble(AiMessage node) {
        VBox col = new VBox(6);
        col.getChildren().add(createBubble(node.getAssistantContent(), false, node));

        String reasoning = node.getAssistantReasoningContent();
        if (reasoning != null && !reasoning.isBlank()) {
            VBox reasoningBox = new VBox(6);
            reasoningBox.setVisible(false);
            reasoningBox.setManaged(false);
            reasoningBox.setStyle("-fx-background-color: rgba(15, 23, 48, 0.72); -fx-background-radius: 10; -fx-padding: 10 12;");

            Label title = new Label("Thinking");
            title.setStyle("-fx-text-fill: #aab4d6; -fx-font-size: 11px; -fx-font-weight: bold;");

            Label text = new Label(reasoning);
            text.setWrapText(true);
            text.setMaxWidth(560);
            text.setStyle("-fx-text-fill: #c7d0ef; -fx-font-size: 12px;");

            reasoningBox.getChildren().addAll(title, text);

            Button toggle = new Button("Show thinking");
            toggle.setFocusTraversable(false);
            toggle.setStyle("-fx-background-color: transparent; -fx-text-fill: #8ea0d6; -fx-padding: 0 0 0 16; -fx-cursor: hand;");
            toggle.setOnAction(e -> {
                boolean show = !reasoningBox.isVisible();
                reasoningBox.setVisible(show);
                reasoningBox.setManaged(show);
                toggle.setText(show ? "Hide thinking" : "Show thinking");
            });

            col.getChildren().addAll(toggle, reasoningBox);
        }

        HBox wrap = new HBox(col);
        wrap.setAlignment(Pos.CENTER_LEFT);
        wrap.setPadding(new Insets(2, 16, 2, 16));
        return wrap;
    }

    private static final class AssistantReply {
        private final String content;
        private final String reasoningContent;

        private AssistantReply(String content, String reasoningContent) {
            this.content = content;
            this.reasoningContent = reasoningContent;
        }

        private String content() {
            return content;
        }

        private String reasoningContent() {
            return reasoningContent;
        }
    }

    private static String jsonStr(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private boolean isCurrentSession(AiSession session) {
        return currentSession != null && currentSession.getId().equals(session.getId());
    }

    private void scrollToBottom() {
        if (messageScroll == null) return;
        pendingBottomScrollPulses = Math.max(pendingBottomScrollPulses, 2);
        scheduleBottomScroll();
    }

    private void scheduleBottomScroll() {
        if (bottomScrollScheduled || pendingBottomScrollPulses <= 0) return;
        bottomScrollScheduled = true;
        Platform.runLater(() -> {
            bottomScrollScheduled = false;
            if (messageScroll == null) {
                pendingBottomScrollPulses = 0;
                return;
            }
            messageArea.applyCss();
            messageArea.layout();
            messageScroll.applyCss();
            messageScroll.layout();
            messageScroll.setVvalue(1.0);
            pendingBottomScrollPulses--;
            scheduleBottomScroll();
        });
    }

    private static String makeTitle() {
        return LocalDateTime.now().format(TTL);
    }
}
