package example.aiwbs.ui;

import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.Line;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

public final class IconButtons {
    private IconButtons() {}

    private static final Color B = Color.web("#dbe4ff");
    private static final Color G = Color.web("#d7bb74");

    // ── Helpers ──

    private static Button wrap(Pane icon) {
        Button btn = new Button();
        StackPane box = new StackPane(icon);
        box.setPrefSize(30, 30);
        box.setMinSize(30, 30);
        box.setMaxSize(30, 30);
        btn.setGraphic(box);
        btn.setMinSize(34, 30);
        btn.setPrefSize(34, 30);
        btn.setMaxSize(34, 30);
        btn.getStyleClass().add("icon-button");
        return btn;
    }

    private static Pane bg() {
        Pane p = new Pane();
        p.setPrefSize(30, 30);
        return p;
    }

    private static Rectangle frame(double x, double y, double w, double h, Color c) {
        Rectangle r = new Rectangle(x, y, w, h);
        r.setArcWidth(8); r.setArcHeight(8);
        r.setFill(Color.TRANSPARENT); r.setStroke(c); r.setStrokeWidth(2.2);
        return r;
    }

    private static Line ln(double sx, double sy, double ex, double ey, Color c) {
        Line l = new Line(sx, sy, ex, ey);
        l.setStroke(c); l.setStrokeWidth(2.2);
        l.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        return l;
    }

    // ── Sidebar ──

    public static Button sidebarOpenButton() {
        Pane i = bg();
        i.getChildren().addAll(frame(4, 4, 22, 22, B), ln(13, 4, 13, 26, B), ln(18, 6, 18, 24, B));
        return wrap(i);
    }

    public static Button sidebarActiveButton() {
        Pane i = bg();
        i.getChildren().addAll(frame(4, 4, 22, 22, G), ln(9, 9, 9, 21, G), ln(13, 9, 13, 21, G));
        return wrap(i);
    }

    // ── Chapter ──

    public static Button chapterOpenButton() {
        Pane i = bg();
        i.getChildren().addAll(frame(4, 4, 22, 22, B),
                ln(20, 4, 20, 26, B), ln(8, 10, 17, 10, B),
                ln(8, 15, 16, 15, B), ln(8, 20, 18, 20, B));
        return wrap(i);
    }

    public static Button chapterActiveButton() {
        Pane i = bg();
        i.getChildren().addAll(frame(4, 4, 22, 22, G),
                ln(9, 9, 9, 21, G), ln(14, 11, 22, 11, G),
                ln(14, 16, 20, 16, G), ln(14, 21, 23, 21, G));
        return wrap(i);
    }

    // ── Outline (tree) ──

    public static Button outlineButton() {
        Pane i = bg();
        Rectangle f = frame(4, 4, 22, 22, B);
        i.getChildren().add(f);
        for (int y : new int[]{8, 15, 22}) {
            Circle d = new Circle(9, y, 2);
            d.setFill(B);
            i.getChildren().add(d);
            i.getChildren().add(ln(13, y, 22, y, B));
        }
        return wrap(i);
    }

    public static Button outlineActiveButton() {
        Pane i = bg();
        Rectangle f = frame(4, 4, 22, 22, G);
        i.getChildren().add(f);
        Line rightEdge = ln(19, 8, 19, 22, G);
        i.getChildren().add(rightEdge);
        Line arrowUp = ln(14, 15, 18, 11, G);
        Line arrowDown = ln(14, 15, 18, 19, G);
        i.getChildren().addAll(arrowUp, arrowDown);
        return wrap(i);
    }

    // ── Outline entry (top toolbar) ──

    public static Button outlineEntryButton() {
        Pane i = bg();
        i.getChildren().add(frame(4, 4, 22, 22, B));
        i.getChildren().add(ln(13, 8, 13, 22, B));
        i.getChildren().add(ln(10, 11, 16, 11, B));
        i.getChildren().add(ln(10, 15, 16, 15, B));
        i.getChildren().add(ln(10, 19, 12, 19, B));
        return wrap(i);
    }

    public static Button outlineEntryActiveButton() {
        Pane i = bg();
        i.getChildren().add(frame(4, 4, 22, 22, G));
        i.getChildren().add(ln(13, 8, 13, 22, G));
        i.getChildren().add(ln(10, 11, 16, 11, G));
        i.getChildren().add(ln(10, 15, 16, 15, G));
        i.getChildren().add(ln(10, 19, 12, 19, G));
        return wrap(i);
    }

    // ── Vol/Chap outline (细纲) ──

    public static Button volChapOutlineButton() {
        Pane i = bg();
        Rectangle p = new Rectangle(7, 6, 16, 19);
        p.setArcWidth(4); p.setArcHeight(4);
        p.setFill(Color.TRANSPARENT); p.setStroke(B); p.setStrokeWidth(2.0);
        i.getChildren().add(p);
        for (int y : new int[]{11, 15, 19}) {
            i.getChildren().add(ln(11, y, 22, y, B));
        }
        return wrap(i);
    }

    public static Button volChapOutlineActiveButton() {
        Pane i = bg();
        Rectangle p = new Rectangle(7, 6, 16, 19);
        p.setArcWidth(4); p.setArcHeight(4);
        p.setFill(Color.TRANSPARENT); p.setStroke(G); p.setStrokeWidth(2.0);
        i.getChildren().add(p);
        for (int y : new int[]{11, 15, 19}) {
            i.getChildren().add(ln(11, y, 22, y, G));
        }
        return wrap(i);
    }

    // ── Navigation ──

    public static Button backButton() {
        Pane i = bg();
        i.getChildren().addAll(ln(17, 8, 11, 15, B), ln(11, 15, 17, 22, B));
        return wrap(i);
    }

    public static Button forwardButton() {
        Pane i = bg();
        i.getChildren().addAll(ln(13, 8, 19, 15, B), ln(19, 15, 13, 22, B));
        return wrap(i);
    }

    // ── Utility ──

    public static Button trashButton() {
        Pane i = bg();
        i.getChildren().addAll(ln(14, 7, 16, 7, B), ln(15, 7, 15, 5, B), ln(9.5, 9, 20.5, 9, B));
        Path bin = new Path(
                new MoveTo(10, 11), new LineTo(20, 11),
                new LineTo(18.5, 25.5), new CubicCurveTo(18.5, 27, 18, 27, 17, 27),
                new LineTo(13, 27), new CubicCurveTo(12, 27, 11.5, 27, 11.5, 25.5),
                new LineTo(10, 11));
        bin.setFill(Color.TRANSPARENT); bin.setStroke(B); bin.setStrokeWidth(1.8);
        i.getChildren().add(bin);
        i.getChildren().addAll(ln(13.5, 12.5, 13, 24.5, B), ln(16.5, 12.5, 17, 24.5, B));
        return wrap(i);
    }

    public static Button moreButton() {
        Pane i = bg();
        i.getChildren().addAll(ln(11, 15, 11, 15, B), ln(15, 15, 15, 15, B), ln(19, 15, 19, 15, B));
        return wrap(i);
    }

    public static Button settingsButton() {
        Pane i = bg();
        SVGPath g = new SVGPath();
        g.setContent("M21.2,13.6 L23.5,13.8 L23.5,16.2 L21.2,16.4 A6,6 0 0,1 20.3,18.4 "
                + "L22.1,20.1 L20.4,21.8 L18.5,20.1 A6,6 0 0,1 16.4,21.2 L16.2,23.5 "
                + "L13.8,23.5 L13.6,21.2 A6,6 0 0,1 11.6,20.1 L9.7,21.8 L7.9,20.1 "
                + "L9.7,18.4 A6,6 0 0,1 8.8,16.4 L6.5,16.2 L6.5,13.8 L8.8,13.6 "
                + "A6,6 0 0,1 9.7,11.6 L7.9,9.9 L9.6,8.1 L11.5,9.9 A6,6 0 0,1 13.6,8.8 "
                + "L13.8,6.5 L16.2,6.5 L16.4,8.8 A6,6 0 0,1 18.4,9.9 L20.3,8.1 L22,9.9 "
                + "L20.1,11.6 A6,6 0 0,1 21.2,13.6 Z");
        g.setFill(Color.TRANSPARENT); g.setStroke(B); g.setStrokeWidth(2.2);
        g.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        Circle c = new Circle(15, 15, 7);
        c.setFill(Color.TRANSPARENT); c.setStroke(B); c.setStrokeWidth(2.2);
        i.getChildren().addAll(g, c);
        return wrap(i);
    }

    public static Button aiButton() {
        Pane i = bg();
        Rectangle b = new Rectangle(6, 9, 18, 14);
        b.setArcWidth(6); b.setArcHeight(6);
        b.setFill(Color.TRANSPARENT); b.setStroke(B); b.setStrokeWidth(2.2);
        Path t = new Path(new MoveTo(18, 21), new LineTo(22, 25), new LineTo(24, 21));
        t.setFill(Color.TRANSPARENT); t.setStroke(B); t.setStrokeWidth(2.2);
        t.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        i.getChildren().addAll(b, t);
        for (int x : new int[]{10, 15, 20}) {
            Circle d = new Circle(x, 16, 1.5);
            d.setFill(B);
            i.getChildren().add(d);
        }
        return wrap(i);
    }
}
