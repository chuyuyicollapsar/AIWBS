package example.aiwbs.ui;

import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.Line;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;

public final class IconButtons {
    private IconButtons() {
    }

    public static Button sidebarButton() {
        Pane icon = baseIcon();
        addLine(icon, 9, 9, 9, 21);
        addLine(icon, 13, 9, 13, 21);
        return iconButton(icon);
    }

    public static Button sidebarOpenButton() {
        Pane icon = baseIcon();
        addLine(icon, 13, 4, 13, 26);
        addLine(icon, 18, 6, 18, 24);
        return iconButton(icon);
    }

    public static Button chapterButton() {
        Pane icon = baseIcon();
        addLine(icon, 9, 9, 9, 21);
        addLine(icon, 14, 11, 22, 11);
        addLine(icon, 14, 16, 20, 16);
        addLine(icon, 14, 21, 23, 21);
        return iconButton(icon);
    }

    public static Button chapterOpenButton() {
        Pane icon = baseIcon();
        addLine(icon, 20, 4, 20, 26);
        addLine(icon, 8, 10, 17, 10);
        addLine(icon, 8, 15, 16, 15);
        addLine(icon, 8, 20, 18, 20);
        return iconButton(icon);
    }

    public static Button trashButton() {
        Pane icon = new Pane();
        icon.setPrefSize(30, 30);
        addLine(icon, 14, 7, 16, 7);
        addLine(icon, 15, 7, 15, 5);
        addLine(icon, 9.5, 9, 20.5, 9);

        Path bin = new Path(
                new MoveTo(10, 11),
                new LineTo(20, 11),
                new LineTo(18.5, 25.5),
                new CubicCurveTo(18.5, 27, 18, 27, 17, 27),
                new LineTo(13, 27),
                new CubicCurveTo(12, 27, 11.5, 27, 11.5, 25.5),
                new LineTo(10, 11)
        );
        bin.setFill(Color.TRANSPARENT);
        bin.setStroke(Color.web("#dbe4ff"));
        bin.setStrokeWidth(1.8);
        icon.getChildren().add(bin);
        addLine(icon, 13.5, 12.5, 13, 24.5);
        addLine(icon, 16.5, 12.5, 17, 24.5);
        return iconButton(icon);
    }

    public static Button moreButton() {
        Pane icon = new Pane();
        icon.setPrefSize(30, 30);
        addLine(icon, 11, 15, 11, 15);
        addLine(icon, 15, 15, 15, 15);
        addLine(icon, 19, 15, 19, 15);
        return iconButton(icon);
    }

    public static Button backButton() {
        Pane icon = new Pane();
        icon.setPrefSize(30, 30);
        addLine(icon, 17, 8, 11, 15);
        addLine(icon, 11, 15, 17, 22);
        return iconButton(icon);
    }

    public static Button forwardButton() {
        Pane icon = new Pane();
        icon.setPrefSize(30, 30);
        addLine(icon, 13, 8, 19, 15);
        addLine(icon, 19, 15, 13, 22);
        return iconButton(icon);
    }

    public static Button outlineButton() {
        Pane icon = baseIcon();
        addBulletLine(icon, 8);
        addBulletLine(icon, 15);
        addBulletLine(icon, 22);
        return iconButton(icon);
    }

    public static Button outlineActiveButton() {
        Pane icon = new Pane();
        icon.setPrefSize(30, 30);
        javafx.scene.shape.Rectangle frame = new javafx.scene.shape.Rectangle(4, 4, 22, 22);
        frame.setArcWidth(8);
        frame.setArcHeight(8);
        frame.setFill(Color.TRANSPARENT);
        frame.setStroke(Color.web("#d7bb74"));
        frame.setStrokeWidth(2.2);
        icon.getChildren().add(frame);
        addBulletLineActive(icon, 8);
        addBulletLineActive(icon, 15);
        addBulletLineActive(icon, 22);
        return iconButton(icon);
    }

    private static void addBulletLine(Pane pane, double y) {
        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(9, y, 2);
        dot.setFill(Color.web("#dbe4ff"));
        pane.getChildren().add(dot);
        Line line = new Line(13, y, 22, y);
        line.setStroke(Color.web("#dbe4ff"));
        line.setStrokeWidth(1.8);
        line.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        pane.getChildren().add(line);
    }

    private static void addBulletLineActive(Pane pane, double y) {
        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(9, y, 2);
        dot.setFill(Color.web("#d7bb74"));
        pane.getChildren().add(dot);
        Line line = new Line(13, y, 22, y);
        line.setStroke(Color.web("#d7bb74"));
        line.setStrokeWidth(1.8);
        line.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        pane.getChildren().add(line);
    }

    private static Pane baseIcon() {
        Pane pane = new Pane();
        pane.setPrefSize(30, 30);
        Rectangle frame = new Rectangle(4, 4, 22, 22);
        frame.setArcWidth(8);
        frame.setArcHeight(8);
        frame.setFill(Color.TRANSPARENT);
        frame.setStroke(Color.web("#dbe4ff"));
        frame.setStrokeWidth(2.2);
        pane.getChildren().add(frame);
        return pane;
    }

    private static void addLine(Pane pane, double sx, double sy, double ex, double ey) {
        Line line = new Line(sx, sy, ex, ey);
        line.setStroke(Color.web("#dbe4ff"));
        line.setStrokeWidth(2.2);
        line.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        pane.getChildren().add(line);
    }

    private static Button iconButton(Pane icon) {
        Button button = new Button();
        StackPane box = new StackPane(icon);
        box.setPrefSize(30, 30);
        box.setMinSize(30, 30);
        box.setMaxSize(30, 30);
        button.setGraphic(box);
        button.setMinSize(34, 30);
        button.setPrefSize(34, 30);
        button.setMaxSize(34, 30);
        button.getStyleClass().add("icon-button");
        return button;
    }
}
