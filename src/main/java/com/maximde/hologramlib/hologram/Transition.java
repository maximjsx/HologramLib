package com.maximde.hologramlib.hologram;

import com.maximde.hologramlib.hologram.MotionAnimation.Keyframe;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Hides a hologram, lets its content change while nobody can see it, and shows it again. Played by
 * {@link HologramManager#transition}. Every step is interpolated by the client, so it stays smooth at any frame rate.
 */
public enum Transition {

    /**
     * Changes right away.
     */
    NONE,

    /**
     * Tips over around its horizontal middle like a split-flap sign and comes back from the other side.
     */
    FLIP,

    /**
     * Turns around its vertical axis like a card.
     */
    TURN,

    /**
     * Folds flat into a line and unfolds again.
     */
    SQUASH,

    /**
     * Shrinks to nothing and grows back.
     */
    POP,

    /**
     * Slides off to the left while folding up and unfolds in from the right, like a page swiped aside. It never leaves
     * its own width, so it stays on a panel behind it.
     */
    SLIDE,

    /**
     * Swings away around its top edge and the next one swings down in its place, like the flaps of a departure board.
     * Best with {@code TextLeaderboard#setSplitRows}, so every row is a flap of its own.
     */
    FLAP,

    /**
     * Sinks into its bottom edge and the next one unrolls from the top, stretching a little as it lands.
     */
    DROP,

    /**
     * Spins away into a point and spins back out of it.
     */
    SPIN;

    private static final float QUARTER_TURN = (float) Math.toRadians(90);

    /**
     * @param width  the hologram's width in blocks
     * @param height the hologram's height in blocks, it turns and folds around its middle
     */
    MotionAnimation out(int ticks, float width, float height) {
        return MotionAnimation.of(false, hidden(1, ticks, width, height / 2));
    }

    MotionAnimation in(int ticks, float width, float height) {
        Keyframe start = hidden(-1, 0, width, height / 2);
        if (this != DROP) return MotionAnimation.of(false, start, Keyframe.offset(0, 0, 0, ticks));
        int fall = Math.max(1, ticks * 2 / 3);
        return MotionAnimation.of(false, start,
                Keyframe.scaled(1, 1.15f, 1, fall),
                Keyframe.offset(0, 0, 0, Math.max(1, ticks - fall)));
    }

    private Keyframe hidden(int side, int ticks, float width, float pivotHeight) {
        return switch (this) {
            case NONE -> Keyframe.offset(0, 0, 0, ticks);
            case FLIP -> turned(new Quaternionf().rotateX(side * QUARTER_TURN), ticks, pivotHeight);
            case TURN -> turned(new Quaternionf().rotateY(side * QUARTER_TURN), ticks, pivotHeight);
            case SQUASH -> new Keyframe(new Vector3f(0, pivotHeight, 0), new Quaternionf(), new Vector3f(1, 0, 1), ticks);
            case POP -> new Keyframe(new Vector3f(0, pivotHeight, 0), new Quaternionf(), new Vector3f(0, 0, 0), ticks);
            case SLIDE -> new Keyframe(new Vector3f(-side * width / 2, 0, 0), new Quaternionf(), new Vector3f(0, 1, 1), ticks);
            case FLAP -> turned(new Quaternionf().rotateX(side * QUARTER_TURN), ticks, 2 * pivotHeight);
            case DROP -> new Keyframe(new Vector3f(0, side > 0 ? 0 : 2 * pivotHeight, 0), new Quaternionf(),
                    new Vector3f(1, 0, 1), ticks);
            case SPIN -> new Keyframe(new Vector3f(0, pivotHeight, 0),
                    new Quaternionf().rotateZ(side * (float) Math.toRadians(120)), new Vector3f(0, 0, 0), ticks);
        };
    }

    private static Keyframe turned(Quaternionf rotation, int ticks, float pivotHeight) {
        Vector3f pivot = new Vector3f(0, pivotHeight, 0);
        Vector3f shift = new Vector3f(pivot).sub(rotation.transform(new Vector3f(pivot)));
        return new Keyframe(shift, rotation, new Vector3f(1, 1, 1), ticks);
    }
}
