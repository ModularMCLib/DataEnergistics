package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning.cache;

import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRules;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCanonicalNbt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import net.minecraft.core.RegistryAccess;

import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * One grid's server-thread cache for a single publication, rule/recipe epoch and registry generation.
 * Stores only structural closures and canonical key encodings; inventory membership, live recipe validation,
 * actor permissions and execution targets are deliberately evaluated by each request.
 */
public final class TrinityCaptureCache {

    private static final int TARGET_LIMIT = 64;
    private static final int PATTERN_REFERENCE_LIMIT = 16384;
    private static final int KEY_LIMIT = 8192;
    private static final int ENCODING_CHARACTER_LIMIT = 1048576;

    private final TrinityCraftingGraphSnapshot catalog;
    private final ReusableInputRules rules;
    private final long epoch;
    private final RegistryAccess registries;
    private final Object2ObjectLinkedOpenHashMap<AEKey, Seed> targets = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2BooleanOpenHashMap<TrinityPatternIdentity> ruleCandidates = new Object2BooleanOpenHashMap<>();
    private final Object2ObjectLinkedOpenHashMap<AEItemKey, String> encodings = new Object2ObjectLinkedOpenHashMap<>();
    private int patternReferences;
    private int encodingCharacters;

    public TrinityCaptureCache(TrinityCraftingGraphSnapshot catalog, ReusableInputRules rules, long epoch,
                               RegistryAccess registries) {
        this.catalog = catalog;
        this.rules = rules;
        this.epoch = epoch;
        this.registries = registries;
    }

    /** Identity checks prevent sharing values across replacement publications or registry reloads. */
    public boolean matches(TrinityCraftingGraphSnapshot catalog, ReusableInputRules rules, long epoch,
                           RegistryAccess registries) {
        return this.catalog == catalog && this.rules == rules && this.epoch == epoch && this.registries == registries;
    }

    public @Nullable Seed seed(AEKey target) {
        return this.targets.getAndMoveToLast(target);
    }

    /** Only a fully traversed, pre-validation closure may be published; no partial or inventory-specific graph. */
    public void remember(AEKey target, TrinityCraftingGraphSnapshot graph, TrinitySameItemPolicy policy) {
        int size = graph.patterns().size();
        if (size > PATTERN_REFERENCE_LIMIT || this.targets.containsKey(target)) return;
        while (this.targets.size() >= TARGET_LIMIT || this.patternReferences + size > PATTERN_REFERENCE_LIMIT) {
            this.patternReferences -= this.targets.removeFirst().graph().patterns().size();
        }
        this.targets.putAndMoveToLast(target, new Seed(graph, policy));
        this.patternReferences += size;
    }

    /** The conservative filter depends only on the frozen pattern and recipe model, never provider availability. */
    public boolean mayHaveRule(TrinityCraftingGraphPattern pattern, Predicate<TrinityCraftingGraphPattern> filter) {
        if (!this.ruleCandidates.containsKey(pattern.identity())) {
            this.ruleCandidates.put(pattern.identity(), filter.test(pattern));
        }
        return this.ruleCandidates.getBoolean(pattern.identity());
    }

    /** Exact immutable item keys include their components; changed stock states cannot hit an old encoding. */
    public String canonicalKey(AEItemKey key) {
        String cached = this.encodings.getAndMoveToLast(key);
        if (cached != null) return cached;
        String encoded = TrinityCanonicalNbt.encode(key.toTagGeneric(this.registries));
        if (encoded.length() > ENCODING_CHARACTER_LIMIT) return encoded;
        while (this.encodings.size() >= KEY_LIMIT || this.encodingCharacters + encoded.length() > ENCODING_CHARACTER_LIMIT) {
            this.encodingCharacters -= this.encodings.removeFirst().length();
        }
        this.encodings.putAndMoveToLast(key, encoded);
        this.encodingCharacters += encoded.length();
        return encoded;
    }

    /** Immutable initial closure, before inventory-dependent component or reusable-input expansion. */
    public record Seed(TrinityCraftingGraphSnapshot graph, TrinitySameItemPolicy policy) {}
}
