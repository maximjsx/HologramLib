package com.maximde.hologramlib.hologram.custom;

import com.github.retrooper.packetevents.protocol.particle.Particle;
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;
import com.maximde.hologramlib.HologramLib;
import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.TaskHandle;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Text or a picture drawn with colored dust particles, sent as packets to the players near it. Faces the yaw of its
 * location like a sign. Every pixel is one particle, so keep pictures small: a few hundred pixels look good and stay
 * cheap.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class ParticleHologram {

    private record Dot(float x, float y, Particle<ParticleDustData> particle) {}

    private final List<Dot> dots;

    /**
     * Blocks between two neighbouring pixels.
     */
    @Getter @Setter @Accessors(chain = true)
    private float pixelSize = 0.06f;

    /**
     * Dust particles fade after about a second, they are sent again every this many ticks.
     */
    @Getter @Setter @Accessors(chain = true)
    private int refreshTicks = 5;

    @Getter @Setter @Accessors(chain = true)
    private double viewDistance = 32;

    private @Nullable TaskHandle task;

    private ParticleHologram(List<Dot> dots) {
        this.dots = List.copyOf(dots);
    }

    /**
     * Every pixel that is not see-through becomes a particle in its own color.
     */
    public static ParticleHologram image(BufferedImage image) {
        List<Dot> dots = new ArrayList<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) < 128) continue;
                dots.add(new Dot(x - image.getWidth() / 2f, image.getHeight() - 1 - y,
                        new Particle<>(ParticleTypes.DUST, new ParticleDustData(1, (argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff))));
            }
        }
        return new ParticleHologram(dots);
    }

    /**
     * {@code text} in a bold font {@code fontSize} pixels high, in {@code color}.
     */
    public static ParticleHologram text(String text, int fontSize, Color color) {
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measuring = measure.createGraphics();
        int width = measuring.getFontMetrics(font).stringWidth(text);
        int height = measuring.getFontMetrics(font).getHeight();
        measuring.dispose();

        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        // no smoothing, pixels are fully on or off
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setFont(font);
        graphics.setColor(color);
        graphics.drawString(text, 0, graphics.getFontMetrics().getAscent());
        graphics.dispose();
        return image(image);
    }

    public int getParticleCount() {
        return dots.size();
    }

    public ParticleHologram spawn(Location location) {
        remove();
        Location facing = location.clone();
        double yaw = Math.toRadians(facing.getYaw());
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        List<WrapperPlayServerParticle> packets = dots.stream().map(dot -> new WrapperPlayServerParticle(dot.particle(),
                true,
                new Vector3d(facing.getX() + rightX * dot.x() * pixelSize, facing.getY() + dot.y() * pixelSize,
                        facing.getZ() + rightZ * dot.x() * pixelSize),
                new Vector3f(0, 0, 0), 0, 1)).toList();
        double maxDistanceSquared = viewDistance * viewDistance;
        task = BukkitTasks.runTaskTimer(() -> {
            for (Player player : facing.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(facing) > maxDistanceSquared) continue;
                packets.forEach(packet -> HologramLib.getPlayerManager().sendPacket(player, packet));
            }
        }, 1L, Math.max(1, refreshTicks));
        return this;
    }

    public void remove() {
        if (task != null) task.cancel();
        task = null;
    }
}
