package com.maximde.hologramlib.hook;

import com.github.retrooper.packetevents.PacketEventsAPI;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * Turns on PlaceholderAPI placeholders in text holograms. The texts themselves are sent by
 * {@link com.maximde.hologramlib.listener.PerPlayerTextListener}.
 */
public class PlaceholderAPIHook {

    private static volatile boolean enabled;

    /**
     * @param packetEventsAPI no longer used, kept so the constructor stays compatible
     */
    public PlaceholderAPIHook(PacketEventsAPI<?> packetEventsAPI) {
        enabled = true;
    }

    /**
     * Whether PlaceholderAPI is there, so placeholders in hologram texts get filled in.
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Only call when {@link #isEnabled()}, PlaceholderAPI's classes are missing otherwise.
     */
    public static String setPlaceholders(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
