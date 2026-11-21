package com.maximde.hologramlib.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Map;

/**
 * Measures text in pixels of Minecraft's default font, so text can be lined up inside one text display. A text
 * display draws 40 of these pixels per block at scale 1.
 */
public final class FontWidth {

    public static final int PIXELS_PER_BLOCK = 40;
    public static final int LINE_HEIGHT = 10;

    private static final int DEFAULT_GLYPH = 5;
    // heads are 8px plus 1px spacing
    private static final int OBJECT_WIDTH = 9;
    private static final Map<Integer, Integer> GLYPHS = Map.ofEntries(
            Map.entry((int) ' ', 3), Map.entry((int) '!', 1), Map.entry((int) '"', 3), Map.entry((int) '\'', 1),
            Map.entry((int) '(', 3), Map.entry((int) ')', 3), Map.entry((int) '*', 3), Map.entry((int) ',', 1),
            Map.entry((int) '.', 1), Map.entry((int) ':', 1), Map.entry((int) ';', 1), Map.entry((int) '<', 4),
            Map.entry((int) '>', 4), Map.entry((int) '@', 6), Map.entry((int) 'I', 3), Map.entry((int) '[', 3),
            Map.entry((int) ']', 3), Map.entry((int) '`', 2), Map.entry((int) 'f', 4), Map.entry((int) 'i', 1),
            Map.entry((int) 'k', 4), Map.entry((int) 'l', 2), Map.entry((int) 't', 3), Map.entry((int) '{', 3),
            Map.entry((int) '|', 1), Map.entry((int) '}', 3));

    private FontWidth() {}

    /**
     * The width of the widest line of a MiniMessage text.
     */
    public static int of(String miniMessage) {
        return of(MiniMessage.get(miniMessage));
    }

    /**
     * The width of the widest line of {@code component}.
     */
    public static int of(Component component) {
        int[] lines = {0, 0};
        measure(component, false, lines);
        return Math.max(lines[0], lines[1]);
    }

    /**
     * How far one character moves the next one along, including the pixel of spacing and the extra pixel of bold.
     */
    public static int advance(int codePoint, boolean bold) {
        return GLYPHS.getOrDefault(codePoint, DEFAULT_GLYPH) + 1 + (bold ? 1 : 0);
    }

    // lines[0] widest finished line, lines[1] current one
    private static void measure(Component component, boolean parentBold, int[] lines) {
        TextDecoration.State state = component.decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.NOT_SET ? parentBold : state == TextDecoration.State.TRUE;
        if (component instanceof TextComponent text) {
            text.content().codePoints().forEach(codePoint -> {
                if (codePoint == '\n') {
                    lines[0] = Math.max(lines[0], lines[1]);
                    lines[1] = 0;
                    return;
                }
                lines[1] += advance(codePoint, bold);
            });
        } else if (component instanceof ObjectComponent) {
            lines[1] += OBJECT_WIDTH;
        }
        for (Component child : component.children()) {
            measure(child, bold, lines);
        }
    }
}
