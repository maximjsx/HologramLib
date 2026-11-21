package com.maximde.hologramlib.persistence;

import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.Enums;
import com.maximde.hologramlib.utils.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record ClickAction(Type type, String data) {

    public enum Type { MESSAGE, COMMAND, CONSOLE, SOUND }

    public static Optional<ClickAction> parse(String action) {
        int colon = action.indexOf(':');
        if (colon < 0) return Optional.empty();
        String data = action.substring(colon + 1).trim();
        return Enums.parse(Type.class, action.substring(0, colon).trim()).map(type -> new ClickAction(type, data));
    }

    public void run(Player player) {
        String filled = data.replace("{player}", player.getName());
        switch (type) {
            case MESSAGE -> player.sendMessage(MiniMessage.toLegacy(filled));
            case COMMAND -> player.performCommand(stripSlash(filled));
            case CONSOLE -> BukkitTasks.runTask(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(filled)));
            case SOUND -> player.playSound(player.getLocation(), soundKey(filled), 1f, 1f);
        }
    }

    @Override
    public String toString() {
        return type + ":" + data;
    }

    private static String soundKey(String sound) {
        if (sound.contains(".") || sound.contains(":")) return sound.toLowerCase(Locale.ROOT);
        return SoundNames.KEYS.getOrDefault(sound.toUpperCase(Locale.ROOT), sound.toLowerCase(Locale.ROOT));
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private static final class SoundNames {

        private static final Map<String, String> KEYS = read();

        private static Map<String, String> read() {
            Map<String, String> keys = new HashMap<>();
            for (Keyed sound : Registry.SOUNDS) {
                NamespacedKey key = sound.getKey();
                keys.put(key.getKey().replace('.', '_').toUpperCase(Locale.ROOT), key.toString());
            }
            return Map.copyOf(keys);
        }
    }
}
