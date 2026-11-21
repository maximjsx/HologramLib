package com.maximde.hologramlib.command;

import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.MiniMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Messages {

    private static final String FILE_NAME = "messages.yml";
    private static final long LOAD_TIMEOUT_SECONDS = 10;

    private final Configuration texts;

    public static CompletableFuture<Messages> load(JavaPlugin plugin) {
        return CompletableFuture.supplyAsync(() -> read(plugin), BukkitTasks::runTaskAsync)
                .orTimeout(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public static Messages defaults(JavaPlugin plugin) {
        return new Messages(bundled(plugin));
    }

    public void send(CommandSender sender, String key, TagResolver... placeholders) {
        sender.sendMessage(MiniMessage.toLegacy(text("prefix") + text(key), placeholders));
    }

    private String text(String key) {
        return Objects.requireNonNullElse(texts.getString(key), key);
    }

    private static Messages read(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) plugin.saveResource(FILE_NAME, false);
        YamlConfiguration texts = YamlConfiguration.loadConfiguration(file);
        texts.setDefaults(bundled(plugin));
        return new Messages(texts);
    }

    private static YamlConfiguration bundled(JavaPlugin plugin) {
        InputStream resource = Objects.requireNonNull(plugin.getResource(FILE_NAME), FILE_NAME + " is missing from the jar");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
    }
}
