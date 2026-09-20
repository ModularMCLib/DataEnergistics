package com.fish_dan_.data_energistics.api.crafting.matching;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/**
 * Immutable item equivalence shared by pattern editing, planning, dispatch and return routing.
 * Tags are an OR of declarations resolved from a native recipe, not arbitrary tags of the selected item.
 * Construct after registry/tag loading; match on the logical server or against a synchronized client registry.
 * Does not consume resources, compare quantities, or rewrite item components. Non-item keys match only exactly.
 */
@NullMarked
public record ItemMatchingRule(ProcessingMatchMode mode, ObjectList<ResourceLocation> tags) {

    public static final ItemMatchingRule EXACT = new ItemMatchingRule(ProcessingMatchMode.EXACT, ObjectList.of());
    public static final ItemMatchingRule ID = new ItemMatchingRule(ProcessingMatchMode.ID, ObjectList.of());

    public ItemMatchingRule(ProcessingMatchMode mode, List<ResourceLocation> tags) {
        this(mode, new ObjectImmutableList<>(tags));
    }

    public ItemMatchingRule {
        Objects.requireNonNull(mode);
        tags = new ObjectImmutableList<>(tags);
        if (mode == ProcessingMatchMode.TAG ? tags.isEmpty() : !tags.isEmpty()) throw new IllegalArgumentException("Only TAG rules require nonempty recipe tags");
    }

    /** Compares non-null complete keys under this rule; quantities must be checked separately by the caller. */
    public boolean matches(AEKey expected, AEKey actual) {
        if (mode == ProcessingMatchMode.EXACT) return expected.equals(actual);
        if (!(expected instanceof AEItemKey template) || !(actual instanceof AEItemKey item)) return false;
        if (mode == ProcessingMatchMode.ID) return template.getItem() == item.getItem();
        var holder = BuiltInRegistries.ITEM.wrapAsHolder(item.getItem());
        return tags.stream().anyMatch(tag -> holder.is(TagKey.create(Registries.ITEM, tag)));
    }

    /** True when two declared domains can accept at least one common item key; quantities are not considered. */
    public boolean overlaps(AEItemKey own, ItemMatchingRule other, AEItemKey theirs) {
        if (matches(own, theirs) || other.matches(theirs, own)) return true;
        if (mode != ProcessingMatchMode.TAG || other.mode != ProcessingMatchMode.TAG) return false;
        for (var name : tags) {
            var members = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, name));
            if (members.isEmpty()) continue;
            for (var holder : members.get()) {
                if (other.tags.stream().anyMatch(tag -> holder.is(TagKey.create(Registries.ITEM, tag)))) return true;
            }
        }
        return false;
    }

    /** Returns an independent persistent snapshot; callers may add their own resource/quantity fields. */
    public CompoundTag save() {
        var data = new CompoundTag();
        data.putString("mode", mode.name());
        var names = new ListTag();
        tags.forEach(tag -> names.add(StringTag.valueOf(tag.toString())));
        data.put("tags", names);
        return data;
    }

    /** Decodes at the untrusted save/network boundary; invalid modes, names and empty TAG declarations throw. */
    public static ItemMatchingRule load(CompoundTag data) {
        var names = new ObjectArrayList<ResourceLocation>();
        for (var tag : data.getList("tags", Tag.TAG_STRING)) names.add(ResourceLocation.parse(tag.getAsString()));
        return new ItemMatchingRule(ProcessingMatchMode.valueOf(data.getString("mode")), names);
    }
}
