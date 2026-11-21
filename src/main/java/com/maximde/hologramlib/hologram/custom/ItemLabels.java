package com.maximde.hologramlib.hologram.custom;

import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.TextHologram;
import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.TaskHandle;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Floating name tags over dropped items, like "Diamond x3", seen only by players close by. The names come from the
 * client's own language. Label single items with {@link #label} or every drop with {@link #setLabelAllDrops}.
 * {@link #close()} removes every label and stops listening.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class ItemLabels implements Listener {

    private record Label(Item item, TextHologram hologram) {}

    private final HologramManager manager;
    private final Map<UUID, Label> labels = new ConcurrentHashMap<>();
    private final TaskHandle cleanup;

    /**
     * MiniMessage with {@code {name}} and {@code {amount}}, for stacks of more than one.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile String format = "<white>{name} <gray>x{amount}";

    /**
     * MiniMessage with {@code {name}}, for a single item.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile String singleFormat = "<white>{name}";

    @Getter @Setter @Accessors(chain = true)
    private volatile boolean labelAllDrops = false;

    @Getter @Setter @Accessors(chain = true)
    private volatile double viewDistance = 12;

    @Getter @Setter @Accessors(chain = true)
    private volatile float scale = 0.6f;

    public ItemLabels(HologramManager manager, Plugin plugin) {
        this.manager = manager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // items gone without an event, like lava or /kill
        this.cleanup = BukkitTasks.runTaskTimer(() -> labels.values().stream()
                .filter(label -> !label.item().isValid())
                .toList()
                .forEach(label -> unlabel(label.item())), 20L, 20L);
    }

    /**
     * Puts a label over {@code item}, or updates it. Call it on the item's thread.
     */
    public synchronized void label(Item item) {
        Label existing = labels.get(item.getUniqueId());
        if (existing != null) {
            existing.hologram().setMiniMessageText(text(item.getItemStack())).update();
            return;
        }
        TextHologram hologram = new TextHologram("item_label_" + item.getEntityId())
                .setMiniMessageText(text(item.getItemStack()))
                .setScale(scale, scale, scale)
                .setTranslation(0, 0.25f, 0)
                .setMaxPlayerRenderDistanceSquared(viewDistance * viewDistance);
        manager.spawn(hologram, item.getLocation(), false);
        labels.put(item.getUniqueId(), new Label(item, hologram));
        // client drops passengers it doesn't have yet
        BukkitTasks.runTaskLater(() -> {
            if (labels.containsKey(item.getUniqueId())) manager.attach(hologram, item.getEntityId());
        }, 3L);
    }

    public void unlabel(Item item) {
        Label label = labels.remove(item.getUniqueId());
        if (label != null) manager.remove(label.hologram());
    }

    public void close() {
        HandlerList.unregisterAll(this);
        cleanup.cancel();
        labels.values().forEach(label -> manager.remove(label.hologram()));
        labels.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        if (labelAllDrops) label(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        unlabel(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        unlabel(event.getEntity());
        relabelLater(event.getTarget());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        relabelLater(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        relabelLater(event.getItem());
    }

    // stack amount only changes after the event
    private void relabelLater(Item item) {
        if (!labels.containsKey(item.getUniqueId())) return;
        BukkitTasks.runTaskLater(() -> BukkitTasks.runTask(() -> {
            if (item.isValid()) label(item);
            else unlabel(item);
        }, item), 1L);
    }

    private String text(ItemStack stack) {
        String name = name(stack);
        return stack.getAmount() > 1
                ? format.replace("{name}", name).replace("{amount}", String.valueOf(stack.getAmount()))
                : singleFormat.replace("{name}", name);
    }

    @SuppressWarnings("deprecation")
    private static String name(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return MiniMessage.miniMessage().serialize(LegacyComponentSerializer.legacySection().deserialize(meta.getDisplayName()));
        }
        String kind = stack.getType().isBlock() ? "block" : "item";
        return "<lang:" + kind + ".minecraft." + stack.getType().getKey().getKey() + ">";
    }
}
