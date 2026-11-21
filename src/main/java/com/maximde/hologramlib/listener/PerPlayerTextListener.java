package com.maximde.hologramlib.listener;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.maximde.hologramlib.HologramLib;
import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.TextHologram;
import com.maximde.hologramlib.utils.MiniMessage;
import lombok.RequiredArgsConstructor;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Puts each viewer's own text, from {@link TextHologram#setTextProvider} and PlaceholderAPI, into the text packets
 * on their way out.
 */
@RequiredArgsConstructor
public class PerPlayerTextListener implements PacketListener {

    private static final int MAX_CACHED_TEXTS = 4096;

    // most viewers get the same text, so parse each one once
    private static final Map<String, Component> PARSED_TEXTS = new ConcurrentHashMap<>();

    private final HologramManager hologramManager;

    /**
     * Works out the text of every viewer of {@code hologram} again and sends it to those whose text changed since
     * the last refresh. Meant for the main thread, where placeholder expansions are safe to call.
     *
     * @param sentTexts the text each viewer got last time, kept by the hologram between refreshes
     */
    public static void refresh(TextHologram hologram, Map<UUID, String> sentTexts) {
        List<Player> viewers = hologram.getViewers();
        Set<UUID> viewerIds = viewers.stream().map(Player::getUniqueId).collect(Collectors.toSet());
        sentTexts.keySet().retainAll(viewerIds);

        for (Player viewer : viewers) {
            String text = hologram.getRawTextFor(viewer);
            if (text.equals(sentTexts.put(viewer.getUniqueId(), text))) continue;
            // onPacketSend puts the real text in
            HologramLib.getPlayerManager().sendPacket(viewer, new WrapperPlayServerEntityMetadata(hologram.getEntityID(),
                    List.of(new EntityData<>(TextDisplayMeta.OFFSET, EntityDataTypes.ADV_COMPONENT, Component.empty()))));
        }
    }

    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) return;
        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        if (!(hologramManager.getHologramByEntityId(packet.getEntityId()).orElse(null) instanceof TextHologram hologram)) return;
        if (!hologram.hasPerPlayerText()) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        List<EntityData<?>> metadata = packet.getEntityMetadata();
        if (metadata.stream().noneMatch(data -> data.getType() == EntityDataTypes.ADV_COMPONENT)) return;

        Component text = parse(hologram, hologram.getRawTextFor(player));
        List<EntityData<?>> filled = new ArrayList<>(metadata.size());
        for (EntityData<?> data : metadata) {
            filled.add(data.getType() == EntityDataTypes.ADV_COMPONENT
                    ? new EntityData<>(data.getIndex(), EntityDataTypes.ADV_COMPONENT, text)
                    : data);
        }
        packet.setEntityMetadata(filled);
    }

    private static Component parse(TextHologram hologram, String text) {
        Component cached = PARSED_TEXTS.get(text);
        if (cached != null) return cached;
        if (PARSED_TEXTS.size() >= MAX_CACHED_TEXTS) PARSED_TEXTS.clear();
        Component parsed = MiniMessage.get(hologram.replaceFontImages(text));
        PARSED_TEXTS.put(text, parsed);
        return parsed;
    }
}
