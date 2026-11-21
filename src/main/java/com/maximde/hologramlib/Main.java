package com.maximde.hologramlib;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.maximde.hologramlib.command.HologramCommand;
import com.maximde.hologramlib.command.Messages;
import com.maximde.hologramlib.persistence.DecentHologramsImporter;
import com.maximde.hologramlib.persistence.PersistentHolograms;
import com.maximde.hologramlib.utils.BukkitTasks;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;

public class Main extends JavaPlugin {

    @Override
    public void onLoad() {
        HologramLib.onLoad(this);
    }

    @Override
    public void onEnable() {
        HologramLib.init();
        HologramLib.enablePersistence().ifPresent(this::enableCommand);
    }

    @Override
    public void onDisable() {
        HologramLib.onDisable();
        Optional.of(PacketEvents.getAPI()).ifPresent(PacketEventsAPI::terminate);
    }

    private void enableCommand(PersistentHolograms persistentHolograms) {
        Path decentHolograms = getDataFolder().toPath().resolveSibling("DecentHolograms").resolve("holograms");
        DecentHologramsImporter importer = new DecentHologramsImporter(decentHolograms, getLogger());
        Messages.load(this)
                .exceptionally(error -> {
                    getLogger().log(Level.WARNING, "Could not read messages.yml, using the default messages", error);
                    return Messages.defaults(this);
                })
                .thenAccept(messages -> BukkitTasks.runTask(() ->
                        registerCommand(new HologramCommand(persistentHolograms, messages, importer))));
    }

    private void registerCommand(HologramCommand command) {
        PluginCommand pluginCommand = getCommand("hologramlib");
        if (pluginCommand == null) return;
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
    }
}
