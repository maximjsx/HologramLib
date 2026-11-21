package com.maximde.hologramlib.persistence;

import lombok.With;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @param content MiniMessage text, item key like {@code diamond} or block state like {@code oak_stairs[facing=east]}
 * @param backgroundColor ARGB, null for the client's default
 * @param viewPermission null shows it to everyone
 */
@With
public record HologramData(
        String id,
        Type type,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        String content,
        float scale,
        Display.Billboard billboard,
        @Nullable Integer backgroundColor,
        boolean shadow,
        boolean placeholders,
        @Nullable String viewPermission,
        List<ClickAction> actions
) {

    public enum Type { TEXT, ITEM, BLOCK }

    public HologramData {
        actions = List.copyOf(actions);
    }

    public static HologramData create(String id, Type type, Location location, String content) {
        return new HologramData(id, type, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), content, 1f, defaultBillboard(type), null, true, false, null, List.of());
    }

    public HologramData withLocation(Location location) {
        return withWorld(location.getWorld().getName()).withX(location.getX()).withY(location.getY())
                .withZ(location.getZ()).withYaw(location.getYaw());
    }

    private static Display.Billboard defaultBillboard(Type type) {
        return type == Type.TEXT ? Display.Billboard.CENTER : Display.Billboard.FIXED;
    }
}
