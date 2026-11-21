package com.maximde.hologramlib.hologram.effect;

import org.bukkit.Location;
import org.bukkit.SoundCategory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One sound of an effect, made of vanilla sounds played on top of each other. Every layer gets a slightly different
 * pitch each time, so a sound heard twice never sounds like a loop.
 */
public record SoundCue(List<Layer> layers) {

    public static final SoundCue SHATTER = of("block.glass.break 1.0 1.0", "block.amethyst_cluster.break 0.6 1.2");
    public static final SoundCue BOUNCE = of("block.wool.step 1.0 0.8", "entity.slime.squish_small 0.35 1.6",
            "entity.slime.jump_small 0.5 1.4");

    private static final double PITCH_VARIATION = 0.06;

    public record Layer(String sound, float volume, float pitch) {}

    public SoundCue {
        layers = List.copyOf(layers);
    }

    /**
     * @param layers {@code "sound volume pitch"} each, like {@code "block.wood.break 0.8 0.6"}
     */
    public static SoundCue of(String... layers) {
        return new SoundCue(Arrays.stream(layers).map(line -> {
            String[] parts = line.trim().split("\\s+");
            if (parts.length != 3) throw new IllegalArgumentException("Expected \"sound volume pitch\" but got " + line);
            return new Layer(parts[0], Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));
        }).toList());
    }

    public void play(Location location) {
        play(location, 1, 1);
    }

    /**
     * @param loudness scales every layer's volume, above 1 the sound carries further
     * @param pitch    scales every layer's pitch
     */
    public void play(Location location, float loudness, float pitch) {
        if (location.getWorld() == null) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Layer layer : layers) {
            float variation = (float) random.nextDouble(1 - PITCH_VARIATION, 1 + PITCH_VARIATION);
            float finalPitch = Math.max(0.5f, Math.min(2f, layer.pitch() * pitch * variation));
            location.getWorld().playSound(location, layer.sound(), SoundCategory.BLOCKS, layer.volume() * loudness, finalPitch);
        }
    }
}
