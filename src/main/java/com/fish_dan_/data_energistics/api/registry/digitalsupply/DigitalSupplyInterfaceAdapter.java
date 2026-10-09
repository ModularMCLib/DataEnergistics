package com.fish_dan_.data_energistics.api.registry.digitalsupply;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/**
 * Public, optional-Mod-neutral behavior contract for one digital-supply integration.
 *
 * <p>
 * Implementations must only depend on this API and the target mod's public API. The block entity owns lifecycle,
 * persistence and AE storage; an adapter owns recognition, native links and native-side rules.
 * </p>
 */
public interface DigitalSupplyInterfaceAdapter {

    ResourceLocation id();

    ObjectList<DigitalSupplyResourceDefinition> resources();

    /** Lower values are evaluated first when several adapters recognize one target. */
    default int priority() {
        return 0;
    }

    /** Returns whether this adapter can operate at the current target. */
    default boolean supports(DigitalSupplyInterfaceTarget target) {
        return true;
    }

    /** Discovers native resources and updates type-presence markers. */
    default void discover(DigitalSupplyInterfaceTarget target) {}

    /** Updates or validates native links before the transfer tick. */
    default void updateLinks(DigitalSupplyInterfaceTarget target) {}

    /** Performs simulation and commit operations for one server tick. */
    default void tick(DigitalSupplyInterfaceTarget target, DigitalSupplyTransferContext transfer) {}

    /** Saves adapter-owned cursors and link state without storing consumable quantities. */
    default void saveState(CompoundTag tag) {}

    /** Restores adapter-owned state; malformed adapter state must throw at this boundary. */
    default void loadState(CompoundTag tag) {}

    /** Called when the target is removed or permanently unloaded. */
    default void detach(DigitalSupplyInterfaceTarget target) {}

    /** Returns a resource declaration by stable ID, if this adapter owns it. */
    default @Nullable DigitalSupplyResourceDefinition resource(ResourceLocation resourceId) {
        for (DigitalSupplyResourceDefinition resource : resources()) {
            if (resource.id().equals(resourceId)) {
                return resource;
            }
        }
        return null;
    }
}
