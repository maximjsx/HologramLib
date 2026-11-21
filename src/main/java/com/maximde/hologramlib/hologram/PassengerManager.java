package com.maximde.hologramlib.hologram;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps holograms mounted on the entities they are attached to. Every passenger packet that goes out, from the server
 * or from HologramLib, is completed to the server's own riders plus every attached hologram, so attaching a second
 * hologram or a player getting into a boat no longer drops the others. A vehicle that is spawned again for a client,
 * after leaving its view or dying, gets its holograms back right away.
 */
public class PassengerManager implements PacketListener {

    // The riders the server itself put on each vehicle, as last sent.
    private static final Map<Integer, int[]> riders = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<Integer>> attachments = new ConcurrentHashMap<>();

    public PassengerManager(PacketEventsAPI<?> packetEventsAPI) {
        packetEventsAPI.getEventManager().registerListener(this, PacketListenerPriority.NORMAL);
    }

    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        if (!(event.getPacketType() instanceof PacketType.Play.Server type)) return;
        switch (type) {
            case SET_PASSENGERS -> complete(event);
            case SPAWN_ENTITY -> remountAfterSend(event, new WrapperPlayServerSpawnEntity(event).getEntityId());
            case SPAWN_PLAYER -> remountAfterSend(event, new WrapperPlayServerSpawnPlayer(event).getEntityId());
            // The client replaces its own player on respawn, which throws off what rides it.
            case RESPAWN -> remountAfterSend(event, event.getUser().getEntityId());
            default -> {}
        }
    }

    /**
     * Everything riding {@code entityId}: the server's riders and the attached holograms.
     */
    public static int[] getPassengers(int entityId) {
        Set<Integer> merged = new LinkedHashSet<>();
        for (int rider : riders.getOrDefault(entityId, new int[0])) merged.add(rider);
        merged.addAll(attachments.getOrDefault(entityId, Set.of()));
        return merged.stream().mapToInt(Integer::intValue).toArray();
    }

    static void attach(int vehicleId, int passengerId) {
        attachments.computeIfAbsent(vehicleId, id -> ConcurrentHashMap.newKeySet()).add(passengerId);
    }

    static void detach(int vehicleId, int passengerId) {
        attachments.computeIfPresent(vehicleId, (id, passengers) -> {
            passengers.remove(passengerId);
            return passengers.isEmpty() ? null : passengers;
        });
    }

    private static void complete(PacketSendEvent event) {
        WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
        int vehicleId = packet.getEntityId();
        Set<Integer> attached = attachments.getOrDefault(vehicleId, Set.of());
        // Whatever is not an attached hologram was put there by the server.
        riders.put(vehicleId, Arrays.stream(packet.getPassengers()).filter(id -> !attached.contains(id)).toArray());
        int[] passengers = getPassengers(vehicleId);
        if (Arrays.equals(passengers, packet.getPassengers())) return;
        packet.setPassengers(passengers);
        event.markForReEncode(true);
    }

    private static void remountAfterSend(PacketSendEvent event, int vehicleId) {
        if (!attachments.containsKey(vehicleId)) return;
        User user = event.getUser();
        event.getTasksAfterSend().add(() -> user.sendPacket(new WrapperPlayServerSetPassengers(vehicleId, getPassengers(vehicleId))));
    }
}
