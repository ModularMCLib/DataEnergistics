package com.fish_dan_.data_energistics.common.multiblock.preview.catalog;

import com.fish_dan_.data_energistics.common.multiblock.preview.projection.SubstructurePreviewSpec;

import appeng.api.stacks.AEItemKey;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

/**
 * Revision-bound catalog of named preview substructures owned by one controller item.
 */
public final class MultiblockPreviewSpec {

    private final ResourceLocation controllerId;
    private final Component title;
    private final AEItemKey ownerOutput;
    private final long definitionRevision;
    private final ObjectList<SubstructurePreviewSpec> substructures;

    /**
     * Creates a stable preview catalog from active definitions at one registry revision.
     *
     * @param controllerId       machine id shared by every named definition
     * @param title              player-facing controller title
     * @param ownerOutput        immutable controller or owner item identity
     * @param definitionRevision non-negative active definition revision
     * @param substructures      ordered non-empty named definitions
     */
    public MultiblockPreviewSpec(ResourceLocation controllerId,
                                 Component title,
                                 AEItemKey ownerOutput,
                                 long definitionRevision,
                                 ObjectList<SubstructurePreviewSpec> substructures) {
        if (controllerId == null || title == null || ownerOutput == null || substructures == null) {
            throw new IllegalArgumentException("Multiblock preview spec arguments cannot be null");
        }
        if (definitionRevision < 0L) {
            throw new IllegalArgumentException("Multiblock preview definition revision cannot be negative: " +
                    definitionRevision);
        }
        if (substructures.isEmpty()) {
            throw new IllegalArgumentException("Multiblock preview spec requires at least one substructure");
        }
        this.controllerId = controllerId;
        this.title = title.copy();
        this.ownerOutput = ownerOutput;
        this.definitionRevision = definitionRevision;
        this.substructures = copySubstructures(controllerId, substructures);
    }

    /**
     * Returns the machine id shared by this preview catalog.
     */
    public ResourceLocation controllerId() {
        return this.controllerId;
    }

    /**
     * Returns a detached title so callers cannot mutate catalog-owned text.
     */
    public Component title() {
        return this.title.copy();
    }

    /**
     * Returns the immutable controller or owner output identity.
     */
    public AEItemKey ownerOutput() {
        return this.ownerOutput;
    }

    /**
     * Returns the active definition revision bound to this catalog.
     */
    public long definitionRevision() {
        return this.definitionRevision;
    }

    /**
     * Returns named substructures in stable presentation order.
     */
    public ObjectList<SubstructurePreviewSpec> substructures() {
        return this.substructures;
    }

    /**
     * Resolves one named substructure without relying on list indexes.
     *
     * @param id stable structure name
     * @return matching substructure definition
     */
    public SubstructurePreviewSpec substructure(String id) {
        for (SubstructurePreviewSpec substructure : this.substructures) {
            if (substructure.id().equals(id)) {
                return substructure;
            }
        }
        throw new IllegalArgumentException("Unknown multiblock preview substructure: " + id);
    }

    private static ObjectList<SubstructurePreviewSpec> copySubstructures(
                                                                         ResourceLocation controllerId,
                                                                         ObjectList<SubstructurePreviewSpec> substructures) {
        ObjectList<SubstructurePreviewSpec> copy = new ObjectArrayList<>(substructures);
        ObjectSet<String> ids = new ObjectOpenHashSet<>();
        for (SubstructurePreviewSpec substructure : copy) {
            if (substructure == null) {
                throw new IllegalArgumentException("Multiblock preview substructures cannot contain null");
            }
            if (!controllerId.equals(substructure.definition().key().machineId())) {
                throw new IllegalArgumentException("Substructure " + substructure.id() + " belongs to " +
                        substructure.definition().key().machineId() + ", expected " + controllerId);
            }
            if (!ids.add(substructure.id())) {
                throw new IllegalArgumentException("Duplicate multiblock preview substructure: " + substructure.id());
            }
        }
        return ObjectLists.unmodifiable(copy);
    }
}
