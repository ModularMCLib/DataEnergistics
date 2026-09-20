package com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem;

import com.fish_dan_.data_energistics.api.crafting.matching.ItemMatchingRule;
import com.fish_dan_.data_energistics.api.crafting.matching.ProcessingMatchMode;
import com.fish_dan_.data_energistics.common.crafting.pattern.matching.EncodedPatternMatching;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable request-local equivalence policy for explicitly authorised processing input/output item domains.
 *
 * <p>
 * The policy never rewrites an {@link AEItemKey} or its backing {@code ItemStack}. It selects one existing exact key
 * as the accounting representative for each authorised registered item, while pattern identities and physical
 * inputs and outputs retain their complete data components.
 * </p>
 */
public final class TrinitySameItemPolicy {

    private static final TrinitySameItemPolicy EMPTY = new TrinitySameItemPolicy(Map.of());

    private final Map<Item, AEItemKey> representativesByItem;
    private final Set<AEItemKey> representatives;

    private TrinitySameItemPolicy(Map<Item, AEItemKey> representatives) {
        Object2ObjectLinkedOpenHashMap<Item, AEItemKey> copied = new Object2ObjectLinkedOpenHashMap<>();
        ObjectLinkedOpenHashSet<AEItemKey> keys = new ObjectLinkedOpenHashSet<>();
        for (var entry : representatives.entrySet()) {
            AEItemKey representative = entry.getValue();
            copied.put(entry.getKey(), representative);
            keys.add(representative);
        }
        this.representativesByItem = Object2ObjectMaps.unmodifiable(copied);
        this.representatives = ObjectSets.unmodifiable(keys);
    }

    /** Returns the exact-only policy used by unmarked graphs and legacy saved jobs. */
    public static TrinitySameItemPolicy empty() {
        return EMPTY;
    }

    /**
     * Captures all marker-authorised items from the complete graph and chooses deterministic request representatives.
     * The exact requested key is preferred for its own item so final-output accounting keeps the requested key.
     */
    public static TrinitySameItemPolicy fromGraph(TrinityCraftingGraphSnapshot graph, AEKey target) {
        var domains = new ObjectArrayList<Domain>();
        var outputRepresentatives = new Object2ObjectLinkedOpenHashMap<Item, AEItemKey>();
        var exact = new ObjectLinkedOpenHashSet<Item>();
        for (TrinityCraftingGraphPattern pattern : graph.patterns()) {
            var definition = pattern.definition();
            var encoded = definition.get(AEComponents.ENCODED_PROCESSING_PATTERN);
            if (encoded != null) {
                for (int slot = 0; slot < encoded.sparseInputs().size(); slot++) {
                    var input = encoded.sparseInputs().get(slot);
                    if (input != null && input.what() instanceof AEItemKey key) addDomain(domains, exact, key,
                            EncodedPatternMatching.mode(definition, slot), EncodedPatternMatching.tags(definition, slot), false,
                            outputRepresentatives);
                }
                for (int slot = 0; slot < encoded.sparseOutputs().size(); slot++) {
                    var output = encoded.sparseOutputs().get(slot);
                    if (output != null && output.what() instanceof AEItemKey key) addDomain(domains, exact, key,
                            EncodedPatternMatching.outputMode(definition, slot), EncodedPatternMatching.outputTags(definition, slot), true,
                            outputRepresentatives);
                }
            } else {
                for (var input : pattern.inputs()) for (var alternative : input.alternatives()) {
                    if (alternative.stack().what() instanceof AEItemKey key) exact.add(key.getItem());
                }
                for (var output : pattern.outputs()) if (output.what() instanceof AEItemKey key) exact.add(key.getItem());
            }
            for (List<TrinityBoundPatternInput> assignment : pattern.reusableBindings()) {
                for (TrinityBoundPatternInput binding : assignment) {
                    if (binding.reusableRule() != null) {
                        exact.add(binding.reusableRule().initialKey().getItem());
                        if (binding.remainingKey() instanceof AEItemKey successor) {
                            exact.add(successor.getItem());
                        }
                    }
                }
            }
        }
        var representatives = new Object2ObjectLinkedOpenHashMap<Item, AEItemKey>();
        for (var domain : domains) {
            if (domain.items().stream().anyMatch(exact::contains)) continue;
            // Overlapping different domains are not transitive permission to widen either slot's rule.
            if (domains.stream().anyMatch(other -> !other.items().equals(domain.items()) &&
                    other.items().stream().anyMatch(domain.items()::contains)))
                continue;
            for (var item : domain.items()) {
                AEItemKey representative = target instanceof AEItemKey targetItem && item.equals(targetItem.getItem())
                        ? targetItem
                        : outputRepresentatives.getOrDefault(item, domain.representative());
                representatives.putIfAbsent(item, representative);
            }
        }
        return representatives.isEmpty() ? EMPTY : new TrinitySameItemPolicy(representatives);
    }

    private static void addDomain(ObjectList<Domain> domains, ObjectSet<Item> exact, AEItemKey key,
                                  ProcessingMatchMode mode, List<ResourceLocation> tags, boolean output,
                                  Object2ObjectLinkedOpenHashMap<Item, AEItemKey> outputRepresentatives) {
        if (mode == ProcessingMatchMode.EXACT) {
            exact.add(key.getItem());
            return;
        }
        var members = new ObjectLinkedOpenHashSet<Item>();
        members.add(key.getItem());
        if (mode == ProcessingMatchMode.TAG) {
            if (tags.isEmpty()) throw new IllegalArgumentException("TAG planning rule has no declared tags");
            if (!new ItemMatchingRule(mode, tags).matches(key, key)) return;
            for (var name : tags) BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, name))
                    .ifPresent(values -> values.forEach(holder -> members.add(holder.value())));
        }
        if (output) outputRepresentatives.putIfAbsent(key.getItem(), key);
        domains.add(new Domain(key, ObjectSets.unmodifiable(members)));
    }

    private record Domain(AEItemKey representative, ObjectSet<Item> items) {}

    /** Excludes tool state domains from component-insensitive accounting without modifying physical keys. */
    public TrinitySameItemPolicy preservingExactItems(Collection<AEItemKey> exactItems) {
        if (isEmpty() || exactItems.isEmpty()) {
            return this;
        }
        Object2ObjectLinkedOpenHashMap<Item, AEItemKey> retained = new Object2ObjectLinkedOpenHashMap<>(this.representativesByItem);
        var blocked = new ObjectLinkedOpenHashSet<AEItemKey>();
        exactItems.forEach(key -> {
            var representative = retained.get(key.getItem());
            if (representative != null) blocked.add(representative);
        });
        retained.entrySet().removeIf(entry -> blocked.contains(entry.getValue()));
        return retained.size() == this.representativesByItem.size() ? this : new TrinitySameItemPolicy(retained);
    }

    /** Reconstructs a persisted policy from one representative for each authorised item. */
    public static TrinitySameItemPolicy ofRepresentatives(Collection<AEItemKey> representatives) {
        if (representatives.isEmpty()) {
            return EMPTY;
        }
        var values = new Object2ObjectLinkedOpenHashMap<Item, AEItemKey>();
        for (var representative : representatives) values.put(representative.getItem(), representative);
        return new TrinitySameItemPolicy(values);
    }

    /** Saves the explicit item membership snapshot so tag domains survive job reloads without broadening. */
    public ListTag save(HolderLookup.Provider registries) {
        var result = new ListTag();
        representativesByItem.forEach((item, representative) -> {
            var entry = new CompoundTag();
            entry.putString("item", BuiltInRegistries.ITEM.getKey(item).toString());
            entry.put("representative", representative.toTagGeneric(registries));
            result.add(entry);
        });
        return result;
    }

    public static TrinitySameItemPolicy load(ListTag entries, HolderLookup.Provider registries) {
        var result = new Object2ObjectLinkedOpenHashMap<Item, AEItemKey>();
        for (var value : entries) {
            if (!(value instanceof CompoundTag entry)) throw new IllegalArgumentException("Invalid item-domain policy entry");
            var id = ResourceLocation.parse(entry.getString("item"));
            var key = AEKey.fromTagGeneric(registries, entry.getCompound("representative"));
            if (!BuiltInRegistries.ITEM.containsKey(id) || !(key instanceof AEItemKey representative) ||
                    result.putIfAbsent(BuiltInRegistries.ITEM.get(id), representative) != null) {
                throw new IllegalArgumentException("Invalid or duplicate item-domain policy member");
            }
        }
        for (var representative : result.values()) if (!representative.equals(result.get(representative.getItem()))) {
            throw new IllegalArgumentException("Item-domain representative does not belong to its domain");
        }
        return result.isEmpty() ? EMPTY : new TrinitySameItemPolicy(result);
    }

    /** Returns whether this exact key belongs to an explicitly authorised registered-item domain. */
    public boolean allowsSameItem(AEKey key) {
        return key instanceof AEItemKey itemKey && this.representativesByItem.containsKey(itemKey.getItem());
    }

    /** Returns the request-local logical accounting key without modifying the supplied exact key. */
    public AEKey normalizeKey(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            AEItemKey representative = this.representativesByItem.get(itemKey.getItem());
            if (representative != null) {
                return representative;
            }
        }
        return key;
    }

    /** Merges signed exact-key amounts into their logical domains exactly once and removes zero balances. */
    public Map<AEKey, BigInteger> normalizeAmounts(Map<AEKey, BigInteger> amounts) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> normalized = new Object2ObjectLinkedOpenHashMap<>();
        amounts.forEach((key, amount) -> normalized.merge(normalizeKey(key), amount, BigInteger::add));
        normalized.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Object2ObjectMaps.unmodifiable(normalized);
    }

    /** Merges positive stack amounts by logical accounting key without changing any source stack. */
    public ObjectList<GenericStack> normalizeStacks(List<GenericStack> stacks) {
        if (isEmpty()) {
            return new ObjectImmutableList<>(stacks);
        }
        Object2LongLinkedOpenHashMap<AEKey> normalized = new Object2LongLinkedOpenHashMap<>();
        for (GenericStack stack : stacks) {
            normalized.mergeLong(normalizeKey(stack.what()), stack.amount(), Math::addExact);
        }
        ObjectArrayList<GenericStack> result = new ObjectArrayList<>(normalized.size());
        normalized.object2LongEntrySet().forEach(
                entry -> result.add(new GenericStack(entry.getKey(), entry.getLongValue())));
        return new ObjectImmutableList<>(result);
    }

    /** Returns one stable representative per authorised registered item. */
    public Set<AEItemKey> representatives() {
        return this.representatives;
    }

    /** Returns normalized keys in deterministic first-occurrence order. */
    public ObjectList<AEKey> normalizeKeys(Collection<AEKey> keys) {
        ObjectLinkedOpenHashSet<AEKey> normalized = new ObjectLinkedOpenHashSet<>();
        keys.forEach(key -> normalized.add(normalizeKey(key)));
        return new ObjectImmutableList<>(normalized);
    }

    /** Returns whether no item domain is authorised. */
    public boolean isEmpty() {
        return this.representativesByItem.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof TrinitySameItemPolicy policy &&
                this.representativesByItem.equals(policy.representativesByItem);
    }

    @Override
    public int hashCode() {
        return this.representativesByItem.hashCode();
    }

    @Override
    public String toString() {
        return "TrinitySameItemPolicy" + this.representatives;
    }
}
