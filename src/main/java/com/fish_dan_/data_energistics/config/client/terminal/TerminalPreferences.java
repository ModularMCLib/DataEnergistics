package com.fish_dan_.data_energistics.config.client.terminal;

import com.fish_dan_.data_energistics.client.terminal.TerminalViewportLayout;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

/** Immutable file snapshot; each host screen retains its own window while sharing display choices. */
public record TerminalPreferences(Object2ObjectMap<String, Window> screens, boolean shiftClickToNetwork,
                                  String sort, String filter, boolean descending, ObjectSet<String> disabledKeyTypes) {

    public static final TerminalPreferences DEFAULT = new TerminalPreferences(Object2ObjectMaps.emptyMap(), false,
            "name", "all", false, ObjectSets.emptySet());
    public static final Codec<TerminalPreferences> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Window.CODEC).xmap(Object2ObjectLinkedOpenHashMap::new, values -> values)
                    .optionalFieldOf("screens", new Object2ObjectLinkedOpenHashMap<>()).forGetter(value -> new Object2ObjectLinkedOpenHashMap<>(value.screens())),
            Codec.BOOL.optionalFieldOf("shift_click_to_network", false).forGetter(TerminalPreferences::shiftClickToNetwork),
            Codec.STRING.optionalFieldOf("sort", "name").forGetter(TerminalPreferences::sort),
            Codec.STRING.optionalFieldOf("filter", "all").forGetter(TerminalPreferences::filter),
            Codec.BOOL.optionalFieldOf("descending", false).forGetter(TerminalPreferences::descending),
            Codec.STRING.listOf().xmap(ObjectOpenHashSet::new, values -> new ObjectArrayList<>(values))
                    .optionalFieldOf("disabled_key_types", new ObjectOpenHashSet<>()).forGetter(value -> new ObjectOpenHashSet<>(value.disabledKeyTypes())))
            .apply(instance, TerminalPreferences::new));

    public TerminalPreferences {
        screens = Object2ObjectMaps.unmodifiable(new Object2ObjectLinkedOpenHashMap<>(screens));
        disabledKeyTypes = ObjectSets.unmodifiable(new ObjectOpenHashSet<>(disabledKeyTypes));
    }

    public record Window(float xPercent, float yPercent, int width, int height, boolean folded, boolean open,
                         UploadPanel upload) {

        public static final Window DEFAULT = new Window(0.047058824F, 0.11111111F, TerminalViewportLayout.MIN_WIDTH, 168, true, true, UploadPanel.DEFAULT);
        public static final Codec<Window> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("x_percent", DEFAULT.xPercent()).forGetter(Window::xPercent),
                Codec.FLOAT.optionalFieldOf("y_percent", DEFAULT.yPercent()).forGetter(Window::yPercent),
                Codec.INT.optionalFieldOf("width", DEFAULT.width()).forGetter(Window::width),
                Codec.INT.optionalFieldOf("height", DEFAULT.height()).forGetter(Window::height),
                Codec.BOOL.optionalFieldOf("folded", true).forGetter(Window::folded),
                Codec.BOOL.optionalFieldOf("open", true).forGetter(Window::open),
                UploadPanel.CODEC.optionalFieldOf("upload", UploadPanel.DEFAULT).forGetter(Window::upload))
                .apply(instance, Window::new));

        public Window {
            xPercent = Float.isFinite(xPercent) ? Math.clamp(xPercent, 0, 1) : DEFAULT_PERCENT_X;
            yPercent = Float.isFinite(yPercent) ? Math.clamp(yPercent, 0, 1) : DEFAULT_PERCENT_Y;
        }
    }

    private static final float DEFAULT_PERCENT_X = 0.047058824F;
    private static final float DEFAULT_PERCENT_Y = 0.11111111F;

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
