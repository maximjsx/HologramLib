package com.maximde.hologramlib.hook;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.maximde.hologramlib.HologramLib;
import com.maximde.hologramlib.hologram.Hologram;
import com.maximde.hologramlib.hologram.TextHologram;
import com.maximde.hologramlib.utils.MiniMessage;
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PlaceholderAPIHook implements PacketListener {

    private static final int MAX_CACHED_TEXTS = 4096;

    private static final Map<String, Component> PARSED_TEXTS = new ConcurrentHashMap<>();

    @Getter
    private static volatile boolean enabled;

    public PlaceholderAPIHook(PacketEventsAPI<?> packetEventsAPI) {
        packetEventsAPI.getEventManager().registerListener(this, PacketListenerPriority.NORMAL);
        enabled = true;
    }

    public static void refresh(TextHologram hologram, Map<UUID, String> lastSentTexts) {
        String rawText = hologram.getRawText();
        if (rawText == null || rawText.isEmpty()) return;

        List<Player> viewers = hologram.getViewers();
        Set<UUID> viewerIds = viewers.stream().map(Player::getUniqueId).collect(Collectors.toSet());
        lastSentTexts.keySet().retainAll(viewerIds);

        for (Player viewer : viewers) {
            String parsed = PlaceholderAPI.setPlaceholders(viewer, rawText);
            if (parsed.equals(lastSentTexts.put(viewer.getUniqueId(), parsed))) continue;
            HologramLib.getPlayerManager().sendPacket(viewer, new WrapperPlayServerEntityMetadata(hologram.getEntityID(),
                    List.of(new EntityData<>(TextDisplayMeta.OFFSET, EntityDataTypes.ADV_COMPONENT, Component.empty()))));
        }
    }

    private static Component parse(TextHologram hologram, String text) {
        if (PARSED_TEXTS.size() >= MAX_CACHED_TEXTS) PARSED_TEXTS.clear();
        return PARSED_TEXTS.computeIfAbsent(text, key -> MiniMessage.get(hologram.replaceFontImages(key)));
    }

    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) return;

        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        int entityId = packet.getEntityId();

        Optional<com.maximde.hologramlib.hologram.HologramManager> managerOpt = HologramLib.getManager();
        if (managerOpt.isEmpty()) {
            return;
        }

        Optional<Hologram<?>> hologramOptional = managerOpt.get().getHologramByEntityId(entityId);
        if (hologramOptional.isEmpty()) {
            return;
        }

        Hologram<?> hologram = hologramOptional.get();
        if (!(hologram instanceof TextHologram textHologram)) {
            return;
        }

        if (!textHologram.isPlaceholderApiEnabled()) {
            return;
        }

        Player player = (Player) event.getPlayer();
        if (player == null) {
            return;
        }

        String rawText = textHologram.getRawText();
        if (rawText == null || rawText.isEmpty()) {
            return;
        }


        List<EntityData<?>> metadata = packet.getEntityMetadata();
        if (metadata.stream().noneMatch(data -> data.getType() == EntityDataTypes.ADV_COMPONENT)) {
            return;
        }

        String parsedText = PlaceholderAPI.setPlaceholders(player, rawText);
        Component finalComponent = parse(textHologram, parsedText);

        List<EntityData<?>> newMetadata = new ArrayList<>(metadata.size());
        for (EntityData<?> data : metadata) {
            newMetadata.add(data.getType() == EntityDataTypes.ADV_COMPONENT
                    ? new EntityData<>(data.getIndex(), EntityDataTypes.ADV_COMPONENT, finalComponent)
                    : data);
        }
        packet.setEntityMetadata(newMetadata);
    }
}