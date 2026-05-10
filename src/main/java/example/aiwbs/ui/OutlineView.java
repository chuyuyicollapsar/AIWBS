package example.aiwbs.ui;

import example.aiwbs.model.AppState;
import example.aiwbs.model.Book;
import example.aiwbs.model.OutlineNode;
import example.aiwbs.storage.StateStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

import static example.aiwbs.ui.ViewUtils.*;

/**
 * 大纲界面（树状大纲）。
 * 左侧按钮条：大纲树侧栏开关
 * 内容区：大纲树侧栏 + 节点编辑器
 */
public class OutlineView {
    private final BorderPane root = new BorderPane();
    private final AppState state;
    private final StateStore store;
    private final Book book;
    private final ShellView shellView;

    private OutlineNode selectedOutlineNode;
    private TreeView<OutlineNode> outlineTreeView;
    private boolean settingOutlineSelection;
    private boolean outlineSidebarCollapsed;

    public OutlineView(AppState state, StateStore store, int bookIndex, ShellView shellView) {
        this.state = state;
        this.store = store;
        this.book = state.getBooks().get(bookIndex);
        this.shellView = shellView;
        this.selectedOutlineNode = shellView.getSelectedOutlineNode();
        this.outlineSidebarCollapsed = shellView.isOutlineSidebarCollapsed();
        buildUI();
    }

    public Parent getRoot() { return root; }

    private void buildUI() {
        root.setStyle("-fx-background-color: #0f1730;");

        // Left bar on root (flush left)
        root.setLeft(buildLeftBar());

        // Center wrapper with 12px padding
        HBox body = new HBox(0);
        if (!outlineSidebarCollapsed) {
            HBox wrapper = new HBox(buildOutlineSidebar());
            wrapper.setPadding(new Insets(0, 12, 0, 0));
            body.getChildren().add(wrapper);
        }
        Parent editorContent = buildOutlineEditor();
        body.getChildren().add(editorContent);
        HBox.setHgrow(editorContent, Priority.ALWAYS);

        BorderPane centerWrap = new BorderPane(body);
        centerWrap.setPadding(new Insets(12));
        root.setCenter(centerWrap);
    }

    /** Vertical left bar: outline sidebar toggle. */
    private Parent buildLeftBar() {
        VBox bar = new VBox(6);
        bar.setPadding(new Insets(8, 4, 8, 4));
        bar.setStyle("-fx-background-color: #111a34; -fx-border-color: #223055; -fx-border-width: 0 1 0 0;");
        bar.setAlignment(Pos.TOP_CENTER);

        Button outlineBtn = outlineSidebarCollapsed ? IconButtons.outlineButton() : IconButtons.outlineActiveButton();
        tip(outlineBtn, outlineSidebarCollapsed ? "显示大纲树侧边栏" : "隐藏大纲树侧边栏", "right");
        outlineBtn.setOnAction(e -> {
            outlineSidebarCollapsed = !outlineSidebarCollapsed;
            shellView.setOutlineSidebarCollapsed(outlineSidebarCollapsed);
            buildUI();
        });

        bar.getChildren().add(outlineBtn);
        return bar;
    }

    // ════════════════════════════════════════
    //  Outline sidebar
    // ════════════════════════════════════════

    private Parent buildOutlineSidebar() {
        VBox box = new VBox(8);
        box.setPrefWidth(260);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(255,255,255,0.07); -fx-background-radius: 12;");

        Label title = new Label("Outline");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold;");
        box.getChildren().add(title);

        Button add = fullButton("Add Root Node");
        add.setOnAction(e -> addRootOutlineNode());
        box.getChildren().add(add);

        TreeItem<OutlineNode> rootItem = new TreeItem<>(null);
        rootItem.setExpanded(true);
        for (OutlineNode node : book.getOutlineRoots()) {
            rootItem.getChildren().add(buildTreeItem(node));
        }

        TreeView<OutlineNode> treeView = new TreeView<>(rootItem);
        treeView.setShowRoot(false);
        treeView.setStyle("-fx-background-color: transparent; -fx-control-inner-background: transparent; -fx-background-radius: 8;");
        outlineTreeView = treeView;

        treeView.setCellFactory(tv -> new OutlineTreeCell());
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (!settingOutlineSelection && newVal != null && newVal.getValue() != null) {
                selectedOutlineNode = newVal.getValue();
                shellView.setSelectedOutlineNode(selectedOutlineNode);
                buildUI();
            }
        });

        if (selectedOutlineNode != null) {
            settingOutlineSelection = true;
            selectTreeNode(treeView.getRoot(), selectedOutlineNode.getId());
            settingOutlineSelection = false;
        }

        VBox.setVgrow(treeView, Priority.ALWAYS);
        box.getChildren().add(treeView);
        return box;
    }

    private TreeItem<OutlineNode> buildTreeItem(OutlineNode node) {
        TreeItem<OutlineNode> item = new TreeItem<>(node);
        item.setExpanded(!shellView.isOutlineNodeCollapsed(node.getId()));
        item.expandedProperty().addListener((obs, wasExpanded, isExpanded) ->
                shellView.setOutlineNodeCollapsed(node.getId(), !isExpanded));
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

    // ════════════════════════════════════════
    //  Outline editor
    // ════════════════════════════════════════

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
            buildUI();
        });

        box.getChildren().addAll(header, titleField, contentLabel, contentArea, save);
        return box;
    }

    // ════════════════════════════════════════
    //  Outline node CRUD
    // ════════════════════════════════════════

    private Button fullButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    private void addRootOutlineNode() {
        showSingleLineInput("Outline Node Title", "New Outline Node").ifPresent(name -> {
            OutlineNode node = new OutlineNode(name);
            book.getOutlineRoots().add(node);
            selectedOutlineNode = node;
            shellView.setSelectedOutlineNode(node);
            store.save(state);
            buildUI();
        });
    }

    private void addChildOutlineNode(OutlineNode parent) {
        showSingleLineInput("Outline Node Title", "New Outline Node").ifPresent(name -> {
            OutlineNode node = new OutlineNode(name);
            parent.getChildren().add(node);
            selectedOutlineNode = node;
            shellView.setSelectedOutlineNode(node);
            store.save(state);
            buildUI();
        });
    }

    private void addSiblingOutlineNode(OutlineNode node) {
        showSingleLineInput("Outline Node Title", "New Outline Node").ifPresent(name -> {
            OutlineNode newNode = new OutlineNode(name);
            OutlineNode parent = findOutlineParent(book.getOutlineRoots(), node.getId());
            if (parent != null) {
                int idx = findChildIndex(parent.getChildren(), node.getId());
                parent.getChildren().add(idx + 1, newNode);
            } else {
                int idx = findChildIndex(book.getOutlineRoots(), node.getId());
                book.getOutlineRoots().add(idx + 1, newNode);
            }
            selectedOutlineNode = newNode;
            shellView.setSelectedOutlineNode(newNode);
            store.save(state);
            buildUI();
        });
    }

    private void renameOutlineNode(OutlineNode node) {
        showSingleLineInput("Rename", node.getTitle()).ifPresent(name -> {
            node.setTitle(name);
            store.save(state);
            buildUI();
        });
    }

    private void deleteOutlineNode(OutlineNode node) {
        OutlineNode parent = findOutlineParent(book.getOutlineRoots(), node.getId());
        if (parent != null) {
            parent.getChildren().remove(node);
        } else {
            book.getOutlineRoots().remove(node);
        }
        selectedOutlineNode = null;
        shellView.setSelectedOutlineNode(null);
        store.save(state);
        buildUI();
    }

    private void moveOutlineNode(OutlineNode node, int offset) {
        OutlineNode parent = findOutlineParent(book.getOutlineRoots(), node.getId());
        List<OutlineNode> siblings = parent != null ? parent.getChildren() : book.getOutlineRoots();
        int index = findChildIndex(siblings, node.getId());
        int target = index + offset;
        if (index < 0 || target < 0 || target >= siblings.size()) return;
        siblings.remove(index);
        siblings.add(target, node);
        store.save(state);
        buildUI();
    }

    private OutlineNode findOutlineParent(List<OutlineNode> roots, String childId) {
        for (OutlineNode n : roots) {
            if (n.getChildren().stream().anyMatch(c -> c.getId().equals(childId))) return n;
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
}
