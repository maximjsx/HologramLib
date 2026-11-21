package com.maximde.hologramlib.hologram.custom;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.maximde.hologramlib.hologram.BlockHologram;
import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.MotionAnimation;
import com.maximde.hologramlib.hologram.MotionAnimation.Keyframe;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A panel of block displays behind holograms, like a sign board: a background, an optional border around it and an
 * optional progress bar along its bottom that the client empties smoothly with a single packet.
 * <p>
 * It faces the yaw of the location it is spawned at, so put the holograms in front of it with a fixed billboard.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@RequiredArgsConstructor
public class Backdrop {

    private static final float DEPTH = 0.02f;
    private static final float PANEL_Z = -0.04f;
    private static final float BAR_Z = -0.015f;

    @Getter
    private final String id;

    @Getter @Setter @Accessors(chain = true)
    private Material background = Material.BLACK_CONCRETE;

    @Getter @Setter @Accessors(chain = true)
    private @Nullable Material border;

    @Getter @Setter @Accessors(chain = true)
    private float borderThickness = 0.04f;

    /**
     * Blocks of background around the content on every side.
     */
    @Getter @Setter @Accessors(chain = true)
    private float padding = 0.15f;

    @Getter @Setter @Accessors(chain = true)
    private @Nullable Material barBackground;

    @Getter @Setter @Accessors(chain = true)
    private @Nullable Material barForeground;

    @Getter @Setter @Accessors(chain = true)
    private float barHeight = 0.04f;

    private final List<BlockHologram> parts = new ArrayList<>();
    private @Nullable BlockHologram bar;

    public Backdrop setProgressBar(Material background, Material foreground) {
        this.barBackground = background;
        this.barForeground = foreground;
        return this;
    }

    public boolean hasProgressBar() {
        return barBackground != null && barForeground != null;
    }

    /**
     * @param location the bottom middle of the content it goes behind, its yaw is the way the panel faces
     * @param width    width of the content in blocks
     * @param height   height of the content in blocks
     */
    public Backdrop spawn(HologramManager manager, Location location, float width, float height) {
        float bottom = padding + (hasProgressBar() ? barHeight * 3 : 0);
        float panelWidth = width + 2 * padding;
        float panelHeight = height + padding + bottom;
        float left = -panelWidth / 2;

        part(manager, location, "panel", background, left, -bottom, PANEL_Z, panelWidth, panelHeight);
        if (border != null) {
            float edge = borderThickness;
            part(manager, location, "border_top", border, left - edge, height + padding, PANEL_Z, panelWidth + 2 * edge, edge);
            part(manager, location, "border_bottom", border, left - edge, -bottom - edge, PANEL_Z, panelWidth + 2 * edge, edge);
            part(manager, location, "border_left", border, left - edge, -bottom, PANEL_Z, edge, panelHeight);
            part(manager, location, "border_right", border, -left, -bottom, PANEL_Z, edge, panelHeight);
        }
        if (hasProgressBar()) {
            float barY = -bottom + barHeight;
            part(manager, location, "bar", barBackground, -width / 2, barY, BAR_Z, width, barHeight);
            bar = part(manager, location, "bar_fill", barForeground, -width / 2, barY, BAR_Z + DEPTH / 2, width, barHeight);
        }
        return this;
    }

    /**
     * Fills the progress bar and lets it run empty over {@code ticks}.
     */
    public void runProgressBar(HologramManager manager, int ticks) {
        if (bar == null) return;
        // scale from the left end so it empties right to left
        manager.applyMotion(bar, MotionAnimation.of(false,
                Keyframe.scaled(1, 1, 1, 0),
                Keyframe.scaled(0, 1, 1, Math.max(1, ticks))));
    }

    public void remove(HologramManager manager) {
        parts.forEach(manager::remove);
        parts.clear();
        bar = null;
    }

    private BlockHologram part(HologramManager manager, Location location, String name, Material material,
                               float x, float y, float z, float width, float height) {
        BlockHologram part = new BlockHologram(id + "_" + name)
                .setBlock(blockState(material))
                .setBillboard(Display.Billboard.FIXED)
                .setTranslation(x, y, z)
                .setScale(width, height, DEPTH);
        manager.spawn(part, location, false);
        parts.add(part);
        return part;
    }

    private static int blockState(Material material) {
        return WrappedBlockState.getByString(Bukkit.createBlockData(material).getAsString()).getGlobalId();
    }
}
