package com.fish_dan_.data_energistics.config.client.terminal;

import com.fish_dan_.data_energistics.client.terminal.TerminalViewportLayout;

import net.minecraft.util.StringRepresentable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Optional;

/** Immutable file snapshot; each host screen retains its own window while sharing display choices. */
public record TerminalPreferences(Object2ObjectMap<String, Window> screens, ShiftClickDestination shiftClickDestination,
                                  SortMode sort, ResourceFilter filter, SortDirection sortDirection,
                                  ObjectSet<String> disabledKeyTypes) {

    public static final TerminalPreferences DEFAULT = new TerminalPreferences(Object2ObjectMaps.emptyMap(),
            ShiftClickDestination.PLAYER_INVENTORY, SortMode.NAME, ResourceFilter.ALL, SortDirection.ASCENDING, ObjectSets.emptySet());
    public static final Codec<TerminalPreferences> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Window.CODEC).xmap(Object2ObjectLinkedOpenHashMap::new, values -> values)
                    .optionalFieldOf("screens", new Object2ObjectLinkedOpenHashMap<>()).forGetter(value -> new Object2ObjectLinkedOpenHashMap<>(value.screens())),
            ShiftClickDestination.BOOLEAN_CODEC.optionalFieldOf("shift_click_to_network", ShiftClickDestination.PLAYER_INVENTORY)
                    .forGetter(TerminalPreferences::shiftClickDestination),
            SortMode.CODEC.optionalFieldOf("sort", SortMode.NAME).forGetter(TerminalPreferences::sort),
            ResourceFilter.CODEC.optionalFieldOf("filter", ResourceFilter.ALL).forGetter(TerminalPreferences::filter),
            SortDirection.BOOLEAN_CODEC.optionalFieldOf("descending", SortDirection.ASCENDING).forGetter(TerminalPreferences::sortDirection),
            Codec.STRING.listOf().xmap(ObjectOpenHashSet::new, ObjectArrayList::new)
                    .optionalFieldOf("disabled_key_types", new ObjectOpenHashSet<>()).forGetter(value -> new ObjectOpenHashSet<>(value.disabledKeyTypes())))
            .apply(instance, TerminalPreferences::new));

    public TerminalPreferences {
        screens = Object2ObjectMaps.unmodifiable(new Object2ObjectLinkedOpenHashMap<>(screens));
        disabledKeyTypes = ObjectSets.unmodifiable(new ObjectOpenHashSet<>(disabledKeyTypes));
    }

    public record Window(float xPercent, float yPercent, int width, int height, boolean folded, Optional<Boolean> open,
                         UploadPanel upload) {

        public static final Window DEFAULT = new Window(0.047058824F, 0.11111111F, TerminalViewportLayout.MIN_WIDTH, 168, true, Optional.empty(), UploadPanel.DEFAULT);
        public static final Codec<Window> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("x_percent", DEFAULT.xPercent()).forGetter(Window::xPercent),
                Codec.FLOAT.optionalFieldOf("y_percent", DEFAULT.yPercent()).forGetter(Window::yPercent),
                Codec.INT.optionalFieldOf("width", DEFAULT.width()).forGetter(Window::width),
                Codec.INT.optionalFieldOf("height", DEFAULT.height()).forGetter(Window::height),
                Codec.BOOL.optionalFieldOf("folded", true).forGetter(Window::folded),
                Codec.BOOL.optionalFieldOf("open").forGetter(Window::open),
                UploadPanel.CODEC.optionalFieldOf("upload", UploadPanel.DEFAULT).forGetter(Window::upload))
                .apply(instance, Window::new));

        public Window {
            xPercent = Float.isFinite(xPercent) ? Math.clamp(xPercent, 0, 1) : DEFAULT_PERCENT_X;
            yPercent = Float.isFinite(yPercent) ? Math.clamp(yPercent, 0, 1) : DEFAULT_PERCENT_Y;
        }
    }

    public enum SortMode implements StringRepresentable {

        NAME("name"),
        AMOUNT("amount"),
        MOD("mod");

        public static final Codec<SortMode> CODEC = StringRepresentable.fromEnum(SortMode::values);
        private final String serializedName;

        SortMode(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return this.serializedName;
        }
    }

    public enum ResourceFilter implements StringRepresentable {

        ALL("all"),
        STORED("stored"),
        CRAFTABLE("craftable");

        public static final Codec<ResourceFilter> CODEC = StringRepresentable.fromEnum(ResourceFilter::values);
        private final String serializedName;

        ResourceFilter(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return this.serializedName;
        }
    }

    public enum SortDirection implements StringRepresentable {

        ASCENDING,
        DESCENDING;

        public static final Codec<SortDirection> CODEC = StringRepresentable.fromEnum(SortDirection::values);
        /** Retains the boolean format used by existing terminal preference files. */
        public static final Codec<SortDirection> BOOLEAN_CODEC = Codec.BOOL.xmap(
                descending -> descending ? DESCENDING : ASCENDING,
                direction -> direction == DESCENDING);

        @Override
        public String getSerializedName() {
            return this == DESCENDING ? "descending" : "ascending";
        }
    }

    public enum ShiftClickDestination implements StringRepresentable {

        PLAYER_INVENTORY,
        NETWORK;

        public static final Codec<ShiftClickDestination> CODEC = StringRepresentable.fromEnum(ShiftClickDestination::values);
        /** Retains the boolean format used by existing terminal preference files. */
        public static final Codec<ShiftClickDestination> BOOLEAN_CODEC = Codec.BOOL.xmap(
                toNetwork -> toNetwork ? NETWORK : PLAYER_INVENTORY,
                destination -> destination == NETWORK);

        @Override
        public String getSerializedName() {
            return this == NETWORK ? "network" : "player_inventory";
        }
    }

    private static final float DEFAULT_PERCENT_X = 0.7771174F;
    private static final float DEFAULT_PERCENT_Y = 0.6183746F;

    /** Saved geometry only; opening this panel remains an explicit client action. */
    public record UploadPanel(float xPercent, float yPercent, int width, int height, boolean saved) {

        public static final UploadPanel DEFAULT = new UploadPanel(0, 0, 0, 0, false);
        public static final Codec<UploadPanel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("x_percent").forGetter(UploadPanel::xPercent),
                Codec.FLOAT.fieldOf("y_percent").forGetter(UploadPanel::yPercent),
                Codec.INT.fieldOf("width").forGetter(UploadPanel::width),
                Codec.INT.fieldOf("height").forGetter(UploadPanel::height),
                Codec.BOOL.optionalFieldOf("saved", true).forGetter(UploadPanel::saved))
                .apply(instance, UploadPanel::new));

        public UploadPanel(float xPercent, float yPercent, int width, int height) {
            this(xPercent, yPercent, width, height, true);
        }

        public UploadPanel {
            xPercent = Float.isFinite(xPercent) ? Math.clamp(xPercent, 0, 1) : 0;
            yPercent = Float.isFinite(yPercent) ? Math.clamp(yPercent, 0, 1) : 0;
        }
    }
}
