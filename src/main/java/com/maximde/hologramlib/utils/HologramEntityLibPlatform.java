package com.maximde.hologramlib.utils;

import me.tofaa.entitylib.APIConfig;
import me.tofaa.entitylib.spigot.SpigotEntityLibPlatform;
import org.bukkit.plugin.java.JavaPlugin;

public final class HologramEntityLibPlatform extends SpigotEntityLibPlatform {

    public HologramEntityLibPlatform(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public void setupApi(APIConfig config) {
        try {
            super.setupApi(config);
        } catch (NoSuchMethodError missingNextEntityId) {
            ServerEntityIdProvider provider = ServerEntityIdProvider.find().orElseThrow(() -> missingNextEntityId);
            setEntityIdProvider(provider);
            getAPI().onLoad();
            getAPI().onEnable();
        }
    }
}
