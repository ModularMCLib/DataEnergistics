package com.fish_dan_.data_energistics.research.progression;

import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.research.persistence.ResearchNbt;

import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

/** Shared UUID-owned inventory/projects. Physical copies have one tick budget and one target item. */
public final class ResearchStationState {

    public ItemStack target = ItemStack.EMPTY;
    public final Object2ObjectMap<ResourceLocation, ResearchProject> projects = new Object2ObjectLinkedOpenHashMap<>();
    public final Object2LongMap<AEKey> refunds = new Object2LongLinkedOpenHashMap<>();
    public long revision;
    public long nextOrder;
    public long lastTick = Long.MIN_VALUE;

    public boolean locked() {
        return projects.values().stream().anyMatch(project -> project.node.scope() == ResearchScope.ITEM_UPGRADE);
    }

    public long runningCount() {
        return projects.values().stream().filter(project -> project.running).count();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!target.isEmpty()) tag.put("target", target.save(registries));
        tag.putLong("revision", revision);
        tag.putLong("next_order", nextOrder);
        ListTag work = new ListTag();
        projects.values().forEach(project -> work.add(project.save(registries)));
        tag.put("projects", work);
        ListTag pending = new ListTag();
        refunds.object2LongEntrySet().forEach(entry -> pending.add(ResearchNbt.input(new ResearchInput(entry.getKey(), entry.getLongValue()), registries)));
        tag.put("refunds", pending);
        return tag;
    }

    public static ResearchStationState load(CompoundTag tag, HolderLookup.Provider registries) {
        ResearchStationState state = new ResearchStationState();
        state.target = tag.contains("target") ? ItemStack.parse(registries, tag.getCompound("target")).orElseThrow() : ItemStack.EMPTY;
        if (state.target.getCount() > 1) throw new IllegalArgumentException("Research target must be a single item");
        state.revision = tag.getLong("revision");
        state.nextOrder = tag.getLong("next_order");
        for (Tag value : tag.getList("projects", Tag.TAG_COMPOUND)) {
            ResearchProject project = ResearchProject.load((CompoundTag) value, registries);
            if (state.projects.putIfAbsent(project.node.id(), project) != null) throw new IllegalArgumentException("Duplicate saved research project");
        }
        if (state.runningCount() > 2 || (state.locked() && state.target.isEmpty())) throw new IllegalArgumentException("Invalid research concurrency or target");
        for (Tag value : tag.getList("refunds", Tag.TAG_COMPOUND)) {
            ResearchInput input = ResearchNbt.readInput((CompoundTag) value, registries);
            state.refunds.mergeLong(input.key(), input.amount(), Math::addExact);
        }
        return state;
    }
}
