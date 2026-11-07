package com.fish_dan_.data_energistics.common.weapon.platform.storage;

import com.fish_dan_.data_energistics.common.weapon.platform.model.HudModule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;

/** Equipped-platform display switches and fields; visual placement and colors remain client preferences. */
public record PlatformHudDisplay(int visible, IntList fields) {

    public static final PlatformHudDisplay DEFAULT = new PlatformHudDisplay(63, IntList.of(7, 49, 7, 975, 7, 3));
    public static final Codec<PlatformHudDisplay> CODEC = RecordCodecBuilder.<PlatformHudDisplay>create(instance -> instance.group(
            Codec.intRange(0, 63).fieldOf("visible").forGetter(PlatformHudDisplay::visible),
            Codec.intRange(0, 1023).listOf(6, 6).<IntList>xmap(IntArrayList::new, values -> values).fieldOf("fields").forGetter(PlatformHudDisplay::fields))
            .apply(instance, PlatformHudDisplay::new)).validate(settings -> {
                for (HudModule module : HudModule.values()) {
                    if ((settings.fields().getInt(module.ordinal()) & ~module.fields) != 0) return DataResult.error(() -> "Invalid HUD fields for " + module);
                }
                return DataResult.success(settings);
            });

    public PlatformHudDisplay {
        fields = IntLists.unmodifiable(new IntArrayList(fields));
    }

    public boolean visible(HudModule module) {
        return (visible & 1 << module.ordinal()) != 0;
    }
}
