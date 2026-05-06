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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiConversationView {
    private final BorderPane root = new BorderPane();
    private final AiSessionStore store;
    private final AppState appState;
    private final StateStore stateStore;
    private final AiClient aiClient = new AiClient();
    private final Book book;

    // Panels
    private final VBox sessionPanel = new VBox(8);
    private final VBox navTreePanel = new VBox(8);

    // State
    private AiSession currentSession;
    /** Maps branch-point node ID → selected child ID at that branch. */
    private final Map<String, String> branchSelections = new HashMap<>();
    private boolean sessionPanelVisible = true;
    private boolean navTreeVisible = true;

    // Conversation display
    private VBox messageContainer;
    private ScrollPane messageScrollPane;
    private TextArea inputField;
    private Button sendButton;
    private Label sessionTitleLabel;

    // Callback
    private Runnable onPanelToggle;

    private static final DateTimeFormatter TITLE_FMT = DateTimeFormatter.ofPattern("yy.MM.dd.HH.mm");

    public AiConversationView(AppState appState, StateStore stateStore, Book book) {
        this.appState = appState;
        this.stateStore = stateStore;
        this.book = book;
        this.store = new AiSessionStore(book.getId());
        buildUI();
        loadSessions();
    }

    public Parent getRoot() { return root; }
    public void setOnPanelToggle(Runnable r) { this.onPanelToggle = r; }
    public boolean isSessionPanelVisible() { return sessionPanelVisible; }
    public boolean isNavTreeVisible() { return navTreeVisible; }

    public void toggleSessionPanel() {
        sessionPanelVisible = !sessionPanelVisible;
        sessionPanel.setVisible(sessionPanelVisible);
        sessionPanel.setManaged(sessionPanelVisible);
        if (onPanelToggle != null) onPanelToggle.run();
    }

    public void toggleNavTree() {
        navTreeVisible = !navTreeVisible;
        navTreePanel.setVisible(navTreeVisible);
        navTreePanel.setManaged(navTreeVisible);
        if (onPanelToggle != null) onPanelToggle.run();
    }

    private static String makeTitle() {
        return LocalDateTime.now().format(TITLE_FMT);
    }

    // ════════════════════════════════════════
    //  UI Build
    // ════════════════════════════════════════

    private void buildUI() {
        root.setStyle("-fx-background-color: #0f1730;");
        buildSessionPanel();
        buildNavTreePanel();
        BorderPane conversationArea = buildConversationArea();
        HBox.setHgrow(conversationArea, Priority.ALWAYS);
        HBox center = new HBox(0);
        center.getChildren().addAll(sessionPanel, navTreePanel, conversationArea);
        root.setCenter(center);
    }

    // ── Session Panel ──

    private void buildSessionPanel() {
        sessionPanel.setPrefWidth(220);
        sessionPanel.setMinWidth(0);
        sessionPanel.setPadding(new Insets(12));
        sessionPanel.setStyle("-fx-background-color: #111a34; -fx-border-color: #223055; -fx-border-width: 0 1 0 0;");
        Label title = new Label("Sessions - " + book.getName());
        title.setWrapText(true);
        title.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold;");
        Button newBtn = new Button("+ New Session");
        newBtn.setMaxWidth(Double.MAX_VALUE);
        newBtn.setOnAction(e -> promptNewSession());
        VBox.setVgrow(sessionPanel, Priority.ALWAYS);
        sessionPanel.getChildren().addAll(title, newBtn);
    }

    // ── Nav Tree Panel ──

    private void buildNavTreePanel() {
        navTreePanel.setPrefWidth(240);
        navTreePanel.setMinWidth(0);
        navTreePanel.setPadding(new Insets(12));
        navTreePanel.setStyle("-fx-background-color: #111a34; -fx-border-color: #223055; -fx-border-width: 0 1 0 0;");
    }

    // ── Conversation Area ──

    private BorderPane buildConversationArea() {
        BorderPane area = new BorderPane();
        area.setPadding(new Insets(0));
        sessionTitleLabel = new Label("AI Conversation");
        sessionTitleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 12 16 4 16;");
        sessionTitleLabel.setMaxWidth(Double.MAX_VALUE);

        messageContainer = new VBox(12);
        messageContainer.setPadding(new Insets(16));
        messageContainer.setFillWidth(true);
        messageContainer.setStyle("-fx-background-color: #0f1730;");
        ScrollPane scroll = new ScrollPane(messageContainer);
        messageScrollPane = scroll;
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: #0f1730; -fx-background-color: #0f1730; -fx-border-color: transparent;");
        scroll.setFocusTraversable(false);
        // Ensure the viewport also uses the dark background
        scroll.skinProperty().addListener((obs, old, skin) -> {
            if (skin != null) {
                var vp = scroll.lookup(".viewport");
                if (vp instanceof Region r) r.setStyle("-fx-background-color: #0f1730;");
            }
        });
        VBox.setVgrow(scroll, Priority.ALWAYS);
        StackPane messageFrame = new StackPane(scroll);
        messageFrame.setPadding(new Insets(0, 0, 0, 1));
        messageFrame.setStyle("-fx-background-color: #0f1730; -fx-border-color: #223055; -fx-border-width: 0 0 0 1;");
        messageFrame.setFocusTraversable(false);

        Label placeholder = new Label("Select or create a session to start");
        placeholder.setStyle("-fx-text-fill: rgba(255,255,255,0.40); -fx-font-size: 16px;");
        placeholder.setAlignment(Pos.CENTER);
        StackPane placeholderPane = new StackPane(placeholder);
        placeholderPane.setPrefHeight(400);
        messageContainer.getChildren().add(placeholderPane);

        HBox inputArea = buildInputArea();
        inputArea.setPadding(new Insets(12, 16, 16, 16));
        area.setTop(sessionTitleLabel);
        area.setCenter(messageFrame);
        area.setBottom(inputArea);
        return area;
    }

    private HBox buildInputArea() {
        inputField = new TextArea();
        inputField.setPromptText("Type a message... (Ctrl+Enter to send)");
        inputField.setWrapText(true);
        inputField.setPrefRowCount(1);
        inputField.setMaxHeight(120);
        inputField.setStyle("-fx-font-size: 14px; -fx-padding: 8 14; -fx-background-radius: 12; -fx-border-radius: 12;");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        sendButton = new Button("Send");
        sendButton.getStyleClass().add("primary-action");
        sendButton.setStyle("-fx-font-size: 14px; -fx-padding: 8 20;");
        sendButton.setOnAction(e -> sendMessage());
        // Enter to send, Shift+Enter for newline
        inputField.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                if (e.isShiftDown()) {
                    // Shift+Enter → insert newline explicitly
                    inputField.insertText(inputField.getCaretPosition(), "\n");
                } else {
                    // Enter → send
                    sendMessage();
                }
            }
        });
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
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox list = new VBox(4);
        for (AiSession session : sessions) {
            Button btn = new Button(session.getTitle());
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            if (currentSession != null && currentSession.getId().equals(session.getId())) {
                btn.setStyle("-fx-background-color: #d7bb74; -fx-text-fill: #11182d;");
            } else {
                btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #e7ecff; -fx-border-color: transparent;");
            }
            btn.setOnAction(e -> selectSession(session));
            btn.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.SECONDARY) {
                    ContextMenu menu = new ContextMenu();
                    MenuItem rename = new MenuItem("Rename");
                    rename.setOnAction(ev -> renameSession(session));
                    MenuItem del = new MenuItem("Delete");
                    del.setOnAction(ev -> deleteSession(session));
                    menu.getItems().addAll(rename, del);
                    menu.show(btn, e.getScreenX(), e.getScreenY());
                }
            });
            list.getChildren().add(btn);
        }
        scroll.setContent(list);
        sessionPanel.getChildren().add(scroll);
        if (currentSession == null && !sessions.isEmpty()) {
            selectSession(sessions.get(0));
        } else if (currentSession != null) {
            refreshNavTree();
            refreshMessages();
        }
    }

    private void selectSession(AiSession session) {
        currentSession = session;
        branchSelections.clear();
        loadSessions();
    }

    private void promptNewSession() {
        TextInputDialog dialog = new TextInputDialog("New Session");
        dialog.setTitle("New Session");
        dialog.setHeaderText("Create a new AI conversation session");
        dialog.showAndWait().ifPresent(name -> {
            if (name.isBlank()) name = "New Session";
            AiSession session = new AiSession(name);
            store.save(session);
            selectSession(session);
        });
    }

    private void renameSession(AiSession session) {
        TextInputDialog dialog = new TextInputDialog(session.getTitle());
        dialog.setTitle("Rename Session");
        dialog.setHeaderText("Rename session");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                session.setTitle(name);
                store.save(session);
                loadSessions();
            }
        });
    }

    private void deleteSession(AiSession session) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete session \"" + session.getTitle() + "\"?", ButtonType.OK, ButtonType.CANCEL);
        alert.showAndWait().filter(ButtonType.OK::equals).ifPresent(ok -> {
            store.delete(session);
            if (currentSession != null && currentSession.getId().equals(session.getId())) {
                currentSession = null;
                branchSelections.clear();
                List<AiSession> remaining = store.loadAll();
                if (!remaining.isEmpty()) selectSession(remaining.get(0));
                else { loadSessions(); refreshNavTree(); refreshMessages(); }
            } else { loadSessions(); }
        });
    }

    // ════════════════════════════════════════
    //  Nav Tree
    // ════════════════════════════════════════

    private void refreshNavTree() {
        navTreePanel.getChildren().clear();

        Label title = new Label("Branch Navigator");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold;");
        navTreePanel.getChildren().add(title);

        if (currentSession == null) return;

        initBranchDefaults(currentSession.getRootMessages());

        VBox list = new VBox(4);
        collectPathBranchGroups(list);
        if (list.getChildren().isEmpty()) {
            Label empty = new Label("(no branches yet)");
            empty.setStyle("-fx-text-fill: rgba(255,255,255,0.35); -fx-font-size: 12px; -fx-padding: 8 0;");
            list.getChildren().add(empty);
        }

        ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        sp.skinProperty().addListener((obs, old, skin) -> {
            if (skin != null && sp.lookup(".viewport") instanceof Region vp) {
                vp.setStyle("-fx-background-color: transparent;");
            }
        });
        VBox.setVgrow(sp, Priority.ALWAYS);
        navTreePanel.getChildren().add(sp);
    }

    // ── Branch-point navigation ──

    /** Ensure every branch point has a default selection (first child). */
    private void initBranchDefaults(List<AiMessage> roots) {
        for (AiMessage root : roots) initBranchDefaults(root);
    }

    private void initBranchDefaults(AiMessage node) {
        if (node.getChildren().size() >= 2 && !branchSelections.containsKey(node.getId())) {
            branchSelections.put(node.getId(), node.getChildren().getFirst().getId());
        }
        for (AiMessage child : node.getChildren()) initBranchDefaults(child);
    }

    private void collectPathBranchGroups(VBox list) {
        List<AiMessage> path = buildSelectedPath();
        int depth = 0;
        for (AiMessage node : path) {
            if (node.getChildren().size() >= 2) {
                list.getChildren().add(createBranchGroup(node, depth));
            }
            depth++;
        }
    }

    private Node createBranchGroup(AiMessage branchPoint, int indent) {
        VBox group = new VBox(3);
        group.setPadding(new Insets(6, 2, 6, 2 + indent * 14));

        Label groupLabel = new Label(branchPoint.getTitle() == null || branchPoint.getTitle().isBlank()
                ? "(branch)" : branchPoint.getTitle());
        groupLabel.setStyle("-fx-text-fill: #d7bb74; -fx-font-size: 11px; -fx-font-weight: bold;");

        String selectedId = branchSelections.get(branchPoint.getId());
        String bpId = branchPoint.getId();

        for (AiMessage child : branchPoint.getChildren()) {
            String cId = child.getId();
            boolean sel = cId.equals(selectedId);
            String display = child.getTitle() == null || child.getTitle().isBlank() ? "(unnamed)" : child.getTitle();
            if (display.length() > 16) display = display.substring(0, 16) + "…";

            Button btn = new Button((sel ? "✓ " : "  ") + display);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setStyle(sel
                    ? "-fx-background-color: #d7bb74; -fx-text-fill: #11182d; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4;"
                    : "-fx-background-color: transparent; -fx-text-fill: #aab; -fx-font-size: 11px; -fx-padding: 2 6; -fx-border-color: #334; -fx-border-radius: 4;");
            btn.setOnAction(e -> {
                branchSelections.put(bpId, cId);
                refreshNavTree();
                refreshMessages();
            });
            group.getChildren().add(btn);
        }

        group.getChildren().addFirst(groupLabel);
        return group;
    }

    /** Build the conversation path by following branch selections. */
    private List<AiMessage> buildSelectedPath() {
        List<AiMessage> path = new ArrayList<>();
        for (AiMessage root : currentSession.getRootMessages()) {
            followPath(root, path);
        }
        return path;
    }

    private void followPath(AiMessage node, List<AiMessage> out) {
        out.add(node);
        List<AiMessage> children = node.getChildren();
        if (children.isEmpty()) return;
        if (children.size() == 1) { followPath(children.getFirst(), out); return; }
        // Branch point: follow selected child (default: first)
        String picked = branchSelections.get(node.getId());
        for (AiMessage child : children) {
            if (child.getId().equals(picked)) { followPath(child, out); return; }
        }
        followPath(children.getFirst(), out);
    }

    // ════════════════════════════════════════
    //  Message Display
    // ════════════════════════════════════════

    private void refreshMessages() {
        messageContainer.getChildren().clear();
        if (currentSession == null) {
            Label placeholder = new Label("Select or create a session to start");
            placeholder.setStyle("-fx-text-fill: rgba(255,255,255,0.40); -fx-font-size: 16px;");
            placeholder.setAlignment(Pos.CENTER);
            StackPane pane = new StackPane(placeholder);
            pane.setPrefHeight(400);
            messageContainer.getChildren().add(pane);
            sessionTitleLabel.setText("AI Conversation");
            inputField.setDisable(true);
            sendButton.setDisable(true);
            return;
        }
        sessionTitleLabel.setText(currentSession.getTitle());
        inputField.setDisable(false);
        sendButton.setDisable(false);

        List<AiMessage> path = buildSelectedPath();

        if (path.isEmpty()) {
            Label hint = new Label("Type a message below to start.");
            hint.setWrapText(true);
            hint.setStyle("-fx-text-fill: rgba(255,255,255,0.40); -fx-font-size: 14px; -fx-padding: 20;");
            messageContainer.getChildren().add(hint);
        } else {
            for (AiMessage node : path) {
                // Node header
                Label nodeHeader = new Label(node.getTitle() == null || node.getTitle().isBlank() ? "(unnamed)" : node.getTitle());
                nodeHeader.setStyle("-fx-text-fill: #d7bb74; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 0 2 0;");

                // Separator
                Separator sep = new Separator();
                sep.setStyle("-fx-background: rgba(215,187,116,0.3);");

                VBox nodeBlock = new VBox(6);
                nodeBlock.getChildren().addAll(nodeHeader, sep);

                // User message (right-aligned)
                if (node.getUserContent() != null && !node.getUserContent().isBlank()) {
                    nodeBlock.getChildren().add(createStableChatBubble(node.getUserContent(), true, node));
                }

                // Assistant response (left-aligned)
                if (node.getAssistantContent() != null && !node.getAssistantContent().isBlank()) {
                    nodeBlock.getChildren().add(createStableChatBubble(node.getAssistantContent(), false, node));
                }

                messageContainer.getChildren().add(nodeBlock);
            }
        }
        scrollToBottom();
    }

    /** Create a chat bubble with selectable text, auto-sized, no internal scrollbars. */
    private Node createChatBubble(String text, boolean isUser, AiMessage node) {
        String bg = isUser ? "rgba(36, 60, 124, 0.85)" : "rgba(25, 35, 70, 0.85)";
        String fg = isUser ? "#edf2ff" : "#e7ecff";

        TextArea ta = new TextArea(text);
        ta.setEditable(false);
        ta.setContextMenu(null);
        ta.setWrapText(true);
        ta.setMaxWidth(600);
        ta.setMinHeight(30);
        ta.setPrefRowCount(Math.max(1, (int) Math.ceil(text.length() / 40.0) + 1));

        ta.setStyle("-fx-background-color: " + bg + "; -fx-control-inner-background: " + bg + "; -fx-text-fill: " + fg + "; "
                + "-fx-border-color: transparent; -fx-background-radius: 12; -fx-background-insets: 0; "
                + "-fx-padding: 10 14; -fx-font-size: 14px; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");

        // Right-click context menu
        ContextMenu cm = new ContextMenu();
        if (isUser && node.getUserContent() != null && !node.getUserContent().isBlank()) {
            MenuItem editItem = new MenuItem("Edit & Resend");
            editItem.setOnAction(e -> editAndResend(node));
            cm.getItems().add(editItem);
        }
        MenuItem insertItem = new MenuItem("Insert custom pair");
        insertItem.setOnAction(e -> insertCustomPair(node));
        cm.getItems().add(insertItem);
        ta.setContextMenu(cm);

        ta.skinProperty().addListener((obs, old, skin) -> {
            if (skin != null) {
                // Disable scrollbars entirely
                if (ta.lookup(".scroll-pane") instanceof ScrollPane sp) {
                    sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                    sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                }
                Platform.runLater(() -> {
                    // ALL internal layers transparent so TextArea's single uniform bg shows everywhere
                    for (String sel : new String[]{".scroll-pane", ".viewport", ".content"}) {
                        if (ta.lookup(sel) instanceof Region r) {
                            r.setStyle("-fx-background-color: transparent; -fx-background-insets: 0;");
                        }
                    }
                    // Measure content height and resize to fit
                    Node content = ta.lookup(".content");
                    if (content != null) {
                        double h = content.getBoundsInLocal().getHeight();
                        if (h > 0) {
                            ta.setPrefHeight(h + 20);
                            // Content grew → keep scroll at bottom
                            scrollToBottom();
                        }
                    }
                });
            }
        });

        HBox wrapper = new HBox(ta);
        wrapper.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        wrapper.setPadding(new Insets(2, 16, 2, 16));
        HBox.setHgrow(ta, Priority.NEVER);
        return wrapper;
    }

    // ════════════════════════════════════════
    //  Branch operations
    // ════════════════════════════════════════

    /** Stable non-focusable chat bubble; copy is explicit to avoid TextArea focus artifacts. */
    private Node createStableChatBubble(String text, boolean isUser, AiMessage node) {
        String bg = isUser ? "rgba(36, 60, 124, 0.85)" : "rgba(25, 35, 70, 0.85)";
        String fg = isUser ? "#edf2ff" : "#e7ecff";

        Label content = new Label(text);
        content.setWrapText(true);
        content.setMaxWidth(560);
        content.setStyle("-fx-text-fill: " + fg + "; -fx-font-size: 14px;");

        Button copyButton = new Button("Copy all");
        copyButton.setFocusTraversable(false);
        copyButton.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-text-fill: rgba(255,255,255,0.72); "
                + "-fx-border-color: transparent; -fx-background-radius: 6; -fx-font-size: 11px; -fx-padding: 3 8;");
        copyButton.setOnAction(e -> {
            ClipboardContent clip = new ClipboardContent();
            clip.putString(text);
            Clipboard.getSystemClipboard().setContent(clip);
        });

        HBox copyRow = new HBox(copyButton);
        copyRow.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(8, content, copyRow);
        bubble.setMaxWidth(600);
        bubble.setFocusTraversable(false);
        bubble.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 12; -fx-padding: 10 14;");

        ContextMenu cm = new ContextMenu();
        if (isUser && node.getUserContent() != null && !node.getUserContent().isBlank()) {
            MenuItem editItem = new MenuItem("Edit & Resend");
            editItem.setOnAction(e -> editAndResend(node));
            cm.getItems().add(editItem);
        }
        MenuItem insertItem = new MenuItem("Insert custom pair");
        insertItem.setOnAction(e -> insertCustomPair(node));
        cm.getItems().add(insertItem);
        bubble.setOnContextMenuRequested(e -> cm.show(bubble, e.getScreenX(), e.getScreenY()));

        HBox wrapper = new HBox(bubble);
        wrapper.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        wrapper.setPadding(new Insets(2, 16, 2, 16));
        wrapper.setFocusTraversable(false);
        return wrapper;
    }

    /** Edit a user message and resend as a new sibling (creates a branch). */
    private void editAndResend(AiMessage node) {
        TextInputDialog dialog = new TextInputDialog(node.getUserContent());
        dialog.setTitle("Edit & Resend");
        dialog.setHeaderText("Edit your message");
        dialog.showAndWait().ifPresent(newText -> {
            if (newText.isBlank()) return;

            AiMessage sibling = new AiMessage(makeTitle());
            sibling.setUserContent(newText);

            AiMessage parent = findParentInSession(node);
            if (parent != null) {
                parent.getChildren().add(sibling);
                if (parent.getChildren().size() >= 2) {
                    branchSelections.put(parent.getId(), sibling.getId());
                }
            } else {
                int idx = currentSession.getRootMessages().indexOf(node);
                currentSession.getRootMessages().add(idx + 1, sibling);
            }

            refreshNavTree();
            refreshMessages();
            scrollToBottom();

            List<AiMessage> context = buildSelectedPath();
            List<String> messagesJson = flattenMessages(context);
            AiConfig config = appState.getAiConfig();
            String systemPrompt = "You are a professional writing assistant. Help the user write and improve their novel. "
                    + "Provide creative suggestions, plot ideas, character development, and editing advice.";
            new Thread(() -> {
                try {
                    String response = aiClient.chat(config, systemPrompt, messagesJson);
                    Platform.runLater(() -> {
                        sibling.setAssistantContent(response);
                        store.save(currentSession);
                        refreshNavTree();
                        refreshMessages();
                        scrollToBottom();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        String msg = e.getMessage();
                        sibling.setAssistantContent("[Error] " + (msg != null ? msg : "unknown"));
                        store.save(currentSession);
                        refreshNavTree();
                        refreshMessages();
                    });
                }
            }).start();
        });
    }

    /** Insert a custom user+assistant pair as a child of the given node. */
    private void insertCustomPair(AiMessage node) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Insert Custom Pair");

        TextArea userField = new TextArea();
        userField.setPromptText("User message");
        userField.setPrefRowCount(3);
        TextArea asstField = new TextArea();
        asstField.setPromptText("Assistant response");
        asstField.setPrefRowCount(3);
        VBox content = new VBox(8, new Label("User:"), userField, new Label("Assistant:"), asstField);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            String userText = userField.getText();
            String asstText = asstField.getText();
            if (userText.isBlank() && asstText.isBlank()) return;

            AiMessage custom = new AiMessage(makeTitle());
            if (!userText.isBlank()) custom.setUserContent(userText);
            if (!asstText.isBlank()) custom.setAssistantContent(asstText);
            node.getChildren().add(custom);

            store.save(currentSession);
            refreshNavTree();
            refreshMessages();
        });
    }

    /** Find the parent of a node in the session's message tree (reference equality). */
    private AiMessage findParentInSession(AiMessage target) {
        if (currentSession == null) return null;
        for (AiMessage root : currentSession.getRootMessages()) {
            if (root == target) return null;
            AiMessage found = findParentInSession(root, target);
            if (found != null) return found;
        }
        return null;
    }

    private AiMessage findParentInSession(AiMessage node, AiMessage target) {
        for (AiMessage child : node.getChildren()) {
            if (child == target) return node;
            AiMessage found = findParentInSession(child, target);
            if (found != null) return found;
        }
        return null;
    }

    // ════════════════════════════════════════
    //  Send Message
    // ════════════════════════════════════════

    private void sendMessage() {
        String text = inputField.getText();
        if (text == null || text.isBlank()) return;
        if (currentSession == null) return;

        inputField.clear();
        inputField.setDisable(true);
        sendButton.setDisable(true);
        sendButton.setText("AI思考中...");

        // Build context from the currently selected branch path
        List<AiMessage> contextPath = buildSelectedPath();

        AiMessage roundNode = new AiMessage(makeTitle());
        roundNode.setUserContent(text);
        if (!contextPath.isEmpty()) {
            AiMessage parent = contextPath.getLast();
            parent.getChildren().add(roundNode);
            if (parent.getChildren().size() >= 2) {
                branchSelections.put(parent.getId(), roundNode.getId());
            }
        } else {
            currentSession.getRootMessages().add(roundNode);
        }

        String nodeId = roundNode.getId();

        List<AiMessage> apiContext = new ArrayList<>(contextPath);
        apiContext.add(roundNode);
        refreshNavTree();
        refreshMessages();
        scrollToBottom();

        final List<String> messagesJson = flattenMessages(apiContext);
        new Thread(() -> {
            try {
                AiConfig config = appState.getAiConfig();
                String systemPrompt = "You are a professional writing assistant. Help the user write and improve their novel. "
                        + "Provide creative suggestions, plot ideas, character development, and editing advice.";
                String responseText = aiClient.chat(config, systemPrompt, messagesJson);

                Platform.runLater(() -> {
                    roundNode.setAssistantContent(responseText);
                    store.save(currentSession);
                    selectAiResponseNode(nodeId);
                    inputField.setDisable(false);
                    sendButton.setDisable(false);
                    sendButton.setText("Send");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && (errorMsg.contains("401") || errorMsg.contains("API Key")
                            || errorMsg.contains("required"))) {
                        errorMsg = "API configuration error. Please check Settings > AI Config.";
                    }
                    roundNode.setAssistantContent("[Error] " + errorMsg);
                    store.save(currentSession);
                    selectAiResponseNode(nodeId);
                    inputField.setDisable(false);
                    sendButton.setDisable(false);
                    sendButton.setText("Send");
                });
            }
        }).start();
    }

    /** Flatten conversation nodes into JSON message objects for the API. */
    private List<String> flattenMessages(List<AiMessage> nodes) {
        List<String> result = new ArrayList<>();
        for (AiMessage node : nodes) {
            if (node.getUserContent() != null && !node.getUserContent().isBlank()) {
                result.add("{\"role\":\"user\",\"content\":" + jsonStr(node.getUserContent()) + "}");
            }
            if (node.getAssistantContent() != null && !node.getAssistantContent().isBlank()
                    && !node.getAssistantContent().startsWith("[Error]")) {
                result.add("{\"role\":\"assistant\",\"content\":" + jsonStr(node.getAssistantContent()) + "}");
            }
        }
        return result;
    }

    private static String jsonStr(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    // ── Node selection after response ──

    private void selectAiResponseNode(String nodeId) {
        refreshNavTree();
        refreshMessages();
        scrollToBottom();
    }

    private TreeItem<AiMessage> findLastChild(TreeItem<AiMessage> parent, String id) {
        if (parent == null) return null;
        for (TreeItem<AiMessage> child : parent.getChildren()) {
            if (child.getValue() != null && child.getValue().getId().equals(id)) return child;
            TreeItem<AiMessage> found = findLastChild(child, id);
            if (found != null) return found;
        }
        return null;
    }

    private void scrollToBottom() {
        if (messageScrollPane != null) {
            messageScrollPane.layout();
            messageScrollPane.setVvalue(1.0);
        }
    }
}
