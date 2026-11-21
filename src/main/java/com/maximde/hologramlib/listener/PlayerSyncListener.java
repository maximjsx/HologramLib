package com.maximde.hologramlib.listener;

import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.utils.BukkitTasks;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * A world change makes the client forget every entity, while the server still counts the player as a viewer of
 * holograms it shows in every world, like those with a fixed viewer list, and never sends them again. This spawns
 * them for the player once more. A respawn in the same world keeps the client's entities, so it needs nothing.
 */
@RequiredArgsConstructor
public class PlayerSyncListener implements Listener {

    private final HologramManager hologramManager;

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        BukkitTasks.runTaskLater(() -> resync(event.getPlayer()), 1L);
    }

    private void resync(Player player) {
        if (!player.isOnline()) return;

        // only the new world, others would show up at the same coords
        hologramManager.getHolograms().forEach(hologram -> {
            if (!inWorldOf(player, hologram.getLocation())) return;
            if (!hologram.hasViewer(player.getUniqueId())) return;
            hologram.removeViewer(player);
            hologram.addViewer(player);
        });

        hologramManager.getInteractionBoxes().forEach(box -> {
            if (!inWorldOf(player, box.getLocation())) return;
            if (!box.hasViewer(player.getUniqueId())) return;
            box.removeViewer(player);
            box.addViewer(player);
        });
    }

    private static boolean inWorldOf(Player player, Location location) {
        return location != null && player.getWorld().equals(location.getWorld());
    }
}
