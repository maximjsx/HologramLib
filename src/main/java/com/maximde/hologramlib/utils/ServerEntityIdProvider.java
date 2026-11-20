package com.maximde.hologramlib.utils;

import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import me.tofaa.entitylib.EntityIdProvider;
import org.bukkit.Bukkit;
import org.bukkit.UnsafeValues;
import org.bukkit.World;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.UUID;


final class ServerEntityIdProvider implements EntityIdProvider {

    private final MethodHandle nextEntityId;

    private ServerEntityIdProvider(MethodHandle nextEntityId) {
        this.nextEntityId = nextEntityId;
    }

    static Optional<ServerEntityIdProvider> find() {
        try {
            MethodHandle handle = MethodHandles.publicLookup()
                    .findVirtual(UnsafeValues.class, "nextEntityId", MethodType.methodType(int.class, World.class))
                    .bindTo(Bukkit.getUnsafe());
            return Optional.of(new ServerEntityIdProvider(handle));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public int provide(UUID entityUuid, EntityType entityType) {
        try {
            return (int) nextEntityId.invokeExact(Bukkit.getWorlds().get(0));
        } catch (Throwable throwable) {
            throw new IllegalStateException("Could not get an entity id from the server", throwable);
        }
    }
}
