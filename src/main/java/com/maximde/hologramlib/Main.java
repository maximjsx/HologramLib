package com.maximde.hologramlib;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.maximde.hologramlib.command.HologramCommand;
import com.maximde.hologramlib.command.Messages;
import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.persistence.HologramStore;
import com.maximde.hologramlib.persistence.PersistentHolograms;
import com.maximde.hologramlib.utils.BukkitTasks;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.logging.Level;

public class Main extends JavaPlugin {

    private PersistentHolograms persistentHolograms;

    @Override
    public void onLoad() {
        HologramLib.onLoad(this);
    }

    @Override
    public void onEnable() {
        HologramLib.init();
        HologramLib.getManager().ifPresent(this::enablePersistence);
    }

    @Override
    public void onDisable() {
        if (persistentHolograms != null) persistentHolograms.shutdown();
        HologramLib.onDisable();
        Optional.of(PacketEvents.getAPI()).ifPresent(PacketEventsAPI::terminate);
    }

    // only the standalone plugin saves holograms, plugins shading the lib handle their own
    private void enablePersistence(HologramManager manager) {
        HologramStore store = new HologramStore(getDataFolder().toPath().resolve("holograms.yml"), getLogger());
        persistentHolograms = new PersistentHolograms(manager, store, getLogger());
        getServer().getPluginManager().registerEvents(persistentHolograms, this);
        persistentHolograms.load();

        Messages.load(this)
                .exceptionally(error -> {
                    getLogger().log(Level.WARNING, "Could not read messages.yml, using the default messages", error);
                    return Messages.defaults(this);
                })
                .thenAccept(messages -> BukkitTasks.runTask(() -> registerCommand(new HologramCommand(persistentHolograms, messages))));
    }

    private void registerCommand(HologramCommand command) {
        PluginCommand pluginCommand = getCommand("hologramlib");
        if (pluginCommand == null) return;
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
    }
}
