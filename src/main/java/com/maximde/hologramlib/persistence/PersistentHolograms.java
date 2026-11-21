package com.maximde.hologramlib.persistence;

import com.maximde.hologramlib.hologram.BlockHologram;
import com.maximde.hologramlib.hologram.Hologram;
import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.ItemHologram;
import com.maximde.hologramlib.hologram.TextHologram;
import com.maximde.hologramlib.utils.BukkitTasks;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

@RequiredArgsConstructor
public final class PersistentHolograms implements Listener {

    private final HologramManager manager;
    private final HologramStore store;
    private final Logger logger;
    private final Map<String, HologramData> saved = new ConcurrentSkipListMap<>();
    private final Map<String, Hologram<?>> spawned = new ConcurrentHashMap<>();

    public void load() {
        store.load()
                .thenAccept(loaded -> BukkitTasks.runTask(() -> show(loaded)))
                .exceptionally(error -> {
                    logger.log(Level.SEVERE, "Could not load the saved holograms", error);
                    return null;
                });
    }

    public Collection<HologramData> all() {
        return Collections.unmodifiableCollection(saved.values());
    }

    public Optional<HologramData> get(String id) {
        return Optional.ofNullable(saved.get(id));
    }

    public boolean exists(String id) {
        return saved.containsKey(id);
    }

    /**
     * Empty while its world isn't loaded.
     */
    public Optional<Hologram<?>> getSpawned(String id) {
        return Optional.ofNullable(spawned.get(id));
    }

    /**
     * Saves the hologram and respawns it. Returns false if the item or block doesn't exist nothing is saved then.
     */
    public synchronized boolean put(HologramData data) {
        if (!HologramContent.isValid(data)) return false;
        despawn(data.id());
        saved.put(data.id(), data);
        spawn(data);
        store.save(saved.values());
        return true;
    }

    public synchronized boolean delete(String id) {
        if (saved.remove(id) == null) return false;
        despawn(id);
        store.save(saved.values());
        return true;
    }

    public synchronized void shutdown() {
        store.save(saved.values());
        store.close();
        List.copyOf(spawned.keySet()).forEach(this::despawn);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        savedIn(event.getWorld())
                .filter(data -> !spawned.containsKey(data.id()))
                .forEach(this::spawn);
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        savedIn(event.getWorld()).forEach(data -> despawn(data.id()));
    }

    private Stream<HologramData> savedIn(World world) {
        return saved.values().stream().filter(data -> data.world().equals(world.getName()));
    }

    private void show(List<HologramData> loaded) {
        loaded.forEach(data -> saved.put(data.id(), data));
        loaded.forEach(this::spawn);
        logger.info("Loaded " + loaded.size() + " saved holograms");
    }

    private void spawn(HologramData data) {
        World world = Bukkit.getWorld(data.world());
        if (world == null) return;
        if (manager.getHologram(data.id()).isPresent()) {
            logger.warning("The saved hologram " + data.id() + " was not spawned, a plugin already uses that id");
            return;
        }
        Location location = new Location(world, data.x(), data.y(), data.z(), data.yaw(), 0);
        spawnAt(data, location).ifPresentOrElse(hologram -> spawned.put(data.id(), hologram), () ->
                logger.warning("The saved hologram " + data.id() + " shows no known item or block: " + data.content()));
    }

    private void despawn(String id) {
        Hologram<?> hologram = spawned.remove(id);
        if (hologram != null) manager.remove(hologram);
    }

    private Optional<Hologram<?>> spawnAt(HologramData data, Location location) {
        return switch (data.type()) {
            case TEXT -> Optional.of(spawnConfigured(textHologram(data), data, location));
            case ITEM -> HologramContent.item(data.content())
                    .map(item -> spawnConfigured(new ItemHologram(data.id()).setItem(item), data, location));
            case BLOCK -> HologramContent.block(data.content())
                    .map(block -> spawnConfigured(new BlockHologram(data.id()).setBlock(block), data, location));
        };
    }

    private <H extends Hologram<H>> H spawnConfigured(H hologram, HologramData data, Location location) {
        hologram.setScale(data.scale(), data.scale(), data.scale())
                .setBillboard(data.billboard())
                .setViewPermission(data.viewPermission());
        if (!data.actions().isEmpty()) hologram.onInteract(player -> data.actions().forEach(action -> action.run(player)));
        Bukkit.getPluginManager().callEvent(new PersistentHologramSpawnEvent(data, hologram));
        return manager.spawn(hologram, location, false);
    }

    private static TextHologram textHologram(HologramData data) {
        return new TextHologram(data.id())
                .setMiniMessageText(data.content())
                .setPlaceholderApiEnabled(data.placeholders())
                .setShadow(data.shadow())
                .setBackgroundColor(data.backgroundColor() == null ? 0 : data.backgroundColor());
    }
}
