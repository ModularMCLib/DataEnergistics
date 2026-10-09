package com.fish_dan_.data_energistics.api.registry.digitalsupply;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.EnumSet;

/**
 * Stable identity and transfer rules for one resource exposed by a Digital Supply Interface adapter.
 *
 * <p>
 * The {@code key} is the real AE identity used for quantity transactions. The presence marker maintained by a
 * device is deliberately separate and always has amount one.
 * </p>
 */
public record DigitalSupplyResourceDefinition(ResourceLocation id,
                                              AEKey key,
                                              Component displayName,
                                              DigitalSupplyUnitConversion unitConversion,
                                              boolean presenceMarker,
                                              EnumSet<DigitalSupplyTransferDirection> directions) {

    public DigitalSupplyResourceDefinition {
        if (key.getType() == null) {
            throw new IllegalArgumentException("Digital Supply resource key must have a key type");
        }
        unitConversion = unitConversion == null ? DigitalSupplyUnitConversion.IDENTITY : unitConversion;
        if (directions.isEmpty()) {
            throw new IllegalArgumentException("Digital Supply resource must declare at least one transfer direction");
        }
        directions = EnumSet.copyOf(directions);
    }

    /** Returns the key type that owns the resource. */
    public AEKeyType keyType() {
        return key.getType();
    }

    /** Returns an immutable FastUtil view for callers that need to enumerate directions. */
    public ObjectList<DigitalSupplyTransferDirection> directionsFast() {
        ObjectArrayList<DigitalSupplyTransferDirection> result = new ObjectArrayList<>(directions);
        return ObjectLists.unmodifiable(result);
    }

    /** Returns whether this definition allows the requested transfer direction. */
    public boolean allows(DigitalSupplyTransferDirection direction) {
        return directions.contains(direction);
    }
}
