package com.fish_dan_.data_energistics.common.crafting.pattern.matching;

import com.fish_dan_.data_energistics.api.crafting.matching.ItemMatchingRule;
import com.fish_dan_.data_energistics.api.crafting.matching.ProcessingMatchMode;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetails.PatternInputSink;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Keeps distinct slot rules distinct, even when their item keys would normally be condensed by AE2. */
public final class ProcessingPatternInputs {

    private ProcessingPatternInputs() {}

    public static IPatternDetails.IInput[] apply(AEItemKey definition, IPatternDetails.IInput[] original) {
        if (!EncodedPatternMatching.flexible(definition)) return original;
        var encoded = definition.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        var inputs = new ObjectArrayList<IPatternDetails.IInput>();
        for (int slot = 0; slot < encoded.sparseInputs().size(); slot++) {
            var stack = encoded.sparseInputs().get(slot);
            if (stack == null) continue;
            var mode = EncodedPatternMatching.mode(definition, slot);
            inputs.add(new MatchingInput(stack, new ItemMatchingRule(mode,
                    mode == ProcessingMatchMode.TAG ? EncodedPatternMatching.tags(definition, slot) : ObjectList.of())));
        }
        return inputs.toArray(IPatternDetails.IInput[]::new);
    }

    /** Checks all held assets before emitting actual keys in sparse order; never emits the old template key. */
    public static void push(AEItemKey definition, KeyCounter[] supplied, PatternInputSink sink) {
        var encoded = definition.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        int holder = 0;
        for (int slot = 0; slot < encoded.sparseInputs().size(); slot++) {
            var template = encoded.sparseInputs().get(slot);
            if (template == null) continue;
            if (holder >= supplied.length) throw new IllegalArgumentException("Missing processing input holder");
            long actual = 0;
            var mode = EncodedPatternMatching.mode(definition, slot);
            var rule = new ItemMatchingRule(mode, mode == ProcessingMatchMode.TAG ? EncodedPatternMatching.tags(definition, slot) : ObjectList.of());
            for (var entry : supplied[holder++]) {
                if (entry.getLongValue() <= 0 || !rule.matches(template.what(), entry.getKey())) throw new IllegalArgumentException("Unauthorized processing input variant");
                actual = Math.addExact(actual, entry.getLongValue());
            }
            if (template.amount() != actual) throw new IllegalArgumentException("Processing input quantity differs from its slot");
        }
        if (holder != supplied.length) throw new IllegalArgumentException("Unexpected processing input holder");
        for (var counter : supplied) for (var entry : counter) sink.pushInput(entry.getKey(), entry.getLongValue());
    }

    private record MatchingInput(GenericStack template, ItemMatchingRule rule) implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            if (rule.mode() != ProcessingMatchMode.TAG) return new GenericStack[] { new GenericStack(template.what(), 1) };
            var keys = new ObjectLinkedOpenHashSet<AEItemKey>();
            var original = (AEItemKey) template.what();
            if (rule.matches(original, original)) keys.add(original);
            for (var name : rule.tags()) {
                BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, name)).ifPresent(items ->
                        items.forEach(holder -> keys.add(AEItemKey.of(holder.value()))));
            }
            if (keys.isEmpty()) throw new IllegalArgumentException("Recipe tag no longer contains any processing input");
            return keys.stream().map(key -> new GenericStack(key, 1)).toArray(GenericStack[]::new);
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return rule.matches(template.what(), input);
        }

        @Override
        public long getMultiplier() {
            return template.amount();
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey input) {
            return null;
        }
    }
}
