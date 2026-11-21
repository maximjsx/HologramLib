package com.maximde.hologramlib.hologram;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves, turns or scales any hologram through keyframes, started with {@link HologramManager#applyMotion}.
 * <p>
 * Every keyframe is relative to the hologram's own translation, rotation and scale, so a motion works on top of
 * whatever the hologram already looks like. The client interpolates between keyframes, so even a few keyframes
 * look smooth and the server only sends one small packet per keyframe.
 */
public final class MotionAnimation {

    /**
     * @param translation added to the hologram's translation
     * @param rotation    applied on top of the hologram's rotation
     * @param scale       multiplies the hologram's scale
     * @param ticks       how long the client takes to move from the previous keyframe to this one
     */
    public record Keyframe(Vector3f translation, Quaternionf rotation, Vector3f scale, int ticks) {

        public Keyframe {
            if (ticks < 0) throw new IllegalArgumentException("A keyframe cannot take negative ticks");
            translation = new Vector3f(translation);
            rotation = new Quaternionf(rotation);
            scale = new Vector3f(scale);
        }

        public static Keyframe offset(float x, float y, float z, int ticks) {
            return new Keyframe(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(1, 1, 1), ticks);
        }

        public static Keyframe scaled(float x, float y, float z, int ticks) {
            return new Keyframe(new Vector3f(), new Quaternionf(), new Vector3f(x, y, z), ticks);
        }

        public static Keyframe turnedY(float degrees, int ticks) {
            return new Keyframe(new Vector3f(), new Quaternionf().rotateY((float) Math.toRadians(degrees)),
                    new Vector3f(1, 1, 1), ticks);
        }
    }

    private final List<Keyframe> keyframes;
    private final boolean loop;

    private MotionAnimation(List<Keyframe> keyframes, boolean loop) {
        if (keyframes.isEmpty()) throw new IllegalArgumentException("A motion needs at least one keyframe");
        this.keyframes = List.copyOf(keyframes);
        this.loop = loop;
    }

    public static MotionAnimation of(boolean loop, Keyframe... keyframes) {
        return new MotionAnimation(List.of(keyframes), loop);
    }

    /**
     * Turns around the vertical axis forever, one full turn every {@code ticksPerTurn} ticks.
     */
    public static MotionAnimation spin(int ticksPerTurn) {
        // client turns the short way, so steps under 180 degrees
        int step = Math.max(1, ticksPerTurn / 4);
        return of(true,
                Keyframe.turnedY(90, step),
                Keyframe.turnedY(180, step),
                Keyframe.turnedY(270, step),
                Keyframe.turnedY(360, step));
    }

    /**
     * Floats up by {@code height} blocks and back down, forever.
     */
    public static MotionAnimation floating(float height, int periodTicks) {
        int half = Math.max(1, periodTicks / 2);
        return of(true,
                Keyframe.offset(0, height, 0, half),
                Keyframe.offset(0, 0, 0, half));
    }

    /**
     * Jumps up by {@code height} blocks and lands with a short squash, forever.
     */
    public static MotionAnimation bounce(float height, int periodTicks) {
        int air = Math.max(2, periodTicks - 4) / 2;
        return of(true,
                Keyframe.offset(0, height, 0, air),
                Keyframe.offset(0, 0, 0, air),
                Keyframe.scaled(1.15f, 0.85f, 1.15f, 2),
                Keyframe.scaled(1, 1, 1, 2));
    }

    /**
     * Grows to {@code factor} times its size and shrinks back, forever.
     */
    public static MotionAnimation pulse(float factor, int periodTicks) {
        int half = Math.max(1, periodTicks / 2);
        return of(true,
                Keyframe.scaled(factor, factor, factor, half),
                Keyframe.scaled(1, 1, 1, half));
    }

    /**
     * Stretches to {@code factor} times its height and back, forever.
     */
    public static MotionAnimation stretch(float factor, int periodTicks) {
        int half = Math.max(1, periodTicks / 2);
        return of(true,
                Keyframe.scaled(1, factor, 1, half),
                Keyframe.scaled(1, 1, 1, half));
    }

    /**
     * Grows from nothing to its full size once, nice right after spawning.
     */
    public static MotionAnimation popIn(int ticks) {
        return of(false,
                Keyframe.scaled(0, 0, 0, 0),
                Keyframe.scaled(1.1f, 1.1f, 1.1f, Math.max(1, ticks - 2)),
                Keyframe.scaled(1, 1, 1, 2));
    }

    /**
     * Shrinks to nothing once, nice right before removing.
     */
    public static MotionAnimation popOut(int ticks) {
        return of(false,
                Keyframe.scaled(1.1f, 1.1f, 1.1f, 2),
                Keyframe.scaled(0, 0, 0, Math.max(1, ticks - 2)));
    }

    /**
     * Tilts {@code degrees} to each side and back, forever, like a sign swinging on a chain.
     */
    public static MotionAnimation swing(float degrees, int periodTicks) {
        int quarter = Math.max(1, periodTicks / 4);
        return of(true,
                tilted(degrees, quarter),
                tilted(0, quarter),
                tilted(-degrees, quarter),
                tilted(0, quarter));
    }

    /**
     * Rattles from side to side once, to draw attention or to answer a click.
     */
    public static MotionAnimation shake(float strength, int ticks) {
        int steps = Math.max(2, ticks / 2);
        Keyframe[] keyframes = new Keyframe[steps + 1];
        for (int step = 0; step < steps; step++) {
            float side = step % 2 == 0 ? 1 : -1;
            // each swing smaller so it settles
            float fade = 1 - (float) step / steps;
            keyframes[step] = Keyframe.offset(side * strength * fade, 0, 0, 2);
        }
        keyframes[steps] = Keyframe.offset(0, 0, 0, 2);
        return of(false, keyframes);
    }

    /**
     * Wobbles like jelly once: squashed flat, then stretched tall, smaller each time until it settles. It keeps its
     * volume, so it gets wider while it is flat. It squashes towards its origin, the bottom of a text.
     *
     * @param strength how far it squashes at first, 0.3 is a good hit
     */
    public static MotionAnimation jelly(float strength, int ticks) {
        int steps = Math.max(2, ticks / 2);
        Keyframe[] keyframes = new Keyframe[steps + 1];
        for (int step = 0; step < steps; step++) {
            float amount = -strength * (float) (Math.cos(step * Math.PI * 0.85) * Math.pow(0.65, step));
            float along = Math.max(0.05f, 1 + amount);
            float across = (float) (1 / Math.sqrt(along));
            keyframes[step] = Keyframe.scaled(across, along, across, 2);
        }
        keyframes[steps] = Keyframe.scaled(1, 1, 1, 2);
        return of(false, keyframes);
    }

    private static Keyframe tilted(float degrees, int ticks) {
        return new Keyframe(new Vector3f(), new Quaternionf().rotateZ((float) Math.toRadians(degrees)),
                new Vector3f(1, 1, 1), ticks);
    }

    public List<Keyframe> keyframes() {
        return keyframes;
    }

    public boolean loops() {
        return loop;
    }

    /**
     * Plays one motion on one hologram. Run every tick, it sends the next keyframe once the current one is done.
     */
    static final class Playback {

        private final Hologram<?> hologram;
        private final MotionAnimation motion;
        private final Vector3f baseTranslation;
        private final Quaternionf baseRotation;
        private final Vector3f baseScale;
        private int index;
        private int ticksLeft;

        Playback(Hologram<?> hologram, MotionAnimation motion) {
            this.hologram = hologram;
            this.motion = motion;
            this.baseTranslation = new Vector3f(hologram.translation);
            this.baseRotation = new Quaternionf(hologram.leftRotation.getX(), hologram.leftRotation.getY(),
                    hologram.leftRotation.getZ(), hologram.leftRotation.getW());
            this.baseScale = new Vector3f(hologram.scale);
        }

        /**
         * @return false once a motion that does not loop has played its last keyframe
         */
        boolean tick() {
            if (hologram.isDead()) return true;
            if (ticksLeft-- > 0) return true;
            if (index == motion.keyframes.size()) {
                if (!motion.loop) return false;
                index = 0;
            }
            Keyframe keyframe = motion.keyframes.get(index++);
            hologram.sendTransformation(
                    new Vector3f(baseTranslation).add(keyframe.translation()),
                    new Quaternionf(keyframe.rotation()).mul(baseRotation),
                    new Vector3f(baseScale).mul(keyframe.scale()),
                    keyframe.ticks());
            ticksLeft = keyframe.ticks() - 1;
            return true;
        }

        void restore() {
            if (hologram.isDead()) return;
            hologram.sendTransformation(baseTranslation, baseRotation, baseScale, 0);
        }
    }

    /**
     * Builds a custom motion keyframe by keyframe.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<Keyframe> keyframes = new ArrayList<>();
        private boolean loop = true;

        public Builder keyframe(Keyframe keyframe) {
            keyframes.add(keyframe);
            return this;
        }

        public Builder loop(boolean loop) {
            this.loop = loop;
            return this;
        }

        public MotionAnimation build() {
            return new MotionAnimation(keyframes, loop);
        }
    }
}
