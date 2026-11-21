package com.maximde.hologramlib.hologram;

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.maximde.hologramlib.HologramLib;
import com.maximde.hologramlib.hook.PlaceholderAPIHook;
import com.maximde.hologramlib.listener.PerPlayerTextListener;
import com.maximde.hologramlib.utils.MiniMessage;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.tofaa.entitylib.meta.EntityMeta;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.joml.Vector3f;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public class TextHologram extends Hologram<TextHologram> {

    /**
     * @deprecated Never used by HologramLib and not the text index of the metadata packet (23 since 1.20.2).
     */
    @Deprecated
    public static final int TEXT_DISPLAY_META_INDEX = 15;

    @Getter
    protected String rawText = "";
    protected Component text = Component.text("");

    @Setter @Getter @Accessors(chain = true)
    private boolean shadow = true;

    @Setter @Getter @Accessors(chain = true)
    private int maxLineWidth = 200;

    /**
     * The background color is an argb integer which can be generated <a href="https://argb-int-calculator.netlify.app/">here</a>
     */
    @Setter @Getter @Accessors(chain = true)
    private int backgroundColor;

    /**
     * Controls how the text appears through blocks.
     * When true, text will be visible through solid blocks.
     * When false, blocks will occlude the text normally.
     */
    @Setter @Getter @Accessors(chain = true)
    private boolean seeThroughBlocks = false;

    @Setter @Getter @Accessors(chain = true)
    private TextDisplay.TextAlignment alignment = TextDisplay.TextAlignment.CENTER;

    @Setter @Getter @Accessors(chain = true)
    private byte textOpacity = (byte) -1;

    @Setter @Getter @Accessors(chain = true)
    private boolean placeholderApiEnabled = false;

    /**
     * Whether per-player texts, from {@link #setTextProvider} and PlaceholderAPI, are worked out again every update
     * period. Only viewers whose text changed get a packet.
     */
    @Setter @Getter @Accessors(chain = true)
    private boolean placeholderRefresh = true;

    /**
     * Gives every viewer their own MiniMessage text instead of {@link #getRawText()}. PlaceholderAPI placeholders in
     * it are filled in too when {@link #isPlaceholderApiEnabled()}. Called for each viewer every update period and
     * when packets go out, also off the main thread, so keep it fast and thread safe.
     */
    @Setter @Getter @Accessors(chain = true)
    private volatile @Nullable Function<Player, String> textProvider;

    private final Map<UUID, String> sentTexts = new ConcurrentHashMap<>();

    /**
     * Creates a new text hologram with the specified ID and render mode.
     *
     * @param id Unique identifier for this hologram. Cannot contain spaces.
     * @param renderMode Determines how and to whom the hologram is rendered
     * @throws IllegalArgumentException if id contains spaces
     */
    public TextHologram(String id, RenderMode renderMode) {
        super(id, renderMode, EntityTypes.TEXT_DISPLAY);
    }

    /**
     * Creates a new text hologram with the specified ID and nearby render mode.
     *
     * @param id Unique identifier for this hologram. Cannot contain spaces.
     * @throws IllegalArgumentException if id contains spaces
     */
    public TextHologram(String id) {
        this(id, RenderMode.ALL);
    }

    /**
     * Creates a copy of this hologram with a new ID.
     * The new ID will be the original ID with '_copy_' followed by a random number appended.
     * @return A new TextHologram instance with copied properties
     */
    @Override
    protected TextHologram copy() {
        int randomNumber = ThreadLocalRandom.current().nextInt(100000);
        return this.copy(this.id + "_copy_" + randomNumber);
    }

    /**
     * Creates a copy of this hologram with a new ID.
     *
     * @return A new TextHologram instance with copied properties
     */
    @Override
    public TextHologram copy(String id) {
        TextHologram copy = new TextHologram(id, this.renderMode);
        copy.text = this.text;
        copy.rawText = this.rawText;
        copy.scale = new Vector3f(this.scale);
        copy.translation = new Vector3f(this.translation);
        copy.rightRotation = new Quaternion4f(this.rightRotation.getX(), this.rightRotation.getY(),
                this.rightRotation.getZ(), this.rightRotation.getW());
        copy.leftRotation = new Quaternion4f(this.leftRotation.getX(), this.leftRotation.getY(),
                this.leftRotation.getZ(), this.leftRotation.getW());
        copy.billboard = this.billboard;
        copy.teleportDuration = this.teleportDuration;
        copy.interpolationDurationTransformation = this.interpolationDurationTransformation;
        copy.viewRange = this.viewRange;
        copy.shadow = this.shadow;
        copy.maxLineWidth = this.maxLineWidth;
        copy.backgroundColor = this.backgroundColor;
        copy.seeThroughBlocks = this.seeThroughBlocks;
        copy.alignment = this.alignment;
        copy.glowing = this.glowing;
        copy.glowColor = this.glowColor;
        copy.textOpacity = this.textOpacity;
        copy.setUpdateTaskPeriod(this.updateTaskPeriod);
        copy.maxPlayerRenderDistanceSquared = this.maxPlayerRenderDistanceSquared;
        copy.setViewCondition(this.viewCondition);
        copy.placeholderApiEnabled = this.placeholderApiEnabled;
        copy.placeholderRefresh = this.placeholderRefresh;
        copy.textProvider = this.textProvider;
        return copy;
    }

    @Override
    protected EntityMeta applyMeta() {
        TextDisplayMeta meta = (TextDisplayMeta) super.entity.getEntityMeta();

        // Set the text, if PlaceholderAPI is enabled, we'll handle it per-player in the hook
        meta.setText(this.text);

        meta.setInterpolationDelay(-1);
        meta.setTransformationInterpolationDuration(this.interpolationDurationTransformation);
        meta.setPositionRotationInterpolationDuration(this.teleportDuration);
        meta.setTranslation(super.toVector3f(this.translation));
        meta.setLeftRotation(this.leftRotation);
        meta.setRightRotation(this.rightRotation);
        meta.setScale(super.toVector3f(this.scale));
        meta.setBillboardConstraints(AbstractDisplayMeta.BillboardConstraints.valueOf(this.billboard.name()));
        meta.setLineWidth(this.maxLineWidth);
        meta.setViewRange((float) this.viewRange);
        meta.setGlowing(this.glowing);
        meta.setBackgroundColor(this.backgroundColor);
        meta.setTextOpacity(this.textOpacity);
        meta.setGlowColorOverride(this.glowColor);
        meta.setShadow(this.shadow);
        meta.setInvisible(this.isInvisible);
        if(super.brightness > -1) meta.setBrightnessOverride(super.brightness);
        meta.setSeeThrough(this.seeThroughBlocks);
        setInternalAlignment(meta);
        return meta;
    }

    @Override
    public int getHeight() {
        String plainText = getTextWithoutColor();
        int lineCount = plainText.isEmpty() ? 1 : plainText.split("\n").length;
        float charHeight = 0.1f;
        float totalHeight = lineCount * charHeight;
        return Math.max(1, (int) Math.ceil(totalHeight * this.scale.y));
    }

    @Override
    public int getWidth() {
        String plainText = getTextWithoutColor();
        String[] lines = plainText.isEmpty() ? new String[]{""} : plainText.split("\n");
        int maxLength = 0;
        for (String line : lines) {
            maxLength = Math.max(maxLength, line.length());
        }
        float charWidth = 0.06f;
        float totalWidth = maxLength * charWidth;
        return Math.max(1, (int) Math.ceil(totalWidth * Math.max(this.scale.x, this.scale.z)));
    }

    private void setInternalAlignment(TextDisplayMeta meta) {
        switch (this.alignment) {
            case LEFT -> meta.setAlignLeft(true);
            case RIGHT -> meta.setAlignRight(true);
        }
    }

    public Component getTextAsComponent() {
        return this.text;
    }


    public String getText() {
        StringBuilder plain = new StringBuilder();
        ComponentFlattener.basic().flatten(this.text, plain::append);
        return plain.toString();
    }

    public String getTextWithoutColor() {
        return ChatColor.stripColor(getText());
    }

    public TextHologram setText(String text) {
        this.rawText = text;
        this.text = Component.text(replaceFontImages(text));
        return this;
    }

    public TextHologram setText(Component component) {
        this.rawText = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(component);
        this.text = component;
        return this;
    }

    public TextHologram setMiniMessageText(String text) {
        this.rawText = text;
        this.text = MiniMessage.get(replaceFontImages(text));
        return this;
    }

    /**
     * Sends viewers only the current text instead of every property like {@link #update()} does
     */
    public TextHologram updateText() {
        TextDisplayMeta meta = (TextDisplayMeta) super.entity.getEntityMeta();
        sendChanges(() -> meta.setText(this.text));
        return this;
    }

    public TextHologram setRawText(String rawText) {
        this.rawText = rawText;

        if (!placeholderApiEnabled) {
            this.text = MiniMessage.get(replaceFontImages(rawText));
        }
        return this;
    }

    /**
     * The text {@code player} sees, with their {@link #setTextProvider text provider} and placeholders applied.
     */
    public Component getTextForPlayer(Player player) {
        if (!hasPerPlayerText()) return this.text;
        return MiniMessage.get(replaceFontImages(getRawTextFor(player)));
    }

    /**
     * Like {@link #getTextForPlayer} but the MiniMessage text before it is parsed.
     */
    public String getRawTextFor(Player player) {
        Function<Player, String> provider = this.textProvider;
        String forPlayer = provider == null ? rawText : provider.apply(player);
        if (forPlayer == null) return "";
        if (placeholderApiEnabled && PlaceholderAPIHook.isEnabled()) return PlaceholderAPIHook.setPlaceholders(player, forPlayer);
        return forPlayer;
    }

    /**
     * Whether viewers can see different texts, because of a text provider or PlaceholderAPI.
     */
    public boolean hasPerPlayerText() {
        return textProvider != null || (placeholderApiEnabled && PlaceholderAPIHook.isEnabled());
    }

    /**
     * Works out every viewer's text again right away instead of at the next update period, for when something the
     * {@link #setTextProvider text provider} reads has changed. Only viewers whose text changed get a packet.
     */
    public TextHologram refreshTexts() {
        if (!dead && hasPerPlayerText()) PerPlayerTextListener.refresh(this, sentTexts);
        return this;
    }

    @Override
    protected void onUpdateTick() {
        super.onUpdateTick();
        if (placeholderRefresh) refreshTexts();
    }

    public String replaceFontImages(String string) {
        return HologramLib.getReplaceText().replace(string);
    }
}