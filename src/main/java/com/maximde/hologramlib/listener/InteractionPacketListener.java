package com.maximde.hologramlib.listener;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.maximde.hologramlib.event.HologramInteractEvent;
import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.InteractionBox;
import com.maximde.hologramlib.utils.BukkitTasks;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class InteractionPacketListener implements PacketListener {

    private final HologramManager hologramManager;
    private final ConcurrentHashMap<UUID, Long> lastProcessedInteraction = new ConcurrentHashMap<>();

    private static final String ATTACK = "ATTACK";

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        int entityId;
        boolean leftClick;
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            entityId = interact.getEntityId();
            leftClick = interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
        } else if (ATTACK.equals(event.getPacketType().getName())) {
            entityId = new WrapperPlayClientAttack(event).getEntityId();
            leftClick = true;
        } else {
            return;
        }
        Optional<InteractionBox> interactionBox = hologramManager.getInteractionBoxByEntityId(entityId);

        if (interactionBox.isEmpty()) return;

        UUID playerUUID = event.getUser().getUUID();
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) return;

        BukkitTasks.runTask(() -> {
            if (player.isOnline()) {
                Location boxLocation = interactionBox.get().getLocation();

                if (boxLocation == null || boxLocation.getWorld() == null) return;

                if (!player.getWorld().equals(boxLocation.getWorld())) return;

                double maxDistanceSquared = 6.0 * 6.0;
                if (player.getLocation().distanceSquared(boxLocation) > maxDistanceSquared) return;

                long now = System.currentTimeMillis();
                if (lastProcessedInteraction.containsKey(playerUUID) && (now - lastProcessedInteraction.get(playerUUID) < 100)) {
                    return;
                }
                lastProcessedInteraction.put(playerUUID, now);

                HologramInteractEvent interactEvent = new HologramInteractEvent(player, interactionBox.get(),
                        hologramManager.getHologramOf(interactionBox.get()).orElse(null), leftClick);
                Bukkit.getPluginManager().callEvent(interactEvent);
                if (interactEvent.isCancelled()) return;

                interactionBox.get().triggerInteraction((player));
            }
        }, player);
    }
}