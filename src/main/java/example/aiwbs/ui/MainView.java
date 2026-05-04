package example.aiwbs.ui;

import example.aiwbs.model.AppState;
import example.aiwbs.storage.StateStore;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

public class MainView {
    private final BorderPane root = new BorderPane();
    private final BookManagerView bookManagerView;
    private final HBox bar = new HBox(10);
    private Button volumeToggle;
    private Button chapterToggle;
    private Button bookMenu;

    public MainView(AppState state, StateStore store) {
        bookManagerView = new BookManagerView(state, store);
        bookManagerView.inSelectionPageProperty().addListener((obs, oldValue, newValue) -> refreshToolbarIcons());
        root.setStyle("-fx-background-color: #0f1730;");
        root.setTop(buildToolbar());
        root.setCenter(bookManagerView.getRoot());
    }

    public Parent getRoot() {
        return root;
    }

    private Parent buildToolbar() {
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #111a34; -fx-padding: 6 12; -fx-border-color: #223055; -fx-border-width: 0 0 1 0;");

        Button back = IconButtons.backButton();
        back.disableProperty().bind(bookManagerView.inSelectionPageProperty());
        back.setOnAction(e -> bookManagerView.showSelectionPage());

        Button forward = IconButtons.forwardButton();
        forward.disableProperty().bind(bookManagerView.inSelectionPageProperty().not());
        forward.setOnAction(e -> bookManagerView.enterSelectedBook());

        volumeToggle = IconButtons.sidebarButton();
        volumeToggle.setOnAction(e -> bookManagerView.toggleVolumeSidebar());

        chapterToggle = IconButtons.chapterButton();
        chapterToggle.setOnAction(e -> bookManagerView.toggleChapterSidebar());

        bookMenu = IconButtons.moreButton();
        bookMenu.setOnAction(e -> bookManagerView.showBookMenu(bookMenu));

        bar.getChildren().addAll(back, forward, volumeToggle, chapterToggle, bookMenu);
        refreshToolbarIcons();
        return bar;
    }

    private void refreshToolbarIcons() {
        if (volumeToggle == null || chapterToggle == null) {
            return;
        }
        boolean inSelectionPage = bookManagerView.inSelectionPageProperty().get();
        volumeToggle.setVisible(!inSelectionPage);
        volumeToggle.setManaged(!inSelectionPage);
        chapterToggle.setVisible(!inSelectionPage);
        chapterToggle.setManaged(!inSelectionPage);
        if (bookMenu != null) {
            bookMenu.setVisible(!inSelectionPage);
            bookMenu.setManaged(!inSelectionPage);
        }

        int volumeIndex = bar.getChildren().indexOf(volumeToggle);
        int chapterIndex = bar.getChildren().indexOf(chapterToggle);
        Button nextVolume = bookManagerView.isVolumeSidebarCollapsed() ? IconButtons.sidebarOpenButton() : IconButtons.sidebarButton();
        Button nextChapter = bookManagerView.isChapterSidebarCollapsed() ? IconButtons.chapterOpenButton() : IconButtons.chapterButton();
        nextVolume.setOnAction(e -> {
            bookManagerView.toggleVolumeSidebar();
            refreshToolbarIcons();
        });
        nextChapter.setOnAction(e -> {
            bookManagerView.toggleChapterSidebar();
            refreshToolbarIcons();
        });
        nextVolume.setVisible(!inSelectionPage);
        nextVolume.setManaged(!inSelectionPage);
        nextChapter.setVisible(!inSelectionPage);
        nextChapter.setManaged(!inSelectionPage);
        if (volumeIndex >= 0) {
            bar.getChildren().set(volumeIndex, nextVolume);
        }
        if (chapterIndex >= 0) {
            bar.getChildren().set(chapterIndex, nextChapter);
        }
        volumeToggle = nextVolume;
        chapterToggle = nextChapter;
        bar.getChildren().get(0).setOpacity(inSelectionPage ? 0.35 : 1.0);
        bar.getChildren().get(1).setOpacity(inSelectionPage ? 1.0 : 0.35);
    }

}
