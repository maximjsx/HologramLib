package com.maximde.hologramlib.persistence.importer;

import com.maximde.hologramlib.persistence.ClickAction;
import com.maximde.hologramlib.persistence.HologramData;
import org.bukkit.entity.Display;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Lines stacked from the top down like in DecentHolograms and Holographic Displays which become a single hologram
 */
final class StackedLines {

    enum Kind { TEXT, ICON, OTHER }

    /**
     * @param value MiniMessage for text lines, the item key for icons
     */
    record Line(Kind kind, String value, double height) {}

    private static final HologramData.Scale ICON_SCALE = HologramData.Scale.uniform(0.5f);

    // text displays grow up from their location and item displays sit centered on it
    private record Layout(List<String> texts, double textBottom, @Nullable String icon, double iconCenter) {

        static Layout of(List<Line> lines, double top) {
            List<String> texts = new ArrayList<>();
            double textBottom = top;
            String icon = null;
            double iconCenter = top;
            double lineTop = top;
            for (Line line : lines) {
                if (line.kind() == Kind.TEXT) {
                    texts.add(line.value());
                    textBottom = lineTop - line.height();
                } else if (line.kind() == Kind.ICON && icon == null) {
                    icon = line.value();
                    iconCenter = lineTop - line.height() / 2;
                }
                lineTop -= line.height();
            }
            return new Layout(texts, textBottom, icon, iconCenter);
        }
    }

    private StackedLines() {}

    static double height(List<Line> lines) {
        return lines.stream().mapToDouble(Line::height).sum();
    }

    static List<HologramData> toHolograms(String id, Position top, float yaw, List<Line> lines,
                                          @Nullable String viewPermission, List<ClickAction> actions) {
        Layout layout = Layout.of(lines, top.y());
        List<HologramData> holograms = new ArrayList<>();
        if (!layout.texts().isEmpty()) {
            String text = String.join("<newline>", layout.texts());
            holograms.add(new HologramData(id, HologramData.Type.TEXT, top.world(), top.x(), layout.textBottom(), top.z(),
                    yaw, text, HologramData.Scale.ONE, Display.Billboard.CENTER, null, true, text.contains("%"),
                    viewPermission, actions));
        }
        if (layout.icon() != null) {
            String iconId = layout.texts().isEmpty() ? id : id + "_icon";
            holograms.add(new HologramData(iconId, HologramData.Type.ITEM, top.world(), top.x(), layout.iconCenter(), top.z(),
                    yaw, layout.icon(), ICON_SCALE, Display.Billboard.VERTICAL, null, true, false,
                    viewPermission, actions));
        }
        return holograms;
    }
}
