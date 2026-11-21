package com.maximde.hologramlib.hologram.custom;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.maximde.hologramlib.hologram.BlockHologram;
import com.maximde.hologramlib.hologram.Hologram;
import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.ItemHologram;
import com.maximde.hologramlib.hologram.MotionAnimation;
import com.maximde.hologramlib.hologram.TextHologram;
import com.maximde.hologramlib.utils.BukkitTasks;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The best three on pedestals of different heights, each with their head and a label over it, the winner in the
 * middle. Faces the yaw of the location it is spawned at, set the places with {@link #setEntries}.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@RequiredArgsConstructor
public class Podium {

    /**
     * @param label MiniMessage under the name, like the score
     */
    public record Entry(UUID uuid, String name, String label) {}

    private record Spot(float x, float height, Material block, String color) {}

    @Getter
    private final String id;

    /**
     * Pedestals of the first, second and third place, in that order.
     */
    private final List<Spot> spots = new ArrayList<>(List.of(
            new Spot(0, 1.0f, Material.GOLD_BLOCK, "#fffb00"),
            new Spot(-1.1f, 0.7f, Material.IRON_BLOCK, "#cfcfcf"),
            new Spot(1.1f, 0.45f, Material.COPPER_BLOCK, "#d3854d")));

    /**
     * MiniMessage over each head with {@code {color}}, {@code {place}}, {@code {name}} and {@code {label}}.
     */
    @Getter @Setter @Accessors(chain = true)
    private String labelFormat = "<color:{color}><bold>{name}</bold><newline><gray>{label}";

    /**
     * The winner's head turns and glows.
     */
    @Getter @Setter @Accessors(chain = true)
    private boolean celebrateWinner = true;

    private final List<Hologram<?>> parts = new ArrayList<>();
    private final List<ItemHologram> heads = new ArrayList<>();
    private final List<TextHologram> labels = new ArrayList<>();
    private List<Entry> entries = List.of();

    /**
     * @param place 1, 2 or 3
     */
    public Podium setPedestal(int place, Material block, float height) {
        Spot spot = spots.get(place - 1);
        spots.set(place - 1, new Spot(spot.x(), height, block, spot.color()));
        return this;
    }

    /**
     * The best three in order, fewer leave pedestals empty. Shows right away when spawned.
     */
    public Podium setEntries(List<Entry> entries) {
        this.entries = List.copyOf(entries.subList(0, Math.min(3, entries.size())));
        if (!heads.isEmpty()) showEntries();
        return this;
    }

    public Podium spawn(HologramManager manager, Location location) {
        Location facing = location.clone();
        facing.setPitch(0);
        for (int index = 0; index < spots.size(); index++) {
            Spot spot = spots.get(index);
            Location base = offset(facing, spot.x());
            int place = index + 1;

            BlockHologram pedestal = new BlockHologram(id + "_pedestal_" + place)
                    .setBlock(blockState(spot.block()))
                    .setBillboard(Display.Billboard.FIXED)
                    .setScale(0.9f, spot.height(), 0.9f)
                    .setTranslation(-0.45f, 0, -0.45f);
            TextHologram number = new TextHologram(id + "_number_" + place)
                    .setMiniMessageText("<color:" + spot.color() + "><bold>" + place)
                    .setBillboard(Display.Billboard.FIXED)
                    .setScale(1.6f, 1.6f, 1.6f)
                    .setTranslation(0, spot.height() / 2 - 0.2f, 0.46f)
                    .setBackgroundColor(0);
            ItemHologram head = new ItemHologram(id + "_head_" + place)
                    .setItem(new ItemStack.Builder().type(ItemTypes.PLAYER_HEAD).build())
                    .setBillboard(Display.Billboard.FIXED)
                    .setScale(0.7f, 0.7f, 0.7f)
                    .setTranslation(0, spot.height() + 0.35f, 0);
            TextHologram label = new TextHologram(id + "_label_" + place)
                    .setTranslation(0, spot.height() + 0.75f, 0);
            if (place == 1 && celebrateWinner) head.setGlowing(true).setGlowColor(new Color(0xfffb00));

            add(manager, pedestal, base);
            add(manager, number, base);
            add(manager, head, base);
            add(manager, label, base);
            heads.add(head);
            labels.add(label);
            if (place == 1 && celebrateWinner) manager.applyMotion(head, MotionAnimation.spin(80));
        }
        showEntries();
        return this;
    }

    public void remove(HologramManager manager) {
        parts.forEach(manager::remove);
        parts.clear();
        heads.clear();
        labels.clear();
    }

    private <H extends Hologram<H>> void add(HologramManager manager, H part, Location location) {
        manager.spawn(part, location, false);
        parts.add(part);
    }

    private void showEntries() {
        for (int index = 0; index < labels.size(); index++) {
            TextHologram label = labels.get(index);
            if (index >= entries.size()) {
                label.setMiniMessageText("").update();
                continue;
            }
            Entry entry = entries.get(index);
            label.setMiniMessageText(labelFormat
                    .replace("{color}", spots.get(index).color())
                    .replace("{place}", String.valueOf(index + 1))
                    .replace("{name}", MiniMessage.miniMessage().escapeTags(entry.name()))
                    .replace("{label}", entry.label())).update();
            ItemHologram head = heads.get(index);
            // skin lookup can hit the web
            BukkitTasks.runTaskAsync(() -> head.setPlayerHead(entry.uuid()).update());
        }
    }

    // x goes to the right of whoever faces the podium
    private static Location offset(Location location, float x) {
        double yaw = Math.toRadians(location.getYaw());
        return location.clone().add(Math.cos(yaw) * x, 0, Math.sin(yaw) * x);
    }

    private static int blockState(Material material) {
        return WrappedBlockState.getByString(Bukkit.createBlockData(material).getAsString()).getGlobalId();
    }
}
