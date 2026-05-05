package example.aiwbs.ui;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;

/**
 * 自定义 tooltip 浮层，替代 javafx.scene.control.Tooltip。
 *
 * JavaFX 内置 Tooltip 的弹出窗口在 Windows 上会带一层系统黑框，
 * 导致圆角边框露出方角。用 Popup 自绘容器可完全控制渲染，消除此问题。
 */
public class TooltipPopup {
    private final Popup popup;
    private final StackPane container;

    public TooltipPopup(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #e7ecff; -fx-font-size: 13px;");

        container = new StackPane(label);
        container.setStyle(
            "-fx-background-color: #111a34;" +
            "-fx-background-radius: 0;" +
            "-fx-padding: 6 10;" +
            "-fx-border-color: #4a7aff;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 0;"
        );

        popup = new Popup();
        popup.getContent().add(container);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
    }

    /** 浮层出现在按钮正下方，左对齐 */
    public void showAtBottom(Node owner) {
        Point2D p = owner.localToScreen(0, owner.getLayoutBounds().getHeight());
        popup.show(owner, p.getX(), p.getY());
    }

    /** 浮层出现在按钮右侧，顶对齐（用于左侧边栏按钮） */
    public void showAtRight(Node owner) {
        Point2D p = owner.localToScreen(owner.getLayoutBounds().getWidth(), 0);
        popup.show(owner, p.getX(), p.getY());
    }

    /**
     * 浮层出现在按钮正下方，右对齐（用于最右侧的按钮）。
     * 先显示再在 onShown 中右移，避免在布局完成前宽度未知的问题。
     */
    public void showAtBottomRight(Node owner) {
        double bw = owner.getLayoutBounds().getWidth();
        double bh = owner.getLayoutBounds().getHeight();
        Point2D br = owner.localToScreen(bw, bh);
        popup.setOnShown(e -> {
            double pw = container.getWidth();
            if (pw > 0) {
                popup.setAnchorX(br.getX() - pw);
                popup.setAnchorY(br.getY());
            }
        });
        popup.show(owner, br.getX(), br.getY());
    }

    public void hide() {
        popup.hide();
    }
}
