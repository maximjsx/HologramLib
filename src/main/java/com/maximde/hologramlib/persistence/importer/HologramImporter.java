package com.maximde.hologramlib.persistence.importer;

import com.maximde.hologramlib.persistence.HologramData;
import com.maximde.hologramlib.utils.BukkitTasks;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class HologramImporter {

    public record Result(List<HologramData> holograms, int skipped) {}

    private static final long TIMEOUT_SECONDS = 30;
    private static final Pattern INVALID_ID_CHARS = Pattern.compile("[^A-Za-z0-9_-]");

    private final String name;
    private final Path source;
    @Getter(AccessLevel.NONE)
    protected final Logger logger;

    public String key() {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Empty if there is nothing to import at {@link #getSource()}
     */
    public CompletableFuture<Optional<Result>> read() {
        return CompletableFuture.supplyAsync(this::readAll, BukkitTasks::runTaskAsync)
                .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((result, error) -> {
                    if (error != null) logger.log(Level.SEVERE, "Importing from " + name + " failed", error);
                });
    }

    protected abstract Optional<Result> readAll();

    protected void logSkipped(String hologram) {
        logger.info("Skipping the " + name + " hologram " + hologram + ", it is disabled, broken or has nothing to import");
    }

    protected static String validId(String name) {
        return INVALID_ID_CHARS.matcher(name).replaceAll("_");
    }
}
