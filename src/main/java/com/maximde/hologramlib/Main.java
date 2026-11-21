package com.maximde.hologramlib;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.maximde.hologramlib.command.HologramCommand;
import com.maximde.hologramlib.command.Messages;
import com.maximde.hologramlib.persistence.PersistentHolograms;
import com.maximde.hologramlib.persistence.importer.DecentHologramsImporter;
import com.maximde.hologramlib.persistence.importer.FancyHologramsImporter;
import com.maximde.hologramlib.persistence.importer.HologramImporter;
import com.maximde.hologramlib.persistence.importer.HolographicDisplaysImporter;
import com.maximde.hologramlib.utils.BukkitTasks;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.List;
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
        Path plugins = getDataFolder().toPath().getParent();
        List<HologramImporter> importers = List.of(
                new DecentHologramsImporter(plugins, getLogger()),
                new FancyHologramsImporter(plugins, getLogger()),
                new HolographicDisplaysImporter(plugins, getLogger()));
        Messages.load(this)
                .exceptionally(error -> {
                    getLogger().log(Level.WARNING, "Could not read messages.yml, using the default messages", error);
                    return Messages.defaults(this);
                })
                .thenAccept(messages -> BukkitTasks.runTask(() ->
                        registerCommand(new HologramCommand(persistentHolograms, messages, importers))));
    }

    private void registerCommand(HologramCommand command) {
        PluginCommand pluginCommand = getCommand("hologramlib");
        if (pluginCommand == null) return;
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
    }
}
