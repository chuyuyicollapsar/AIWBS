package example.aiwbs.ui;

import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.util.Optional;

public final class
ViewUtils {
    private ViewUtils() {}

    public static void styleDialog(DialogPane pane) {
        pane.getStylesheets().add(
                ViewUtils.class.getResource("/example/aiwbs/ui/app.css").toExternalForm()
        );
    }

    public static Optional<String> showSingleLineInput(String title, String initialValue) {
        return singleLineInputDialog(title, initialValue).showAndWait();
    }

    public static Optional<String> showMultiLineInput(String title, String initialValue) {
        return multiLineInputDialog(title, initialValue).showAndWait();
    }

    public static Dialog<String> singleLineInputDialog(String title, String initialValue) {
        Dialog<String> d = new Dialog<>();
        d.setTitle(title);
        d.setHeaderText(title);
        TextField tf = new TextField(initialValue);
        tf.setPrefColumnCount(32);

        d.getDialogPane().setContent(tf);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        installDialogTextInputKeys(d, tf, false);
        d.setResultConverter(btn -> btn == ButtonType.OK ? tf.getText() : null);
        d.setOnShown(e -> tf.requestFocus());
        styleDialog(d.getDialogPane());
        return d;
    }

    public static Dialog<String> multiLineInputDialog(String title, String initialValue) {
        Dialog<String> d = new Dialog<>();
        d.setTitle(title);
        d.setHeaderText(title);
        TextArea ta = new TextArea(initialValue);
        ta.setPrefRowCount(6);
        ta.setPrefColumnCount(40);
        ta.setWrapText(true);

        d.getDialogPane().setContent(ta);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        installDialogTextAreaKeys(d, ta);
        d.setResultConverter(btn -> btn == ButtonType.OK ? ta.getText() : null);
        d.setOnShown(e -> ta.requestFocus());
        styleDialog(d.getDialogPane());
        return d;
    }

    public static void installDialogTextAreaKeys(Dialog<?> dialog, TextArea textArea) {
        installDialogTextInputKeys(dialog, textArea, true);
    }

    private static void installDialogTextInputKeys(Dialog<?> dialog, TextInputControl textInput, boolean allowShiftEnterNewLine) {
        final boolean[] suppressEnterRelease = {false};
        dialog.getDialogPane().setFocusTraversable(true);
        textInput.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.ENTER) return;

            e.consume();
            suppressEnterRelease[0] = true;
            if (allowShiftEnterNewLine && e.isShiftDown()) {
                textInput.replaceSelection("\n");
                return;
            }

            dialog.getDialogPane().requestFocus();
        });
        textInput.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            String c = e.getCharacter();
            if ("\r".equals(c) || "\n".equals(c)) {
                e.consume();
            }
        });
        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            if (suppressEnterRelease[0] && e.getCode() == KeyCode.ENTER) {
                suppressEnterRelease[0] = false;
                e.consume();
            }
        });
    }

    public static String blankAsDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static int wordCount(String value) {
        if (value == null || value.isBlank()) return 0;
        int count = 0;
        boolean inToken = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c)) {
                inToken = false;
            } else if (isCjk(c)) {
                count++;
                inToken = false;
            } else if (!inToken) {
                count++;
                inToken = true;
            }
        }
        return count;
    }

    public static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    /** Custom tooltip popup for icon buttons. */
    public static void tip(Button btn, String text, String anchor) {
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
}
