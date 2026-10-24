package com.fish_dan_.data_energistics.common.weapon.platform.prospecting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

public record OreSection(long section, IntList positions, IntList states) {

    private static final Codec<IntList> INTS = Codec.INT.listOf(0, 4096).xmap(IntArrayList::new, values -> values);
    public static final Codec<OreSection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("section").forGetter(OreSection::section),
            INTS.fieldOf("positions").forGetter(OreSection::positions),
            INTS.fieldOf("states").forGetter(OreSection::states)).apply(instance, OreSection::new));

    public OreSection {
        if (positions.size() != states.size()) throw new IllegalArgumentException("Mismatched ore section states");
    }
}
