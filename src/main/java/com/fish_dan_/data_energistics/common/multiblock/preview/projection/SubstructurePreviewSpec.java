package com.fish_dan_.data_energistics.common.multiblock.preview.projection;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewTierDomain;

import net.minecraft.network.chat.Component;

import com.modularmc.mdl.api.multiblock.PatternLayout;
import com.modularmc.mdl.api.multiblock.PatternUnit;
import com.modularmc.mdl.api.multiblock.RepeatRange;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.stream.IntStream;

/**
 * Immutable preview definition for one named MDLib-backed substructure.
 */
public final class SubstructurePreviewSpec {

    private final JsonMultiBlockDefinition definition;
    private final ObjectList<JsonMultiBlockDefinition> variants;
    private final Component title;
    private final ObjectList<PreviewTierDomain> tierDomains;
    private final ObjectList<ObjectList<RepeatRange>> repeatRangesByVariant;
    private final SubstructureSelection defaults;

    /**
     * Binds business tier metadata and validated defaults to one active JSON multiblock definition.
     *
     * @param definition  active structure definition
     * @param title       player-facing substructure title
     * @param tierDomains ordered independent tier categories
     * @param defaults    initial repeat, tier, and candidate choices
     */
    public SubstructurePreviewSpec(JsonMultiBlockDefinition definition,
                                   Component title,
                                   ObjectList<PreviewTierDomain> tierDomains,
                                   SubstructureSelection defaults) {
        this(singleVariant(definition), title, tierDomains, defaults);
    }

    /**
     * Binds an ordered shape-variant domain and business metadata to one stable named structure.
     *
     * @param variants    ordered non-empty definitions sharing one structure key
     * @param title       player-facing substructure title
     * @param tierDomains ordered independent tier categories
     * @param defaults    initial variant, repeat, tier, and candidate choices
     */
    public SubstructurePreviewSpec(ObjectList<JsonMultiBlockDefinition> variants,
                                   Component title,
                                   ObjectList<PreviewTierDomain> tierDomains,
                                   SubstructureSelection defaults) {
        if (variants == null || title == null || tierDomains == null || defaults == null) {
            throw new IllegalArgumentException("Substructure preview spec arguments cannot be null");
        }
        this.variants = copyVariants(variants);
        this.definition = this.variants.getFirst();
        this.title = title.copy();
        this.tierDomains = copyTierDomains(tierDomains);
        ObjectArrayList<ObjectList<RepeatRange>> repeatRangesByVariant = new ObjectArrayList<>(this.variants.size());
        for (JsonMultiBlockDefinition variant : this.variants) {
            ObjectArrayList<RepeatRange> repeatRanges = new ObjectArrayList<>();
            for (PatternUnit unit : variant.pattern().getLayout().units()) {
                repeatRanges.add(unit.repeats());
            }
            repeatRangesByVariant.add(ObjectLists.unmodifiable(repeatRanges));
        }
        this.repeatRangesByVariant = ObjectLists.unmodifiable(repeatRangesByVariant);
        this.defaults = validateSelection(defaults);
    }

    /**
     * Returns the stable structure name declared by the JSON definition key.
     */
    public String id() {
        return this.definition.key().structureName();
    }

    /**
     * Returns the default variant definition for callers written against the former single-shape contract.
     */
    public JsonMultiBlockDefinition definition() {
        return definition(this.defaults.variantIndex());
    }

    /**
     * Returns ordered definitions forming the legal zero-based shape-variant domain.
     */
    public ObjectList<JsonMultiBlockDefinition> variants() {
        return this.variants;
    }

    /**
     * Returns the number of legal shape variants for this named structure.
     */
    public int variantCount() {
        return this.variants.size();
    }

    /**
     * Returns the explicit legal zero-based variant indexes in stable order.
     */
    public IntList variantIndexes() {
        return IntList.of(IntStream.range(0, this.variants.size()).toArray());
    }

    /**
     * Resolves one shape variant and fails fast for an out-of-range index.
     *
     * @param variantIndex zero-based shape variant
     * @return matching definition
     */
    public JsonMultiBlockDefinition definition(int variantIndex) {
        if (variantIndex < 0 || variantIndex >= this.variants.size()) {
            throw new IllegalArgumentException("Preview variant index " + variantIndex + " is outside 0.." +
                    (this.variants.size() - 1) + " for " + id());
        }
        return this.variants.get(variantIndex);
    }

    /**
     * Returns a detached title so callers cannot mutate definition-owned text.
     */
    public Component title() {
        return this.title.copy();
    }

    /**
     * Returns ordered independent tier categories.
     */
    public ObjectList<PreviewTierDomain> tierDomains() {
        return this.tierDomains;
    }

    /**
     * Returns repeat ranges for the default variant.
     */
    public ObjectList<RepeatRange> repeatRanges() {
        return repeatRanges(this.defaults.variantIndex());
    }

    /**
     * Returns repeat ranges derived from one variant's MDLib pattern layout.
     *
     * @param variantIndex zero-based shape variant
     * @return immutable repeat ranges for that variant
     */
    public ObjectList<RepeatRange> repeatRanges(int variantIndex) {
        definition(variantIndex);
        return this.repeatRangesByVariant.get(variantIndex);
    }

    /**
     * Returns the validated initial selection for a new preview session.
     */
    public SubstructureSelection defaults() {
        return this.defaults;
    }

    /**
     * Resolves one declared tier category by id.
     *
     * @param domainId stable tier-domain id
     * @return matching tier category
     */
    public PreviewTierDomain tierDomain(String domainId) {
        for (PreviewTierDomain domain : this.tierDomains) {
            if (domain.id().equals(domainId)) {
                return domain;
            }
        }
        throw new IllegalArgumentException("Unknown preview tier domain " + domainId + " for " + id());
    }

    /**
     * Validates a selection against this definition and normalizes tier map order to the domain declaration order.
     *
     * @param selection candidate selection
     * @return validated immutable selection
     */
    public SubstructureSelection validateSelection(SubstructureSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Substructure preview selection cannot be null");
        }
        ObjectList<RepeatRange> repeatRanges = repeatRanges(selection.variantIndex());
        if (selection.repeatCounts().size() != repeatRanges.size()) {
            throw new IllegalArgumentException("Substructure " + id() + " variant " + selection.variantIndex() +
                    " expects " + repeatRanges.size() +
                    " repeat counts, got " + selection.repeatCounts().size());
        }
        for (int index = 0; index < repeatRanges.size(); index++) {
            repeatRanges.get(index).requireValid(selection.repeatCounts().getInt(index));
        }

        ObjectSet<String> declaredDomains = new ObjectOpenHashSet<>();
        for (PreviewTierDomain domain : this.tierDomains) {
            declaredDomains.add(domain.id());
        }
        if (!selection.tierSelections().keySet().equals(declaredDomains)) {
            throw new IllegalArgumentException("Substructure " + id() +
                    " tier selections must exactly match its declared domains");
        }
        Object2IntMap<String> orderedTiers = new Object2IntLinkedOpenHashMap<>();
        for (PreviewTierDomain domain : this.tierDomains) {
            int value = selection.tierSelections().getInt(domain.id());
            domain.option(value);
            orderedTiers.put(domain.id(), value);
        }

        PatternLayout layout = definition(selection.variantIndex()).pattern().getLayout();
        for (Object2IntMap.Entry<PreviewPredicateKey> entry : selection.candidateSelections().object2IntEntrySet()) {
            PreviewPredicateKey key = entry.getKey();
            if (entry.getIntValue() < 0) {
                throw new IllegalArgumentException("Preview candidate index cannot be negative: " + entry.getIntValue());
            }
            if (key.sourceLayer() >= layout.sourceDepth() || key.y() >= layout.height() ||
                    key.x() >= layout.width()) {
                throw new IllegalArgumentException("Preview predicate key is outside substructure " + id() +
                        ": " + key);
            }
        }
        return new SubstructureSelection(
                selection.variantIndex(),
                selection.repeatCounts(),
                orderedTiers,
                selection.candidateSelections());
    }

    private static ObjectList<JsonMultiBlockDefinition> copyVariants(ObjectList<JsonMultiBlockDefinition> variants) {
        ObjectList<JsonMultiBlockDefinition> copy = new ObjectArrayList<>(variants);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("Substructure preview spec requires at least one variant");
        }
        JsonMultiBlockDefinition first = copy.getFirst();
        if (first == null) {
            throw new IllegalArgumentException("Substructure preview variants cannot contain null");
        }
        for (JsonMultiBlockDefinition variant : copy) {
            if (variant == null) {
                throw new IllegalArgumentException("Substructure preview variants cannot contain null");
            }
            if (!first.key().equals(variant.key())) {
                throw new IllegalArgumentException("Substructure preview variants must share one structure key: " +
                        first.key() + " and " + variant.key());
            }
        }
        return ObjectLists.unmodifiable(copy);
    }

    private static ObjectList<JsonMultiBlockDefinition> singleVariant(JsonMultiBlockDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Substructure preview definition cannot be null");
        }
        return ObjectList.of(definition);
    }

    private static ObjectList<PreviewTierDomain> copyTierDomains(ObjectList<PreviewTierDomain> tierDomains) {
        ObjectList<PreviewTierDomain> copy = new ObjectArrayList<>(tierDomains);
        ObjectSet<String> ids = new ObjectOpenHashSet<>();
        for (PreviewTierDomain domain : copy) {
            if (domain == null) {
                throw new IllegalArgumentException("Substructure tier domains cannot contain null");
            }
            if (!ids.add(domain.id())) {
                throw new IllegalArgumentException("Duplicate preview tier domain: " + domain.id());
            }
        }
        return ObjectLists.unmodifiable(copy);
    }
}
