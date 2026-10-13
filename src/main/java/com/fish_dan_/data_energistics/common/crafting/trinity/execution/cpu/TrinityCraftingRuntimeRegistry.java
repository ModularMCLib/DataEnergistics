package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.networking.IGridNode;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

/**
 * Synchronous Trinity runtime publication contract exposed by one AE2 crafting service.
 *
 * <p>
 * Publications change service-visible CPU membership immediately. The caller separately posts AE2's CPU-change event
 * as a cache-invalidation notification after publishing or withdrawing.
 * </p>
 */
public interface TrinityCraftingRuntimeRegistry {

    /**
     * Publishes one node-runtime pair. Repeating the same identities is idempotent; replacing a runtime requires an
     * explicit {@link #data_energistics$withdraw(IGridNode)} first.
     *
     * @return whether node membership changed
     */
    boolean data_energistics$publish(IGridNode node, TrinityDataCoreCraftingRuntime runtime);

    /**
     * Withdraws the runtime published by the exact node identity.
     *
     * @return whether node membership changed
     */
    boolean data_energistics$withdraw(IGridNode node);

    /**
     * Creates isolated membership state for one crafting service instance.
     */
    static Local createLocal() {
        return new LocalTrinityCraftingRuntimeRegistry();
    }

    /**
     * Internal view used by the crafting-service mixin to consume and reconcile its own publications.
     */
    interface Local extends TrinityCraftingRuntimeRegistry {

        /**
         * Returns the immutable snapshot, deduplicated by runtime identity.
         */
        ObjectList<TrinityDataCoreCraftingRuntime> snapshot();

        /**
         * Atomically replaces node publications from one complete AE2 machine scan.
         */
        ObjectList<TrinityDataCoreCraftingRuntime> reconcile(
                                                             Reference2ReferenceMap<IGridNode, TrinityDataCoreCraftingRuntime> scannedRegistrations);
    }
}

/**
 * Identity-based runtime membership owned by exactly one crafting service.
 */
final class LocalTrinityCraftingRuntimeRegistry implements TrinityCraftingRuntimeRegistry.Local {

    /**
     * Exact access-node publications; node equality must never merge distinct AE2 nodes.
     */
    private final Reference2ReferenceMap<IGridNode, TrinityDataCoreCraftingRuntime> registrations = new Reference2ReferenceOpenHashMap<>();

    /**
     * Immutable service-visible runtime membership replaced only after a complete mutation succeeds.
     */
    private volatile ObjectList<TrinityDataCoreCraftingRuntime> snapshot = ObjectList.of();

    @Override
    public synchronized boolean data_energistics$publish(IGridNode node, TrinityDataCoreCraftingRuntime runtime) {
        if (this.registrations.containsKey(node)) {
            TrinityDataCoreCraftingRuntime current = this.registrations.get(node);
            if (current == runtime) {
                return false;
            }
            Data_Energistics.LOGGER.error(
                    "Refusing to replace Trinity crafting runtime {} with {} for grid node {} without withdrawal",
                    identity(current),
                    identity(runtime),
                    identity(node));
            throw new IllegalStateException("A different Trinity crafting runtime is already published for this node");
        }

        Reference2ReferenceMap<IGridNode, TrinityDataCoreCraftingRuntime> replacements = new Reference2ReferenceOpenHashMap<>(this.registrations);
        replacements.put(node, runtime);
        commitRegistrations(replacements);
        return true;
    }

    @Override
    public synchronized boolean data_energistics$withdraw(IGridNode node) {
        if (!this.registrations.containsKey(node)) {
            return false;
        }
        Reference2ReferenceMap<IGridNode, TrinityDataCoreCraftingRuntime> replacements = new Reference2ReferenceOpenHashMap<>(this.registrations);
        replacements.remove(node);
        commitRegistrations(replacements);
        return true;
    }

    @Override
    public ObjectList<TrinityDataCoreCraftingRuntime> snapshot() {
        return this.snapshot;
    }

    @Override
    public synchronized ObjectList<TrinityDataCoreCraftingRuntime> reconcile(
                                                                             Reference2ReferenceMap<IGridNode, TrinityDataCoreCraftingRuntime> scannedRegistrations) {
        Reference2ReferenceMap<IGridNode, TrinityDataCoreCraftingRuntime> replacements = new Reference2ReferenceOpenHashMap<>();
        replacements.putAll(scannedRegistrations);
        return commitRegistrations(replacements);
    }

    /**
     * Builds the complete immutable view before changing live identity registrations.
     */
    private ObjectList<TrinityDataCoreCraftingRuntime> commitRegistrations(
                                                                           Reference2ReferenceMap<IGridNode, TrinityDataCoreCraftingRuntime> replacements) {
        ObjectList<TrinityDataCoreCraftingRuntime> replacementSnapshot = createSnapshot(replacements.values(), this.snapshot);
        this.registrations.clear();
        this.registrations.putAll(replacements);
        this.snapshot = replacementSnapshot;
        return replacementSnapshot;
    }

    private static ObjectList<TrinityDataCoreCraftingRuntime> createSnapshot(
                                                                             Iterable<TrinityDataCoreCraftingRuntime> registrations,
                                                                             ObjectList<TrinityDataCoreCraftingRuntime> previousSnapshot) {
        ReferenceOpenHashSet<TrinityDataCoreCraftingRuntime> present = new ReferenceOpenHashSet<>();
        for (TrinityDataCoreCraftingRuntime runtime : registrations) {
            present.add(runtime);
        }
        ReferenceOpenHashSet<TrinityDataCoreCraftingRuntime> seen = new ReferenceOpenHashSet<>();
        ObjectList<TrinityDataCoreCraftingRuntime> runtimes = new ObjectArrayList<>();
        for (TrinityDataCoreCraftingRuntime runtime : previousSnapshot) {
            if (present.contains(runtime)) {
                seen.add(runtime);
                runtimes.add(runtime);
            }
        }
        for (TrinityDataCoreCraftingRuntime runtime : registrations) {
            if (!seen.contains(runtime)) {
                seen.add(runtime);
                runtimes.add(runtime);
            }
        }
        return FastUtilCollections.immutableList(runtimes);
    }

    private static String identity(Object value) {
        return value.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(value));
    }
}
