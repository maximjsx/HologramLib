package com.maximde.hologramlib.hologram;

import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.TaskHandle;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Moves a hologram along a smooth path through points, like an announcement flying around the spawn. The server
 * sends a position every few ticks and the client glides between them, so it looks smooth at any frame rate.
 */
public final class PathAnimation {

    // client glides this many ticks between positions
    private static final int STEP = 3;

    private final List<Location> positions;
    private final boolean loop;

    private PathAnimation(List<Location> positions, boolean loop) {
        if (positions.isEmpty()) throw new IllegalArgumentException("A path needs at least one point");
        this.positions = List.copyOf(positions);
        this.loop = loop;
    }

    /**
     * A curve through every point, bending smoothly instead of turning sharp corners.
     *
     * @param ticksPerPoint how long it takes from one point to the next
     * @param loop          whether it goes back from the last point to the first and starts over
     */
    public static PathAnimation through(List<Location> points, int ticksPerPoint, boolean loop) {
        if (points.size() < 2) return new PathAnimation(points, loop);
        int samples = Math.max(1, ticksPerPoint / STEP);
        int segments = loop ? points.size() : points.size() - 1;
        List<Location> positions = new ArrayList<>(segments * samples);
        for (int segment = 0; segment < segments; segment++) {
            Location before = point(points, segment - 1, loop);
            Location from = point(points, segment, loop);
            Location to = point(points, segment + 1, loop);
            Location after = point(points, segment + 2, loop);
            for (int sample = 0; sample < samples; sample++) {
                positions.add(catmullRom(before, from, to, after, (double) sample / samples));
            }
        }
        if (!loop) positions.add(points.get(points.size() - 1).clone());
        return new PathAnimation(positions, loop);
    }

    /**
     * Round and round a circle, once every {@code ticksPerTurn}.
     */
    public static PathAnimation circle(Location center, double radius, int ticksPerTurn) {
        int points = 16;
        List<Location> around = new ArrayList<>(points);
        for (int point = 0; point < points; point++) {
            double angle = 2 * Math.PI * point / points;
            around.add(center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        }
        return through(around, Math.max(STEP, ticksPerTurn / points), true);
    }

    /**
     * Starts moving {@code hologram}, which has to be spawned already. Cancel the returned task to stop it where it is.
     */
    public TaskHandle play(Hologram<?> hologram) {
        hologram.setTeleportDuration(STEP).update();
        AtomicInteger next = new AtomicInteger();
        AtomicReference<TaskHandle> task = new AtomicReference<>();
        task.set(BukkitTasks.runTaskTimer(() -> {
            int index = next.getAndIncrement();
            if (index >= positions.size()) {
                if (!loop) {
                    Optional.ofNullable(task.get()).ifPresent(TaskHandle::cancel);
                    return;
                }
                next.set(1);
                index = 0;
            }
            if (!hologram.isDead()) hologram.teleport(positions.get(index));
        }, 1L, STEP));
        return task.get();
    }

    private static Location point(List<Location> points, int index, boolean loop) {
        int size = points.size();
        return points.get(loop ? Math.floorMod(index, size) : Math.max(0, Math.min(size - 1, index)));
    }

    private static Location catmullRom(Location p0, Location p1, Location p2, Location p3, double t) {
        Vector a = p0.toVector();
        Vector b = p1.toVector();
        Vector c = p2.toVector();
        Vector d = p3.toVector();
        double t2 = t * t;
        double t3 = t2 * t;
        Vector point = b.clone().multiply(2)
                .add(c.clone().subtract(a).multiply(t))
                .add(a.clone().multiply(2).subtract(b.clone().multiply(5)).add(c.clone().multiply(4)).subtract(d).multiply(t2))
                .add(b.clone().multiply(3).subtract(a).subtract(c.clone().multiply(3)).add(d).multiply(t3))
                .multiply(0.5);
        Location location = p1.clone();
        location.setX(point.getX());
        location.setY(point.getY());
        location.setZ(point.getZ());
        return location;
    }
}
