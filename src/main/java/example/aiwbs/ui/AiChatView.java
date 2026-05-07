package example.aiwbs.ui;

import example.aiwbs.ai.AiClient;
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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI聊天会话界面。
 */
public class AiChatView {
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
    private boolean sessionVis = true;
    private boolean navVis = true;
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

    // ════════════════════════════════════════
    //  Layout
    // ════════════════════════════════════════

    private void buildUI() {
        root.setStyle("-fx-background-color: #0f1730;");

        buildSessionPanel();
        buildNavPanel();
        BorderPane conv = buildConvPanel();
        leftBar = (VBox) buildLeftBar();

        HBox center = new HBox(0);
        center.getChildren().addAll(leftBar, sessionPanel, navPanel, conv);
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
        ViewUtils.tip(sBtn, "会话列表", "right");
        sBtn.setOnAction(e -> { toggleSessionPanel(); refreshLeftBar(); });

        boolean nVis = navVis;
        Button nBtn = nVis ? IconButtons.navTreeToggleActiveButton() : IconButtons.navTreeToggleButton();
        ViewUtils.tip(nBtn, "对话树", "right");
        nBtn.setOnAction(e -> { toggleNavTree(); refreshLeftBar(); });

        bar.getChildren().addAll(sBtn, nBtn);
        return bar;
    }

    private void refreshLeftBar() {
        if (leftBar == null) return;
        leftBar.getChildren().clear();
        boolean sVis = sessionVis;
        Button sBtn = sVis ? IconButtons.sessionToggleActiveButton() : IconButtons.sessionToggleButton();
        ViewUtils.tip(sBtn, "会话列表", "right");
        sBtn.setOnAction(e -> { toggleSessionPanel(); refreshLeftBar(); });
        boolean nVis = navVis;
        Button nBtn = nVis ? IconButtons.navTreeToggleActiveButton() : IconButtons.navTreeToggleButton();
        ViewUtils.tip(nBtn, "对话树", "right");
        nBtn.setOnAction(e -> { toggleNavTree(); refreshLeftBar(); });
        leftBar.getChildren().addAll(sBtn, nBtn);
    }

    private void buildSessionPanel() {
        sessionPanel.setPrefWidth(220);
        sessionPanel.setMinWidth(0);
        sessionPanel.setPadding(new Insets(12));
        sessionPanel.setStyle("-fx-background-color: #111a34; -fx-border-color: #223055; -fx-border-width: 0 1 0 0;");

        Label title = new Label("Sessions – " + book.getName());
        title.setWrapText(true);
        title.setStyle("-fx-text-fill: #fff; -fx-font-size: 15px; -fx-font-weight: bold;");

        Button newBtn = new Button("+ New Session");
        newBtn.setMaxWidth(Double.MAX_VALUE);
        newBtn.setOnAction(e -> promptNew());

        VBox.setVgrow(sessionPanel, Priority.ALWAYS);
        sessionPanel.getChildren().addAll(title, newBtn);
    }

    private void buildNavPanel() {
        navPanel.setPrefWidth(240);
        navPanel.setMinWidth(0);
        navPanel.setPadding(new Insets(12));
        navPanel.setStyle("-fx-background-color: #111a34; -fx-border-color: #223055; -fx-border-width: 0 1 0 0;");
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
        inputField.setPromptText("Type a message… (Enter to send)");
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

    // ════════════════════════════════════════
    //  Session Management
    // ════════════════════════════════════════

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
        loadSessions();
    }

    private void promptNew() {
        TextInputDialog d = new TextInputDialog("New Session");
        d.setTitle("New Session");
        d.showAndWait().ifPresent(name -> {
            if (name.isBlank()) name = "New Session";
            AiSession s = new AiSession(name);
            store.save(s);
            selectSession(s);
        });
    }

    private void rename(AiSession s) {
        TextInputDialog d = new TextInputDialog(s.getTitle());
        d.setTitle("Rename");
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
        a.showAndWait().filter(ButtonType.OK::equals).ifPresent(ok -> {
            store.delete(s);
            if (currentSession != null && currentSession.getId().equals(s.getId())) {
                currentSession = null;
                branchSel.clear();
                List<AiSession> rem = store.loadAll();
                if (!rem.isEmpty()) selectSession(rem.getFirst());
                else { loadSessions(); refreshNav(); refreshMessages(); }
            } else loadSessions();
        });
    }

    // ════════════════════════════════════════
    //  Branch Navigator
    // ════════════════════════════════════════

    private void refreshNav() {
        navPanel.getChildren().clear();
        Label title = new Label("Branch Navigator");
        title.setStyle("-fx-text-fill: #fff; -fx-font-size: 15px; -fx-font-weight: bold;");
        navPanel.getChildren().add(title);
        if (currentSession == null) return;

        initDefaults(currentSession.getRootMessages());

        VBox list = new VBox(4);
        List<AiMessage> path = selectedPath();
        int depth = 0;
        for (AiMessage n : path) {
            if (n.getChildren().size() >= 2) {
                list.getChildren().add(buildBranchGroup(n, depth));
            }
            depth++;
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

    private Node buildBranchGroup(AiMessage bp, int depth) {
        VBox g = new VBox(3);
        g.setPadding(new Insets(6, 2, 6, 2 + depth * 14));

        Label l = new Label(bp.getTitle() == null || bp.getTitle().isBlank() ? "(branch)" : bp.getTitle());
        l.setStyle("-fx-text-fill: #d7bb74; -fx-font-size: 11px; -fx-font-weight: bold;");

        String sel = branchSel.get(bp.getId());
        String bpId = bp.getId();
        for (AiMessage ch : bp.getChildren()) {
            String cid = ch.getId();
            boolean s = cid.equals(sel);
            String d = ch.getTitle() == null || ch.getTitle().isBlank() ? "(unnamed)" : ch.getTitle();
            if (d.length() > 18) d = d.substring(0, 18) + "…";

            Button b = new Button((s ? "✓ " : "  ") + d);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            b.setStyle(s
                    ? "-fx-background-color: #d7bb74; -fx-text-fill: #11182d; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4;"
                    : "-fx-background-color: transparent; -fx-text-fill: #aab; -fx-font-size: 11px; -fx-padding: 2 6; -fx-border-color: #334; -fx-border-radius: 4;");
            b.setOnAction(ev -> {
                branchSel.put(bpId, cid);
                refreshNav();
                refreshMessages();
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

    // ════════════════════════════════════════
    //  Path Building
    // ════════════════════════════════════════

    private List<AiMessage> selectedPath() {
        List<AiMessage> p = new ArrayList<>();
        for (AiMessage r : currentSession.getRootMessages()) {
            followPath(r, p);
        }
        return p;
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

    // ════════════════════════════════════════
    //  Message Display
    // ════════════════════════════════════════

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
            return;
        }
        sessionTitle.setText(currentSession.getTitle());
        inputField.setDisable(false);
        sendButton.setDisable(false);

        List<AiMessage> path = selectedPath();
        if (path.isEmpty()) {
            Label h = new Label("Type a message below to start.");
            h.setStyle("-fx-text-fill: rgba(255,255,255,0.40); -fx-font-size: 14px; -fx-padding: 20;");
            h.setWrapText(true);
            messageArea.getChildren().add(h);
        } else {
            for (AiMessage n : path) {
                Label hdr = new Label(n.getTitle() == null || n.getTitle().isBlank() ? "(unnamed)" : n.getTitle());
                hdr.setStyle("-fx-text-fill: #d7bb74; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 0 2 0;");

                Separator sep = new Separator();
                sep.setStyle("-fx-background: rgba(215,187,116,0.3);");

                VBox block = new VBox(6);
                block.getChildren().addAll(hdr, sep);

                if (n.getUserContent() != null && !n.getUserContent().isBlank()) {
                    block.getChildren().add(createBubble(n.getUserContent(), true, n));
                }
                if (n.getAssistantContent() != null && !n.getAssistantContent().isBlank()) {
                    block.getChildren().add(createBubble(n.getAssistantContent(), false, n));
                }
                messageArea.getChildren().add(block);
            }
        }
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
            bar.getChildren().add(iconBtn(IconButtons.insertButton(c), e -> insertCustom(node)));
            if (node.getUserContent() != null && !node.getUserContent().isBlank()) {
                bar.getChildren().add(iconBtn(IconButtons.editButton(c), e -> editAndResend(node)));
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
            bar.getChildren().add(iconBtn(IconButtons.insertButton(c), e -> insertCustom(node)));
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

    // ════════════════════════════════════════
    //  Send Message
    // ════════════════════════════════════════

    private void send() {
        String text = inputField.getText();
        if (text == null || text.isBlank() || currentSession == null) return;

        inputField.clear();
        inputField.setDisable(true);
        sendButton.setDisable(true);
        sendButton.setText("AI thinking…");

        List<AiMessage> ctx = selectedPath();
        AiMessage node = new AiMessage(makeTitle());
        node.setUserContent(text);
        if (!ctx.isEmpty()) {
            AiMessage p = ctx.getLast();
            p.getChildren().add(node);
            if (p.getChildren().size() >= 2) branchSel.put(p.getId(), node.getId());
        } else {
            currentSession.getRootMessages().add(node);
        }

        String nid = node.getId();
        List<AiMessage> apiCtx = new ArrayList<>(ctx);
        apiCtx.add(node);

        refreshNav();
        refreshMessages();
        scrollToBottom();

        List<String> json = flatten(apiCtx);
        AiConfig cfg = appState.getAiConfig();
        String sys = "You are a professional writing assistant. Help the user write and improve their novel. "
                + "Provide creative suggestions, plot ideas, character development, and editing advice.";

        new Thread(() -> {
            try {
                String resp = aiClient.chat(cfg, sys, json);
                Platform.runLater(() -> {
                    node.setAssistantContent(resp);
                    store.save(currentSession);
                    refreshNav();
                    refreshMessages();
                    scrollToBottom();
                    inputField.setDisable(false);
                    sendButton.setDisable(false);
                    sendButton.setText("Send");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    String m = e.getMessage();
                    node.setAssistantContent("[Error] " + (m != null ? m : "unknown"));
                    store.save(currentSession);
                    refreshNav();
                    refreshMessages();
                    scrollToBottom();
                    inputField.setDisable(false);
                    sendButton.setDisable(false);
                    sendButton.setText("Send");
                });
            }
        }).start();
    }

    // ════════════════════════════════════════
    //  Branch Operations
    // ════════════════════════════════════════

    private void editAndResend(AiMessage node) {
        TextInputDialog d = new TextInputDialog(node.getUserContent());
        d.setTitle("Edit & Resend");
        d.showAndWait().ifPresent(newText -> {
            if (newText.isBlank()) return;

            AiMessage sib = new AiMessage(makeTitle());
            sib.setUserContent(newText);

            AiMessage parent = findParent(node);
            if (parent != null) {
                parent.getChildren().add(sib);
                if (parent.getChildren().size() >= 2) branchSel.put(parent.getId(), sib.getId());
            } else {
                int idx = currentSession.getRootMessages().indexOf(node);
                currentSession.getRootMessages().add(idx + 1, sib);
            }

            refreshNav();
            refreshMessages();
            scrollToBottom();

            List<AiMessage> ctx = selectedPath();
            List<String> json = flatten(ctx);
            AiConfig cfg = appState.getAiConfig();
            String sys = "You are a professional writing assistant. Help the user write and improve their novel. "
                    + "Provide creative suggestions, plot ideas, character development, and editing advice.";
            new Thread(() -> {
                try {
                    String resp = aiClient.chat(cfg, sys, json);
                    Platform.runLater(() -> {
                        sib.setAssistantContent(resp);
                        store.save(currentSession);
                        refreshNav();
                        refreshMessages();
                        scrollToBottom();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        String m = e.getMessage();
                        sib.setAssistantContent("[Error] " + (m != null ? m : "unknown"));
                        store.save(currentSession);
                        refreshNav();
                        refreshMessages();
                    });
                }
            }).start();
        });
    }

    private void insertCustom(AiMessage node) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Insert Custom Pair");
        TextArea uf = new TextArea();
        uf.setPromptText("User message");
        uf.setPrefRowCount(3);
        TextArea af = new TextArea();
        af.setPromptText("Assistant response");
        af.setPrefRowCount(3);
        d.getDialogPane().setContent(new VBox(8, new Label("User:"), uf, new Label("Assistant:"), af));
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            String u = uf.getText();
            String a = af.getText();
            if (u.isBlank() && a.isBlank()) return;
            AiMessage c = new AiMessage(makeTitle());
            if (!u.isBlank()) c.setUserContent(u);
            if (!a.isBlank()) c.setAssistantContent(a);
            node.getChildren().add(c);
            store.save(currentSession);
            refreshNav();
            refreshMessages();
            scrollToBottom();
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

    // ════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════

    private List<String> flatten(List<AiMessage> nodes) {
        List<String> r = new ArrayList<>();
        for (AiMessage n : nodes) {
            if (n.getUserContent() != null && !n.getUserContent().isBlank()) {
                r.add("{\"role\":\"user\",\"content\":" + jsonStr(n.getUserContent()) + "}");
            }
            if (n.getAssistantContent() != null && !n.getAssistantContent().isBlank()
                    && !n.getAssistantContent().startsWith("[Error]")) {
                r.add("{\"role\":\"assistant\",\"content\":" + jsonStr(n.getAssistantContent()) + "}");
            }
        }
        return r;
    }

    private static String jsonStr(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
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
