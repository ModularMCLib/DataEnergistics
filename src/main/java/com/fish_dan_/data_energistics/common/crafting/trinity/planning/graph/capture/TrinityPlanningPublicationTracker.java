package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.capture;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

/**
 * Server-thread model generation, reconciled after a complete synchronous provider refresh.
 * Mount/unmount still invalidate dispatch identities immediately. Planning observes only the final pattern
 * semantics of touched providers, so an empty or equivalent refresh cannot starve a time-sliced capture.
 * Live references are grid-local and are released after removed providers have been reconciled.
 */
public final class TrinityPlanningPublicationTracker {

    private final Reference2ObjectOpenHashMap<ICraftingProvider, Object2ObjectMap<CraftingProviderId, ObjectList<IPatternDetails>>> mounted = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<ICraftingProvider, ObjectSet<PatternModel>> observed = new Reference2ObjectOpenHashMap<>();
    private final ReferenceOpenHashSet<ICraftingProvider> dirty = new ReferenceOpenHashSet<>();
    private long revision;

    /** Records an immutable list from one successful mount; never inspects third-party recipe code here. */
    public void publish(CraftingProviderId id, ICraftingProvider provider, ObjectList<IPatternDetails> patterns) {
        this.mounted.computeIfAbsent(provider, ignored -> new Object2ObjectOpenHashMap<>()).put(id, patterns);
        this.dirty.add(provider);
    }

    /** Removes the exact mount already validated by the publication index. */
    public void unpublish(CraftingProviderId id, ICraftingProvider provider) {
        var publications = this.mounted.getOrDefault(provider, Object2ObjectMaps.emptyMap());
        if (!publications.containsKey(id)) {
            throw new IllegalStateException("Planning publication is not current: " + id);
        }
        publications.remove(id);
        if (publications.isEmpty()) this.mounted.remove(provider);
        this.dirty.add(provider);
    }

    /**
     * Reads the settled model at a server-thread planning boundary, outside provider mount/unmount callbacks.
     * Compares full immutable signatures, not only hashes. A changed provider identity also invalidates its
     * non-empty model, since it can introduce different reusable execution targets. Signature failures propagate
     * without accepting a partial baseline; the graph capture boundary owns error reporting.
     */
    public long revision() {
        if (this.dirty.isEmpty()) return this.revision;
        Reference2ObjectOpenHashMap<ICraftingProvider, ObjectSet<PatternModel>> next = new Reference2ObjectOpenHashMap<>();
        boolean changed = false;
        for (ICraftingProvider provider : this.dirty) {
            ObjectSet<PatternModel> models = new ObjectOpenHashSet<>();
            var publications = this.mounted.getOrDefault(provider, Object2ObjectMaps.emptyMap());
            for (var patterns : publications.values()) {
                for (IPatternDetails pattern : patterns) {
                    models.add(new PatternModel(pattern.getClass(), TrinityPatternPublicationSignature.capture(pattern)));
                }
            }
            changed |= !this.observed.getOrDefault(provider, ObjectSets.emptySet()).equals(models);
            next.put(provider, models);
        }
        if (changed) this.revision = Math.incrementExact(this.revision);
        next.forEach((provider, models) -> {
            if (models.isEmpty()) this.observed.remove(provider);
            else this.observed.put(provider, models);
        });
        this.dirty.clear();
        return this.revision;
    }

    private record PatternModel(Class<? extends IPatternDetails> patternType,
                                TrinityPatternPublicationSignature signature) {}
}
