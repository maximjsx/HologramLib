package com.maximde.hologramlib;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.maximde.hologramlib.bstats.Metrics;
import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.PassengerManager;
import com.maximde.hologramlib.hook.BedrockPlayerHeadFilter;
import com.maximde.hologramlib.hook.HeadDatabaseHook;
import com.maximde.hologramlib.hook.PlaceholderAPIHook;
import com.maximde.hologramlib.listener.InteractionPacketListener;
import com.maximde.hologramlib.listener.PerPlayerTextListener;
import com.maximde.hologramlib.listener.PlayerJoinListener;
import com.maximde.hologramlib.listener.PlayerQuitListener;
import com.maximde.hologramlib.listener.PlayerSyncListener;
import com.maximde.hologramlib.persistence.HologramStore;
import com.maximde.hologramlib.persistence.PersistentHolograms;
import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.ItemsAdderHolder;
import com.maximde.hologramlib.utils.ReplaceText;
import com.tcoded.folialib.FoliaLib;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import me.tofaa.entitylib.APIConfig;
import me.tofaa.entitylib.EntityLib;
import com.maximde.hologramlib.utils.HologramEntityLibPlatform;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.logging.Level;


public abstract class HologramLib {

    @Getter
    private static ReplaceText replaceText;

    @Getter
    private static PlayerManager playerManager;

    private static HologramManager hologramManager;

    private static PersistentHolograms persistentHolograms;

    private static JavaPlugin plugin;

    private static boolean initialized = false;
    private static boolean loading = false;


    public static Optional<HologramManager> getManager() {
        init();
        return Optional.ofNullable(hologramManager)
                .or(() -> {
                    Bukkit.getLogger().log(Level.WARNING,
                            "HologramLib#getManager() couldn't provide a valid instance! " +
                                    "The plugin was not fully initialized yet.");
                    return Optional.empty();
                });
    }

    /**
     * Saves the holograms put into {@link PersistentHolograms} to holograms.yml in your plugins folder and
     * spawns them again on every start. Call it in onEnable() plugins that depend on the HologramLib plugin don't need to only if you shade this.
     */
    public static Optional<PersistentHolograms> enablePersistence() {
        if (persistentHolograms != null) return Optional.of(persistentHolograms);
        return getManager().map(manager -> {
            HologramStore store = new HologramStore(plugin.getDataFolder().toPath().resolve("holograms.yml"), plugin.getLogger());
            persistentHolograms = new PersistentHolograms(manager, store, plugin.getLogger());
            plugin.getServer().getPluginManager().registerEvents(persistentHolograms, plugin);
            persistentHolograms.load();
            return persistentHolograms;
        });
    }

    public static Optional<PersistentHolograms> getPersistentHolograms() {
        return Optional.ofNullable(persistentHolograms);
    }

    public static void onLoad(JavaPlugin javaPlugin) {
        if (plugin != null) return;
        plugin = javaPlugin;
        Optional.ofNullable(SpigotPacketEventsBuilder.build(plugin))
                .ifPresentOrElse(
                        PacketEvents::setAPI,
                        () -> plugin.getLogger().severe("Failed to build PacketEvents API")
                );
        PacketEvents.getAPI().load();
    }

    /**
     * @deprecated HologramLib no longer registers a command, {@code registerCommand} is ignored. Use {@link #init()}.
     */
    @Deprecated
    public static void init(boolean registerCommand) {
        init();
    }

    public static void init() {
        if(loading || initialized) return;

        if(plugin == null) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "Failed to init HologramLib! HologramLib#onLoad(JavaPlugin) was not called in onLoad() main class.");
            Bukkit.getLogger().log(Level.SEVERE,
                    "If you are not shading HologramLib, add depends: HologramLib to your plugin.yml");
            return;
        }

        if(!plugin.isEnabled()) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "Failed to init HologramLib! The plugin instance which is used by HologramLib has not been initialized (" + plugin.getName() + ") yet!");

            Bukkit.getLogger().log(Level.SEVERE,
                    "If you are not shading HologramLib, add depends: HologramLib to your plugin.yml");
            return;
        }

        loading = true;

        try {
            initializePacketEvents();
            initializeEntityLib();
            initializeManagers();
            initializeMetrics();
            initializeReplaceText();

            FoliaLib foliaLib = new FoliaLib(plugin);
            BukkitTasks.setPlugin(plugin);
            BukkitTasks.setFoliaLib(foliaLib);

            hologramManager = new HologramManager();
            PacketEvents.getAPI().getEventManager().registerListener(new InteractionPacketListener(hologramManager),
                    PacketListenerPriority.LOW);
            PacketEvents.getAPI().getEventManager().registerListener(new PerPlayerTextListener(hologramManager),
                    PacketListenerPriority.NORMAL);

            plugin.getServer().getPluginManager().registerEvents(new PlayerJoinListener(hologramManager), plugin);
            plugin.getServer().getPluginManager().registerEvents(new PlayerQuitListener(hologramManager), plugin);
            plugin.getServer().getPluginManager().registerEvents(new PlayerSyncListener(hologramManager), plugin);


            PluginManager pluginManager = Bukkit.getPluginManager();

            Plugin placeholderAPIPlugin = pluginManager.getPlugin("PlaceholderAPI");
            if (placeholderAPIPlugin != null && placeholderAPIPlugin.isEnabled()) {
                plugin.getLogger().log(Level.INFO, "PlaceholderAPI found! Initializing hook...");
                new PlaceholderAPIHook(PacketEvents.getAPI());
            } else {
                plugin.getLogger().log(Level.INFO, "PlaceholderAPI not found or not enabled. PlaceholderAPI support will be disabled.");
            }

            plugin.getLogger().log(Level.INFO, "Initializing Bedrock player head filter...");
            new BedrockPlayerHeadFilter(PacketEvents.getAPI());

            Plugin headDatabasePlugin = pluginManager.getPlugin("HeadDatabase");
            if (headDatabasePlugin != null && headDatabasePlugin.isEnabled()) {
                plugin.getLogger().log(Level.INFO, "HeadDatabase found! Initializing hook...");
                HeadDatabaseHook.init();
            }

            initialized = true;
            plugin.getLogger().log(Level.INFO, "Successfully initialized!");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to enable HologramLib", e);
        } finally {
            loading = false;
        }
    }

    public static void onDisable() {
        if (persistentHolograms != null) persistentHolograms.shutdown();
        if (hologramManager == null) return;
        try {
            hologramManager.removeAll();
            hologramManager.removeAllInteractionBoxes();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove holograms on disable", e);
        }
    }

    private static void initializePacketEvents() {
        PacketEvents.getAPI().init();
    }

    private static void initializeEntityLib() {
        HologramEntityLibPlatform platform = new HologramEntityLibPlatform(plugin);
        APIConfig config = new APIConfig(PacketEvents.getAPI())
                .usePlatformLogger();
        EntityLib.init(platform, config);
    }

    private static void initializeManagers() {
        PacketEventsAPI<?> packetEventsAPI = PacketEvents.getAPI();
        playerManager = packetEventsAPI.getPlayerManager();
        new PassengerManager(packetEventsAPI);
    }

    private static void initializeMetrics() {
        new Metrics(plugin, 19375);
    }

    private static void initializeReplaceText() {
        replaceText = createReplaceTextInstance()
                .orElse(text -> text);
    }

    private static Optional<ReplaceText> createReplaceTextInstance() {
        try {
            return Optional.of(new ItemsAdderHolder());
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.INFO,"Using default text replacement. (ItemsAdder is not installed)");
            return Optional.empty();
        }
    }

    public static JavaPlugin getPlugin() {
        if (plugin == null) {
            throw new IllegalStateException("Tried to access the plugin instance but HologramLib has not been initialized");
        }
        return plugin;
    }
}