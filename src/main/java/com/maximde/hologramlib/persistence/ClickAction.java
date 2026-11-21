package com.maximde.hologramlib.persistence;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.maximde.hologramlib.HologramLib;
import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.Enums;
import com.maximde.hologramlib.utils.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public record ClickAction(Type type, String data) {

    public enum Type { MESSAGE, COMMAND, CONSOLE, SOUND, TELEPORT, CONNECT, PERMISSION }

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final String PROXY_CHANNEL = "BungeeCord";

    public static Optional<ClickAction> parse(String action) {
        int colon = action.indexOf(':');
        if (colon < 0) return Optional.empty();
        String data = action.substring(colon + 1).trim();
        return Enums.parse(Type.class, action.substring(0, colon).trim()).map(type -> new ClickAction(type, data));
    }

    public static void runAll(List<ClickAction> actions, Player player) {
        for (ClickAction action : actions) {
            if (!action.run(player)) return;
        }
    }

    public boolean run(Player player) {
        String filled = data.replace("{player}", player.getName());
        if (type == Type.PERMISSION) return player.hasPermission(filled);
        switch (type) {
            case MESSAGE -> player.sendMessage(MiniMessage.toLegacy(filled));
            case COMMAND -> player.performCommand(stripSlash(filled));
            case CONSOLE -> BukkitTasks.runTask(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(filled)));
            case SOUND -> playSound(player, filled);
            case TELEPORT -> teleport(player, filled);
            case CONNECT -> connect(player, filled);
        }
        return true;
    }

    @Override
    public String toString() {
        return type + ":" + data;
    }

    // NAME[:volume[:pitch]], the name itself can contain colons like minecraft:block.note_block.pling
    private static void playSound(Player player, String sound) {
        Deque<String> parts = new ArrayDeque<>(List.of(sound.split(":")));
        List<Float> numbers = new ArrayList<>();
        while (numbers.size() < 2 && parts.size() > 1 && NUMBER.matcher(parts.peekLast()).matches()) {
            numbers.add(0, Float.parseFloat(parts.removeLast()));
        }
        float volume = numbers.isEmpty() ? 1f : numbers.get(0);
        float pitch = numbers.size() < 2 ? 1f : numbers.get(1);
        player.playSound(player.getLocation(), soundKey(String.join(":", parts)), volume, pitch);
    }

    private static String soundKey(String sound) {
        if (sound.contains(".") || sound.contains(":")) return sound.toLowerCase(Locale.ROOT);
        return SoundNames.KEYS.getOrDefault(sound.toUpperCase(Locale.ROOT), sound.toLowerCase(Locale.ROOT));
    }

    // world:x:y:z[:yaw:pitch]
    private static void teleport(Player player, String target) {
        String[] parts = target.split(":");
        if (parts.length < 4 || !allNumbers(parts, 1)) return;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return;
        float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : player.getLocation().getYaw();
        float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : player.getLocation().getPitch();
        Location location = new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]), yaw, pitch);
        BukkitTasks.teleport(player, location);
    }

    private static boolean allNumbers(String[] parts, int from) {
        for (int i = from; i < parts.length; i++) {
            if (!NUMBER.matcher(parts[i]).matches()) return false;
        }
        return true;
    }

    private static void connect(Player player, String server) {
        JavaPlugin plugin = HologramLib.getPlugin();
        Messenger messenger = Bukkit.getMessenger();
        if (!messenger.isOutgoingChannelRegistered(plugin, PROXY_CHANNEL)) {
            messenger.registerOutgoingPluginChannel(plugin, PROXY_CHANNEL);
        }
        ByteArrayDataOutput message = ByteStreams.newDataOutput();
        message.writeUTF("Connect");
        message.writeUTF(server);
        player.sendPluginMessage(plugin, PROXY_CHANNEL, message.toByteArray());
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
