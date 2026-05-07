package example.aiwbs.ui;

import javafx.scene.control.Button;
import javafx.scene.control.TextInputDialog;
import java.util.Optional;

public final class
ViewUtils {
    private ViewUtils() {}

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

    public static Optional<String> prompt(String title, String initialValue) {
        TextInputDialog dialog = new TextInputDialog(initialValue);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        return dialog.showAndWait();
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
