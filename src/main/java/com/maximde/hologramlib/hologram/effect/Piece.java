package com.maximde.hologramlib.hologram.effect;

import com.maximde.hologramlib.hologram.Hologram;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * One flying piece of a shattered hologram: it falls, tumbles, bounces off the ground and slides to a stop. Positions
 * are in the frame of the location its display was spawned at.
 */
final class Piece {

    private static final double GRAVITY = 0.04;
    private static final double DRAG = 0.98;
    private static final double BOUNCE = 0.35;
    private static final double FRICTION = 0.6;
    private static final double REST_SPEED = 0.03;

    private final Hologram<?> hologram;
    private final Vector3d position;
    private final Vector3d velocity;
    private final Quaternionf rotation;
    private final Vector3f axis;
    private final Vector3f scale;
    private final double halfHeight;
    // origin to middle, before rotating
    private final Vector3f middle;
    private double spin;
    private boolean resting;

    /**
     * @param position the middle of the piece
     * @param height   how tall the piece looks, for landing on the ground
     * @param middle   from the display's origin to the middle of what it shows, at full size and before turning
     */
    Piece(Hologram<?> hologram, Vector3d position, Vector3d velocity, Quaternionf rotation, Vector3f scale, float height,
          Vector3f middle) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        this.hologram = hologram;
        this.position = position;
        this.velocity = velocity;
        this.rotation = rotation;
        this.scale = scale;
        this.halfHeight = height / 2.0;
        this.middle = middle;
        this.axis = new Vector3f((float) random.nextGaussian(), (float) random.nextGaussian(), (float) random.nextGaussian())
                .normalize();
        // big pieces tumble slow, small ones faster, capped
        double limit = Math.max(0.1, Math.min(0.45, 0.1 / Math.max(0.01, height)));
        this.spin = Math.min(limit, velocity.length() * random.nextDouble(0.6, 1.4) * 4);
    }

    Hologram<?> hologram() {
        return hologram;
    }

    /**
     * @return whether it moved
     */
    boolean step(double groundY) {
        if (resting) return false;
        velocity.y -= GRAVITY;
        velocity.mul(DRAG);
        position.add(velocity);
        rotation.rotateAxis((float) spin, axis);
        if (position.y - halfHeight >= groundY) return true;
        position.y = groundY + halfHeight;
        if (velocity.y < 0) velocity.y = -velocity.y * BOUNCE;
        velocity.x *= FRICTION;
        velocity.z *= FRICTION;
        spin *= FRICTION;
        if (velocity.length() < REST_SPEED) resting = true;
        return true;
    }

    /**
     * @param shrink 1 at full size, down to 0 while it fades away
     */
    Transformation transformation(float shrink) {
        Vector3f center = rotation.transform(new Vector3f(middle).mul(shrink));
        Vector3f translation = new Vector3f((float) position.x, (float) position.y, (float) position.z).sub(center);
        return new Transformation(translation, new Quaternionf(rotation), new Vector3f(scale).mul(shrink), new Quaternionf());
    }
}
