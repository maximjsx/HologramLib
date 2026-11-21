package com.maximde.hologramlib.hologram.effect;

import com.maximde.hologramlib.hologram.Hologram;
import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.TextHologram;
import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.FontWidth;
import com.maximde.hologramlib.utils.TaskHandle;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Blows a text hologram apart into its letters, which fly off, tumble, bounce on the ground and shrink away. The
 * hologram is removed. Everything is packets, the letters land on the first solid block under the hologram.
 * <p>
 * Call it on the thread that owns the hologram's location, it looks up the ground there.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@RequiredArgsConstructor
public final class Shatter {

    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final int FADE_TICKS = 8;
    private static final int GROUND_SEARCH = 12;

    private final HologramManager manager;

    /**
     * How hard the pieces fly apart in blocks per tick
     */
    @Getter @Setter @Accessors(chain = true)
    private double force = 0.22;

    /**
     * Ticks until the pieces are gone
     */
    @Getter @Setter @Accessors(chain = true)
    private int lifetime = 70;

    @Getter @Setter @Accessors(chain = true)
    private @Nullable SoundCue sound = SoundCue.SHATTER;

    public void explode(TextHologram text, float yaw) {
        Transformation shown = text.getTransformation();
        Location anchor = text.getLocation();
        if (anchor == null) return;
        manager.remove(text);
        Location facing = anchor.clone();
        facing.setYaw(yaw);
        facing.setPitch(0);
        float scale = shown.getScale().y;
        float pixel = scale / FontWidth.PIXELS_PER_BLOCK;
        float lineHeight = FontWidth.LINE_HEIGHT * pixel;
        List<List<Letter>> lines = letters(text.getTextAsComponent());
        Vector3f textMiddle = local(shown, new Vector3f(0, lines.size() * FontWidth.LINE_HEIGHT * scale / FontWidth.PIXELS_PER_BLOCK / 2, 0));
        List<Piece> pieces = new ArrayList<>();
        for (int row = 0; row < lines.size(); row++) {
            List<Letter> line = lines.get(row);
            int lineWidth = line.stream().mapToInt(Letter::advance).sum();
            float x = -lineWidth / 2f;
            float bottom = (lines.size() - 1 - row) * FontWidth.LINE_HEIGHT;
            for (Letter letter : line) {
                float center = x + letter.advance() / 2f;
                x += letter.advance();
                if (Character.isWhitespace(letter.codePoint())) continue;
                Vector3f middle = local(shown, new Vector3f(center * pixel, bottom * pixel + lineHeight / 2, 0));
                TextHologram piece = new TextHologram(nextId())
                        .setText(Component.text(Character.toString(letter.codePoint()), letter.style()))
                        .setBillboard(Display.Billboard.FIXED)
                        .setBackgroundColor(0)
                        .setShadow(true);
                pieces.add(piece(piece, facing, middle, textMiddle, new Quaternionf(shown.getLeftRotation()),
                        new Vector3f(scale), lineHeight, new Vector3f(0, lineHeight / 2, 0)));
            }
        }
        start(facing, pieces);
    }

    private <H extends Hologram<H>> Piece piece(H hologram, Location anchor, Vector3f middle, Vector3f burstMiddle,
                                                Quaternionf rotation, Vector3f scale, float height, Vector3f center) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector3f away = new Vector3f(middle).sub(burstMiddle);
        if (away.lengthSquared() < 1e-6) away.set(random.nextFloat() - 0.5f, 0, random.nextFloat() - 0.5f);
        away.normalize();
        Vector3d velocity = new Vector3d(away).mul(force * random.nextDouble(0.6, 1.4))
                .add(random.nextDouble(-0.05, 0.05), random.nextDouble(0.05, 0.18), random.nextDouble(-0.05, 0.05));
        Piece piece = new Piece(hologram, new Vector3d(middle), velocity, rotation, scale, height, center);
        hologram.setTransformation(piece.transformation(1));
        manager.spawn(hologram, anchor, false);
        return piece;
    }

    private void start(Location anchor, List<Piece> pieces) {
        if (sound != null) sound.play(anchor);
        double ground = groundBelow(anchor) - anchor.getY();
        int ticks = Math.max(FADE_TICKS + 1, lifetime);
        AtomicInteger age = new AtomicInteger();
        AtomicReference<TaskHandle> task = new AtomicReference<>();
        task.set(BukkitTasks.runTaskTimer(() -> {
            int left = ticks - age.incrementAndGet();
            if (left <= 0) {
                pieces.forEach(piece -> manager.remove(piece.hologram()));
                Optional.ofNullable(task.get()).ifPresent(TaskHandle::cancel);
                return;
            }
            float shrink = left < FADE_TICKS ? (float) left / FADE_TICKS : 1;
            for (Piece piece : pieces) {
                if (piece.step(ground) || shrink < 1) piece.hologram().showTransformation(piece.transformation(shrink), 1);
            }
        }, 2L, 1L));
    }

    private static Vector3f local(Transformation shown, Vector3f point) {
        return shown.getLeftRotation().transform(new Vector3f(point)).add(shown.getTranslation());
    }

    private static double groundBelow(Location location) {
        if (location.getWorld() == null) return location.getY() - 3;
        for (int depth = 0; depth < GROUND_SEARCH; depth++) {
            Location below = location.clone().subtract(0, depth, 0);
            if (below.getBlock().getType().isSolid()) return below.getBlockY() + 1;
        }
        return location.getY() - GROUND_SEARCH;
    }

    private static String nextId() {
        return "shatter_" + NEXT_ID.incrementAndGet();
    }

    private record Letter(int codePoint, Style style, int advance) {}

    private static List<List<Letter>> letters(Component text) {
        List<List<Letter>> lines = new ArrayList<>();
        lines.add(new ArrayList<>());
        Deque<Style> styles = new ArrayDeque<>();
        styles.push(Style.empty());
        ComponentFlattener.basic().flatten(text, new FlattenerListener() {
            @Override
            public void pushStyle(@NotNull Style style) {
                styles.push(style.merge(styles.peek(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET));
            }

            @Override
            public void component(@NotNull String content) {
                Style style = styles.peek();
                boolean bold = style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
                content.codePoints().forEach(codePoint -> {
                    if (codePoint == '\n') {
                        lines.add(new ArrayList<>());
                        return;
                    }
                    lines.get(lines.size() - 1).add(new Letter(codePoint, style, FontWidth.advance(codePoint, bold)));
                });
            }

            @Override
            public void popStyle(@NotNull Style style) {
                styles.pop();
            }
        });
        return lines;
    }
}
