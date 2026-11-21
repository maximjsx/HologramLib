package com.maximde.hologramlib.hologram.custom;

import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.MotionAnimation;
import com.maximde.hologramlib.hologram.TextHologram;
import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.FontWidth;
import com.maximde.hologramlib.utils.TaskHandle;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A column of clickable buttons in the world, like a small menu on a wall. The button a player looks at lights up for
 * that player only, a click runs its action. Faces the yaw of its location.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@RequiredArgsConstructor
public class HologramMenu implements HologramManager.Events {

    /**
     * @param label MiniMessage
     */
    public record Button(String label, Consumer<Player> action) {}

    private static final int NOT_LOOKING = -1;

    @Getter
    private final String id;

    private final List<Button> buttons = new CopyOnWriteArrayList<>();
    private final Map<UUID, Integer> hovered = new ConcurrentHashMap<>();
    private volatile List<TextHologram> holograms = List.of();
    private @Nullable TaskHandle hoverTask;
    private @Nullable Location location;

    /**
     * MiniMessage for a button nobody looks at, {@code {label}} is the button's label.
     */
    @Getter @Setter @Accessors(chain = true)
    private String format = "<gray>{label}";

    /**
     * MiniMessage for the button the viewer looks at.
     */
    @Getter @Setter @Accessors(chain = true)
    private String hoverFormat = "<#55dce0>» <white>{label}</white> «";

    /**
     * Blocks from the middle of one button to the middle of the next.
     */
    @Getter @Setter @Accessors(chain = true)
    private float spacing = 0.3f;

    @Getter @Setter @Accessors(chain = true)
    private float scale = 1f;

    @Getter @Setter @Accessors(chain = true)
    private int background = 0x60000000;

    /**
     * How far away a player can be and still light up buttons by looking at them.
     */
    @Getter @Setter @Accessors(chain = true)
    private double hoverDistance = 8;

    @Getter @Setter @Accessors(chain = true)
    private @Nullable String hoverSound = "block.note_block.hat";

    @Getter @Setter @Accessors(chain = true)
    private @Nullable String clickSound = "ui.button.click";

    /**
     * Only buttons added before spawning show up.
     */
    public HologramMenu addButton(String label, Consumer<Player> action) {
        buttons.add(new Button(label, action));
        return this;
    }

    /**
     * @param location where the bottom button sits, the others stack above it
     */
    public HologramMenu spawn(HologramManager manager, Location location) {
        Location facing = location.clone();
        facing.setPitch(0);
        this.location = facing;
        List<TextHologram> created = new ArrayList<>();
        for (int index = 0; index < buttons.size(); index++) {
            TextHologram button = createButton(manager, index);
            created.add(button);
            manager.spawn(button, facing.clone().add(0, rowY(index), 0), false);
        }
        holograms = List.copyOf(created);
        manager.registerEventHandler(this);
        hoverTask = BukkitTasks.runTaskTimer(this::updateHover, 2L, 2L);
        return this;
    }

    public void remove(HologramManager manager) {
        if (hoverTask != null) hoverTask.cancel();
        hoverTask = null;
        manager.removeEventHandler(this);
        holograms.forEach(manager::remove);
        holograms = List.of();
        hovered.clear();
    }

    @Override
    public void onJoin(Player player) {}

    @Override
    public void onQuit(Player player) {
        hovered.remove(player.getUniqueId());
    }

    private TextHologram createButton(HologramManager manager, int index) {
        Button button = buttons.get(index);
        String widest = hoverFormat.replace("{label}", button.label());
        TextHologram hologram = new TextHologram(id + "_button_" + index)
                .setMiniMessageText(widest)
                .setBillboard(Display.Billboard.FIXED)
                .setScale(scale, scale, scale)
                .setBackgroundColor(background)
                .setTextProvider(viewer -> (hovered.getOrDefault(viewer.getUniqueId(), NOT_LOOKING) == index
                        ? hoverFormat : format).replace("{label}", button.label()));
        hologram.onInteract(player -> click(manager, player, index));
        hologram.getInteractionBox()
                .setWidth(buttonWidth(index) + 0.1f)
                .setHeight(FontWidth.LINE_HEIGHT * scale / FontWidth.PIXELS_PER_BLOCK + 0.1f);
        return hologram;
    }

    // boxes are as deep as wide, so wide buttons catch clicks meant for others. the highlighted one wins
    private void click(HologramManager manager, Player player, int clickedBox) {
        int highlighted = hovered.getOrDefault(player.getUniqueId(), NOT_LOOKING);
        int index = highlighted == NOT_LOOKING ? clickedBox : highlighted;
        List<TextHologram> shown = holograms;
        if (index >= shown.size() || index >= buttons.size()) return;
        String sound = clickSound;
        if (sound != null) player.playSound(player.getLocation(), sound, 1, 1);
        manager.applyMotion(shown.get(index), MotionAnimation.of(false,
                MotionAnimation.Keyframe.scaled(0.9f, 0.9f, 0.9f, 2), MotionAnimation.Keyframe.scaled(1, 1, 1, 3)));
        buttons.get(index).action().accept(player);
    }

    private void updateHover() {
        Location origin = location;
        if (origin == null || origin.getWorld() == null) return;
        for (Player player : origin.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(origin) > hoverDistance * hoverDistance) {
                setHovered(player, NOT_LOOKING);
                continue;
            }
            setHovered(player, lookedAt(player, origin));
        }
    }

    private void setHovered(Player player, int index) {
        Integer before = hovered.put(player.getUniqueId(), index);
        int previous = before == null ? NOT_LOOKING : before;
        if (previous == index) return;
        List<TextHologram> shown = holograms;
        if (previous != NOT_LOOKING && previous < shown.size()) shown.get(previous).refreshTexts();
        if (index == NOT_LOOKING || index >= shown.size()) return;
        shown.get(index).refreshTexts();
        String sound = hoverSound;
        if (sound != null) player.playSound(player.getLocation(), sound, 0.4f, 1.6f);
    }

    // where the player looks on the menu and which button is there
    private int lookedAt(Player player, Location origin) {
        double yaw = Math.toRadians(origin.getYaw());
        Vector front = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector right = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        double facing = direction.dot(front);
        if (Math.abs(facing) < 1e-6) return NOT_LOOKING;
        double distance = origin.toVector().subtract(eye.toVector()).dot(front) / facing;
        if (distance <= 0 || distance > hoverDistance) return NOT_LOOKING;
        Vector hit = eye.toVector().add(direction.multiply(distance)).subtract(origin.toVector());
        double x = hit.dot(right);
        double y = hit.getY();
        float lineHeight = FontWidth.LINE_HEIGHT * scale / FontWidth.PIXELS_PER_BLOCK;
        for (int index = 0; index < buttons.size(); index++) {
            double bottom = rowY(index);
            boolean inRow = y >= bottom - 0.03 && y <= bottom + lineHeight + 0.03;
            if (inRow && Math.abs(x) <= buttonWidth(index) / 2 + 0.05) return index;
        }
        return NOT_LOOKING;
    }

    private float rowY(int index) {
        return (buttons.size() - 1 - index) * spacing;
    }

    private float buttonWidth(int index) {
        return FontWidth.of(hoverFormat.replace("{label}", buttons.get(index).label())) * scale / FontWidth.PIXELS_PER_BLOCK;
    }
}
