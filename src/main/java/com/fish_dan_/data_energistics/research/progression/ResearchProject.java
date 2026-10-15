package com.fish_dan_.data_energistics.research.progression;

import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.research.persistence.ResearchNbt;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.UUID;

/** Paid research with immutable rules/subject snapshot. Mutated exclusively by the server tick thread. */
public final class ResearchProject {

    public final ResearchNode node;
    public final ObjectSet<UUID> subjects;
    public final UUID initiator;
    public final long order;
    public int progress;
    public double energyCredit;
    public boolean running = true;
    public boolean powered;
    public boolean faulted;

    public ResearchProject(ResearchNode node, ObjectSet<UUID> subjects, UUID initiator, long order) {
        this.node = node;
        this.subjects = ObjectSets.unmodifiable(new ObjectOpenHashSet<>(subjects));
        this.initiator = initiator;
        this.order = order;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("node", ResearchNbt.node(node, registries));
        ListTag owners = new ListTag();
        for (UUID subject : subjects) {
            CompoundTag owner = new CompoundTag();
            owner.putUUID("id", subject);
            owners.add(owner);
        }
        tag.put("subjects", owners);
        tag.putUUID("initiator", initiator);
        tag.putLong("order", order);
        tag.putInt("progress", progress);
        tag.putDouble("energy_credit", energyCredit);
        tag.putBoolean("running", running);
        tag.putBoolean("faulted", faulted);
        tag.putBoolean("powered", powered);
        return tag;
    }

    public static ResearchProject load(CompoundTag tag, HolderLookup.Provider registries) {
        ObjectSet<UUID> subjects = new ObjectOpenHashSet<>();
        for (Tag value : ResearchNbt.readList(tag, "subjects", Tag.TAG_COMPOUND)) subjects.add(((CompoundTag) value).getUUID("id"));
        ResearchProject project = new ResearchProject(ResearchNbt.readNode(tag.getCompound("node"), registries),
                subjects, tag.getUUID("initiator"), tag.getLong("order"));
        project.progress = tag.getInt("progress");
        project.energyCredit = tag.getDouble("energy_credit");
        if (subjects.isEmpty() || project.order < 0 || project.progress < 0 || project.progress >= project.node.researchTicks() ||
                !Double.isFinite(project.energyCredit) || project.energyCredit < 0 || project.energyCredit > project.node.aePerTick()) {
            throw new IllegalArgumentException("Invalid saved research subjects, order, progress or energy credit");
        }
        project.running = tag.getBoolean("running");
        project.faulted = tag.getBoolean("faulted");
        if (project.running && project.faulted) throw new IllegalArgumentException("Faulted research cannot be running");
        return project;
    }
}
