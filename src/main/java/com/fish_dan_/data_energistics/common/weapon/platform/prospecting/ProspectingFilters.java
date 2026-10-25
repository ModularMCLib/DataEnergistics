package com.fish_dan_.data_energistics.common.weapon.platform.prospecting;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.Tags;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.Optional;

/** Each row selects one exact block or one block tag. Empty rows do not match blocks. */
public record ProspectingFilters(ObjectList<Entry> entries) {

    public static final Entry EMPTY = new Entry(Optional.empty(), Optional.empty());
    public static final ProspectingFilters DEFAULT = new ProspectingFilters(ObjectList.of(new Entry(Optional.empty(), Optional.of(Tags.Blocks.ORES.location()))));
    public static final Codec<ProspectingFilters> CODEC = Entry.CODEC.listOf(0, 64).xmap(values -> new ProspectingFilters(new ObjectArrayList<>(values)), ProspectingFilters::entries);

    public ProspectingFilters {
        entries = ObjectLists.unmodifiable(new ObjectArrayList<>(entries));
    }

    public record Entry(Optional<ResourceLocation> block, Optional<ResourceLocation> tag) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.<Entry>create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("block").forGetter(Entry::block),
                ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(Entry::tag)).apply(instance, Entry::new))
                .validate(entry -> entry.block().isPresent() && entry.tag().isPresent() ? DataResult.error(() -> "Prospecting rows cannot select both block and tag") : DataResult.success(entry));
    }
}
