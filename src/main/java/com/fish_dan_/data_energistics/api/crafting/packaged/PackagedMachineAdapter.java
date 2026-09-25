package com.fish_dan_.data_energistics.api.crafting.packaged;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/**
 * Automation of a real crafting machine, shared by adjacent and adaptive remote providers.
 * All callbacks run on the server thread. Discovery and preparation are read-only; only advance may mutate
 * the world. Implementations are stateless; persisted execution data belongs to the supplied operation.
 * No callback may load a chunk or synthesize the pattern's declared output in place of running the machine.
 */
public interface PackagedMachineAdapter {

    /** Stable machine-family ID, also used for independent dispatch policies and persistent operation lookup. */
    ResourceLocation id();

    /** Immutable JEI/EMI category IDs accepted by this adapter; they need not be registry RecipeType IDs. */
    ObjectSet<ResourceLocation> recipeTypes();

    /**
     * Immutable item registry IDs for physical workstations represented by this adapter.
     * The IDs identify the workstation item, rather than its block entity or recipe category.
     * Adapters that do not expose a viewer workstation use the empty set.
     */
    default ObjectSet<ResourceLocation> workstationItemIds() {
        return ObjectSet.of();
    }

    /**
     * Adds countable activation materials and real returned containers before a processing pattern is committed.
     * Called on the server with the exact viewer recipe ID. The input stack is not modified; return it unchanged
     * when this machine needs no augmentation, or null when the referenced recipe cannot be encoded correctly.
     * Implementations must resolve the ID directly, never infer a recipe from its declared output.
     */
    default @Nullable ItemStack completeEncoding(ServerLevel level, ResourceLocation recipeId, ItemStack encodedPattern) {
        return encodedPattern;
    }

    /** Identifies a loaded main machine block without requiring an exposed inventory or touching its state. */
    boolean recognizes(ServerLevel level, BlockPos position);

    /**
     * Reads the maximum currently safe logical batch for one concrete machine, bounded by requestedCount.
     * Prototype counters and pattern describe one logical craft and must not be mutated. Called only for
     * loaded, recognized, unclaimed machines on the server thread. Return zero when unavailable; the default
     * preserves single-craft adapters. Opted-in adapters must accept the scaled inputs and output quantities
     * in prepare and physically process them without bypassing native energy, timing or output accounting.
     * Capacity must be based on real machine storage or native item/entity processing limits, not an unbounded
     * provider queue. No world references or reservations may be retained from this read-only observation.
     */
    default long batchCapacity(ServerLevel level, BlockPos position, Direction face, ResourceLocation recipeId,
                               IPatternDetails pattern, KeyCounter[] prototype, long requestedCount) {
        return 1;
    }

    /**
     * Opts into durable CPU-supplied reusable inputs. A separately registered reusable rule must identify
     * each retained slot with an UNCHANGED rule. Each native cycle must physically return those exact assets through
     * the operation;
     * the dispatcher holds them between cycles and settles them to their CPU owner only once.
     * Called read-only on the server thread; ordinary adapters retain their counted-input behavior.
     */
    default boolean supportsReusableInputs() {
        return false;
    }

    /**
     * Resolves the exact encoded recipe and checks the complete live structure, ingredients and outputs.
     * Returns durable, adapter-specific preparation data or null when this target cannot currently accept.
     * The supplied input counters are read-only. Every referenced structure position must already be loaded.
     */
    @Nullable
    CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                        ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs);

    /**
     * Lists the main block and shared physical input/output parts reserved by this preparation. Called read-only
     * immediately after prepare on the same server thread; every position must be loaded. The dispatcher acquires
     * the complete set atomically and persists it, preventing overlapping machines from sharing a pedestal.
     */
    default ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        return ObjectList.of(position);
    }

    /**
     * Performs a bounded amount of real machine work, recording delivered inputs and harvested outputs through
     * the operation. Returning true means progress, not necessarily completion. Missing or busy world state
     * leaves the operation pending; adapter exceptions stop that operation at the provider boundary.
     */
    boolean advance(PackagedMachineOperation operation);

    /**
     * Recovers physical assets after dismantling, on the server thread. Return false while recovery
     * requires loaded world state; only return true after all recoverable assets have been transferred.
     * The dispatcher then returns undelivered inputs. Never return copies of assets still in the world.
     */
    default boolean recoverRemoved(PackagedMachineOperation operation) {
        return true;
    }
}
