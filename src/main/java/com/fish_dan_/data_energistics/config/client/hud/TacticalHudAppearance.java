package com.fish_dan_.data_energistics.config.client.hud;

import com.fish_dan_.data_energistics.common.weapon.platform.model.HudModule;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle;
import com.fish_dan_.data_energistics.config.client.hud.tactical.HudSidebarLayout;
import com.fish_dan_.data_energistics.config.client.hud.tactical.HudWidgetStyle;
import com.fish_dan_.data_energistics.config.client.hud.tactical.TacticalHudWidget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;

/** Complete local appearance snapshot. Missing module entries receive their documented default style. */
public record TacticalHudAppearance(Reference2ObjectMap<HudModule, HudModuleStyle> styles,
                                    Reference2ObjectMap<TacticalHudWidget, HudWidgetStyle> widgets, HudSidebarLayout weapons) {

    public static final TacticalHudAppearance DEFAULT = new TacticalHudAppearance(Reference2ObjectMaps.emptyMap(), Reference2ObjectMaps.emptyMap(), HudSidebarLayout.EMPTY);
    private static final Codec<Reference2ObjectMap<HudModule, HudModuleStyle>> MODULES = Codec.unboundedMap(HudModule.CODEC, HudModuleStyle.CODEC)
            .xmap(Reference2ObjectLinkedOpenHashMap::new, styles -> styles);
    private static final Codec<TacticalHudAppearance> STRUCTURED = RecordCodecBuilder.create(instance -> instance.group(
            MODULES.fieldOf("modules").forGetter(TacticalHudAppearance::styles),
            Codec.unboundedMap(TacticalHudWidget.CODEC, HudWidgetStyle.CODEC).optionalFieldOf("widgets", Reference2ObjectMaps.emptyMap()).forGetter(TacticalHudAppearance::widgets),
            HudSidebarLayout.CODEC.optionalFieldOf("weapons", HudSidebarLayout.EMPTY).forGetter(TacticalHudAppearance::weapons))
            .apply(instance, (styles, widgets, weapons) -> new TacticalHudAppearance(styles, new Reference2ObjectLinkedOpenHashMap<>(widgets), weapons)));
    public static final Codec<TacticalHudAppearance> CODEC = Codec.withAlternative(STRUCTURED,
            MODULES.xmap(TacticalHudAppearance::fromLegacy, TacticalHudAppearance::styles));

    public TacticalHudAppearance {
        Reference2ObjectMap<HudModule, HudModuleStyle> complete = new Reference2ObjectLinkedOpenHashMap<>();
        for (HudModule module : HudModule.values()) complete.put(module, styles.getOrDefault(module, defaults(module)));
        styles = Reference2ObjectMaps.unmodifiable(complete);
        Reference2ObjectMap<TacticalHudWidget, HudWidgetStyle> completeWidgets = new Reference2ObjectLinkedOpenHashMap<>();
        for (TacticalHudWidget widget : TacticalHudWidget.values()) completeWidgets.put(widget, widgets.getOrDefault(widget, migrated(widget, styles)));
        widgets = Reference2ObjectMaps.unmodifiable(completeWidgets);
    }

    public TacticalHudAppearance withModule(HudModule module, HudModuleStyle style) {
        var updated = new Reference2ObjectLinkedOpenHashMap<>(styles);
        updated.put(module, style);
        return new TacticalHudAppearance(updated, widgets, weapons);
    }

    public TacticalHudAppearance withWidget(TacticalHudWidget widget, HudWidgetStyle style) {
        var updated = new Reference2ObjectLinkedOpenHashMap<>(widgets);
        updated.put(widget, style);
        return new TacticalHudAppearance(styles, updated, weapons);
    }

    public TacticalHudAppearance withWeapons(HudSidebarLayout layout) {
        return new TacticalHudAppearance(styles, widgets, layout);
    }

    private static HudWidgetStyle migrated(TacticalHudWidget widget, Reference2ObjectMap<HudModule, HudModuleStyle> styles) {
        HudModule source = widget.appearanceSource();
        HudModuleStyle old = styles.get(source), original = legacyDefaults(source);
        var migrated = HudWidgetStyle.defaults(widget).toBuilder().scale(old.scale());
        if (old.textColor() != original.textColor()) migrated.textColor(old.textColor());
        if (old.borderColor() != original.borderColor()) migrated.lineColor(old.borderColor());
        return migrated.build();
    }

    public static HudModuleStyle defaults(HudModule module) {
        var style = HudModuleStyle.builder().scale(1).textColor(WeaponPlatformStyle.TEXT)
                .borderColor(WeaponPlatformStyle.EDGE).visible(true);
        return switch (module) {
            case COMPASS -> style.x(0.5).y(0.055).textColor(HudWidgetStyle.TEXT).borderColor(HudWidgetStyle.LINE).borderWidth(1).fields(7).build();
            case WEAPONS -> style.x(0.015).y(0.65).background(WeaponPlatformStyle.HUD_INFORMATION_BACKGROUND)
                    .borderColor(WeaponPlatformStyle.HUD_INFORMATION_EDGE).borderWidth(1).fields(49).build();
            case NETWORK -> style.x(0.015).y(0.14).background(WeaponPlatformStyle.HUD_INFORMATION_BACKGROUND)
                    .borderColor(WeaponPlatformStyle.HUD_INFORMATION_EDGE).borderWidth(1).fields(7).build();
            case TARGETS -> style.background(0xb0101e26).borderWidth(1).relationColors(true)
                    .fields(HudModuleStyle.NAME | HudModuleStyle.DISTANCE | HudModuleStyle.HEALTH | HudModuleStyle.BINARY | HudModuleStyle.TARGET | HudModuleStyle.SPEED | HudModuleStyle.ALTITUDE | HudModuleStyle.MOTION).build();
            case GUIDES -> style.background(0xb0e3e3ea).borderWidth(1).fields(7).build();
            case ORES -> style.x(0.5).y(0.55).background(0x609a9fb4).borderWidth(1).fields(HudModuleStyle.NAME | HudModuleStyle.DISTANCE).build();
        };
    }

    private static HudModuleStyle legacyDefaults(HudModule module) {
        return module == HudModule.COMPASS ? defaults(module).toBuilder().y(0.015).textColor(WeaponPlatformStyle.EDGE).borderColor(WeaponPlatformStyle.EDGE).borderWidth(0).build() : defaults(module);
    }

    private static TacticalHudAppearance fromLegacy(Reference2ObjectMap<HudModule, HudModuleStyle> styles) {
        var migrated = new Reference2ObjectLinkedOpenHashMap<>(styles);
        HudModuleStyle original = legacyDefaults(HudModule.COMPASS);
        HudModuleStyle old = styles.getOrDefault(HudModule.COMPASS, original);
        var compass = old.toBuilder();
        HudModuleStyle current = defaults(HudModule.COMPASS);
        if (old.x() == original.x() && old.y() == original.y()) compass.x(current.x()).y(current.y());
        if (old.textColor() == original.textColor()) compass.textColor(current.textColor());
        if (old.borderColor() == original.borderColor()) compass.borderColor(current.borderColor());
        if (old.borderWidth() == original.borderWidth()) compass.borderWidth(current.borderWidth());
        migrated.put(HudModule.COMPASS, compass.build());
        return new TacticalHudAppearance(migrated, Reference2ObjectMaps.emptyMap(), HudSidebarLayout.EMPTY);
    }
}
