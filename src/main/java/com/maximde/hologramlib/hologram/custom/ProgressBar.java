package com.maximde.hologramlib.hologram.custom;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.maximde.hologramlib.hologram.BlockHologram;
import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.TextHologram;
import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.TaskHandle;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A bar of block displays with a label over it, for goals like votes or a community chest. {@link #setProgress}
 * grows the bar smoothly on the client and counts the label up along with it. Faces the yaw of its location.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@RequiredArgsConstructor
public class ProgressBar {

    // slightly in front so it doesn't flicker into the track
    private static final float FILL_Z = 0.005f;

    @Getter
    private final String id;

    @Getter @Setter @Accessors(chain = true)
    private float width = 2;

    @Getter @Setter @Accessors(chain = true)
    private float height = 0.12f;

    @Getter @Setter @Accessors(chain = true)
    private Material track = Material.GRAY_CONCRETE;

    @Getter @Setter @Accessors(chain = true)
    private Material fill = Material.LIME_CONCRETE;

    /**
     * MiniMessage over the bar with {@code {percent}}, {@code {value}} and {@code {goal}}.
     */
    @Getter @Setter @Accessors(chain = true)
    private String labelFormat = "<white>{value} <gray>/ {goal} <dark_gray>(<white>{percent}%<dark_gray>)";

    @Getter @Setter @Accessors(chain = true)
    private double goal = 100;

    @Getter
    private volatile double value;
    private @Nullable BlockHologram trackPart;
    private @Nullable BlockHologram fillPart;
    private @Nullable TextHologram label;
    private @Nullable TaskHandle counting;

    public ProgressBar spawn(HologramManager manager, Location location) {
        Location facing = location.clone();
        facing.setPitch(0);
        trackPart = part("track", track, width, 0);
        fillPart = part("fill", fill, width * fraction(value), FILL_Z);
        label = new TextHologram(id + "_label")
                .setMiniMessageText(label(value))
                .setBillboard(Display.Billboard.FIXED)
                .setTranslation(0, height + 0.05f, 0);
        manager.spawn(trackPart, facing, false);
        manager.spawn(fillPart, facing, false);
        manager.spawn(label, facing, false);
        return this;
    }

    /**
     * Moves the bar to {@code value} over {@code ticks}, counting the label along.
     */
    public ProgressBar setProgress(double value, int ticks) {
        double from = this.value;
        this.value = value;
        BlockHologram bar = fillPart;
        if (bar == null) return this;
        bar.setInterpolationDurationTransformation(Math.max(0, ticks))
                .setScale(width * fraction(value), height, 0.02f)
                .update();
        countLabel(from, value, Math.max(1, ticks));
        return this;
    }

    public void remove(HologramManager manager) {
        if (counting != null) counting.cancel();
        if (trackPart != null) manager.remove(trackPart);
        if (fillPart != null) manager.remove(fillPart);
        if (label != null) manager.remove(label);
    }

    private void countLabel(double from, double to, int ticks) {
        if (counting != null) counting.cancel();
        TextHologram text = label;
        if (text == null) return;
        AtomicInteger tick = new AtomicInteger();
        AtomicReference<TaskHandle> task = new AtomicReference<>();
        task.set(BukkitTasks.runTaskTimer(() -> {
            int now = Math.min(ticks, tick.incrementAndGet());
            text.setMiniMessageText(label(from + (to - from) * now / ticks)).update();
            if (now == ticks) Optional.ofNullable(task.get()).ifPresent(TaskHandle::cancel);
        }, 1L, 1L));
        counting = task.get();
    }

    private String label(double shown) {
        return labelFormat
                .replace("{percent}", String.valueOf(Math.round(fraction(shown) * 100)))
                .replace("{value}", String.format(Locale.US, "%,d", Math.round(shown)))
                .replace("{goal}", String.format(Locale.US, "%,d", Math.round(goal)));
    }

    private float fraction(double shown) {
        return goal <= 0 ? 0 : (float) Math.max(0, Math.min(1, shown / goal));
    }

    // grows from the left end
    private BlockHologram part(String name, Material material, float partWidth, float z) {
        return new BlockHologram(id + "_" + name)
                .setBlock(WrappedBlockState.getByString(Bukkit.createBlockData(material).getAsString()).getGlobalId())
                .setBillboard(Display.Billboard.FIXED)
                .setTranslation(-width / 2, 0, z)
                .setScale(partWidth, height, 0.02f);
    }
}
