package com.maximde.hologramlib.persistence;

import com.maximde.hologramlib.utils.Enums;
import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequiredArgsConstructor
public final class HologramStore {

    private static final long IO_TIMEOUT_SECONDS = 10;

    private final Path file;
    private final Logger logger;
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "HologramLib Store");
        thread.setDaemon(true);
        return thread;
    });

    public CompletableFuture<List<HologramData>> load() {
        return CompletableFuture.supplyAsync(this::readFile, io)
                .orTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public CompletableFuture<Void> save(Collection<HologramData> holograms) {
        String yaml = toYaml(holograms);
        return CompletableFuture.runAsync(() -> writeFile(yaml), io)
                .orTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> {
                    if (error != null) logger.log(Level.SEVERE, "Could not save " + file.getFileName(), error);
                });
    }

    public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.severe("Saving " + file.getFileName() + " did not finish before shutdown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<HologramData> readFile() {
        if (!Files.exists(file)) return List.of();
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
            return fromYaml(yaml);
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
    }

    // .tmp first and a .bak of the old file, so a crash or full disk mid write can't wipe the holograms
    private void writeFile(String yaml) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, yaml, StandardCharsets.UTF_8);
            if (Files.exists(file)) {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + file, e);
        }
    }

    private static String toYaml(Collection<HologramData> holograms) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (HologramData data : holograms) {
            ConfigurationSection section = yaml.createSection("holograms." + data.id());
            section.set("type", data.type().name());
            section.set("location.world", data.world());
            section.set("location.x", data.x());
            section.set("location.y", data.y());
            section.set("location.z", data.z());
            section.set("location.yaw", data.yaw());
            section.set("content", data.content());
            section.set("scale", data.scale());
            section.set("billboard", data.billboard().name());
            if (data.backgroundColor() != null) section.set("background", ArgbColor.format(data.backgroundColor()));
            if (data.shadow()) section.set("shadow", true);
            if (data.placeholders()) section.set("placeholders", true);
            if (data.viewPermission() != null) section.set("permission", data.viewPermission());
            if (!data.actions().isEmpty()) section.set("actions", data.actions().stream().map(ClickAction::toString).toList());
        }
        return yaml.saveToString();
    }

    private List<HologramData> fromYaml(YamlConfiguration yaml) {
        ConfigurationSection holograms = yaml.getConfigurationSection("holograms");
        if (holograms == null) return List.of();

        List<HologramData> loaded = new ArrayList<>();
        for (String id : holograms.getKeys(false)) {
            ConfigurationSection section = holograms.getConfigurationSection(id);
            if (section == null) continue;
            read(id, section).ifPresentOrElse(loaded::add, () ->
                    logger.warning("Skipping the saved hologram " + id + ", its type " + section.getString("type") + " is unknown"));
        }
        return loaded;
    }

    private Optional<HologramData> read(String id, ConfigurationSection section) {
        return Enums.parse(HologramData.Type.class, section.getString("type", "TEXT")).map(type -> new HologramData(
                id,
                type,
                section.getString("location.world", "world"),
                section.getDouble("location.x"),
                section.getDouble("location.y"),
                section.getDouble("location.z"),
                (float) section.getDouble("location.yaw"),
                section.getString("content", ""),
                (float) section.getDouble("scale", 1),
                Enums.parse(Display.Billboard.class, section.getString("billboard", "")).orElse(Display.Billboard.CENTER),
                Optional.ofNullable(section.getString("background")).flatMap(ArgbColor::parse).orElse(null),
                section.getBoolean("shadow"),
                section.getBoolean("placeholders"),
                section.getString("permission"),
                actions(id, section.getStringList("actions"))));
    }

    private List<ClickAction> actions(String id, List<String> written) {
        List<ClickAction> actions = new ArrayList<>();
        for (String action : written) {
            ClickAction.parse(action).ifPresentOrElse(actions::add, () ->
                    logger.warning("Ignoring the unknown action " + action + " of the saved hologram " + id));
        }
        return actions;
    }
}
