package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import net.minecraft.world.item.Item;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.function.Consumer;

/** Request-local producer expansion over the catalog's complete immutable item index. Server-thread confined. */
final class TrinityCaptureProducers {

    private final TrinityCraftingGraphSnapshot catalog;
    private final TrinitySameItemPolicy policy;
    private final ObjectOpenHashSet<Item> expandedItems = new ObjectOpenHashSet<>();
    private final ObjectOpenHashSet<AEKey> expandedExactKeys = new ObjectOpenHashSet<>();
    private final Object2ObjectOpenHashMap<AEKey, ObjectLinkedOpenHashSet<Item>> pendingDomains = new Object2ObjectOpenHashMap<>();

    TrinityCaptureProducers(TrinityCraftingGraphSnapshot catalog, TrinitySameItemPolicy policy) {
        this.catalog = catalog;
        this.policy = policy;
        if (!policy.isEmpty()) {
            ObjectOpenHashSet<Item> indexedItems = new ObjectOpenHashSet<>();
            for (AEKey key : catalog.keys()) {
                if (key instanceof AEItemKey item && indexedItems.add(item.getItem()) && policy.allowsSameItem(key)) {
                    this.pendingDomains.computeIfAbsent(policy.normalizeKey(key), ignored -> new ObjectLinkedOpenHashSet<>())
                            .add(item.getItem());
                }
            }
        }
    }

    /**
     * Expands each component candidate pool once, even when thousands of exact component keys refer to it.
     * Domain membership only selects candidates; later recipe and reusable-rule capture still validates exact keys.
     */
    void include(AEKey key, boolean componentCandidates, Consumer<TrinityCraftingGraphPattern> select) {
        if (componentCandidates && this.policy.allowsSameItem(key)) {
            var items = this.pendingDomains.remove(this.policy.normalizeKey(key));
            if (items != null) {
                for (Item item : items) includeItem(item, select);
            }
        } else if (componentCandidates && key instanceof AEItemKey item) {
            includeItem(item.getItem(), select);
        } else if (this.expandedExactKeys.add(key)) {
            this.catalog.patternsProducing(key).forEach(select);
        }
    }

    private void includeItem(Item item, Consumer<TrinityCraftingGraphPattern> select) {
        if (this.expandedItems.add(item)) {
            // This index also retains producers of published remainders and reusable byproducts.
            this.catalog.captureProducersForItem(item).forEach(select);
        }
    }
}
