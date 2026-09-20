package com.fish_dan_.data_energistics.common.crafting.pattern.matching;

import com.fish_dan_.data_energistics.api.crafting.matching.ItemMatchingRule;
import com.fish_dan_.data_energistics.api.crafting.matching.ProcessingMatchMode;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** Explicit sparse input/output rules for processing patterns; missing rules use exact matching. */
public final class EncodedPatternMatching {

    public static final int MAX_SLOTS = 81;

    private EncodedPatternMatching() {}

    public static ProcessingMatchMode mode(AEItemKey definition, int slot) {
        return mode(definition, "i", slot);
    }

    public static ProcessingMatchMode outputMode(AEItemKey definition, int slot) {
        return mode(definition, "o", slot);
    }

    private static ProcessingMatchMode mode(AEItemKey definition, String side, int slot) {
        if (slot < 0 || slot >= MAX_SLOTS) return ProcessingMatchMode.EXACT;
        var data = definition.get(DEDataComponents.PROCESSING_PATTERN_MATCHING.get());
        if (data != null) {
            int ordinal = data.getCompound(side + slot).getInt("mode");
            if (ordinal < 0 || ordinal >= ProcessingMatchMode.values().length) throw new IllegalArgumentException("Invalid processing match mode");
            return ProcessingMatchMode.values()[ordinal];
        }
        return ProcessingMatchMode.EXACT;
    }

    public static String modes(AEItemKey definition) {
        return modes(definition, false);
    }

    public static String modes(AEItemKey definition, boolean output) {
        var result = new StringBuilder(MAX_SLOTS);
        for (int i = 0; i < MAX_SLOTS; i++) result.append((output ? outputMode(definition, i) : mode(definition, i)).ordinal());
        return result.toString();
    }

    public static boolean flexible(AEItemKey definition) {
        var rules = definition.get(DEDataComponents.PROCESSING_PATTERN_MATCHING.get());
        return rules != null && !rules.isEmpty();
    }

    public static List<ResourceLocation> tags(AEItemKey definition, int slot) {
        return tags(definition, "i", slot);
    }

    public static List<ResourceLocation> outputTags(AEItemKey definition, int slot) {
        return tags(definition, "o", slot);
    }

    private static List<ResourceLocation> tags(AEItemKey definition, String side, int slot) {
        var data = definition.get(DEDataComponents.PROCESSING_PATTERN_MATCHING.get());
        if (data == null) return List.of();
        var result = new ObjectArrayList<ResourceLocation>();
        for (var tag : data.getCompound(side + slot).getList("tags", Tag.TAG_STRING)) {
            result.add(ResourceLocation.parse(tag.getAsString()));
        }
        return List.copyOf(result);
    }

    public static ProcessingMatchMode modeForKey(AEItemKey definition, AEKey key) {
        var pattern = definition.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (pattern == null) return ProcessingMatchMode.EXACT;
        ProcessingMatchMode result = ProcessingMatchMode.TAG;
        boolean found = false;
        for (int i = 0; i < pattern.sparseInputs().size(); i++) {
            var input = pattern.sparseInputs().get(i);
            if (input == null || !input.what().equals(key)) continue;
            found = true;
            if (mode(definition, i).ordinal() < result.ordinal()) result = mode(definition, i);
        }
        return found ? result : ProcessingMatchMode.EXACT;
    }

    /** Resolves tag names server-side before a blank pattern is consumed; false means no declared tag exists. */
    public static boolean apply(ServerLevel level, ItemStack pattern, String inputs, String outputs) {
        if (!validModes(inputs) || !validModes(outputs)) return false;
        var encoded = pattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null) return inputs.chars().allMatch(value -> value == '0') && outputs.chars().allMatch(value -> value == '0');
        var data = new CompoundTag();
        for (boolean output : new boolean[] { false, true }) {
            var stacks = output ? encoded.sparseOutputs() : encoded.sparseInputs();
            String modes = output ? outputs : inputs;
            for (int slot = 0; slot < Math.min(modes.length(), stacks.size()); slot++) {
                int mode = modes.charAt(slot) - '0';
                var stack = stacks.get(slot);
                if (mode == 0 || stack == null) continue;
                if (!(stack.what() instanceof AEItemKey)) return false;
                var rule = new CompoundTag();
                rule.putInt("mode", mode);
                if (mode == ProcessingMatchMode.TAG.ordinal()) {
                    var tags = output ? RecipeIngredientTags.resolveOutput(level, pattern, slot) : RecipeIngredientTags.resolve(level, pattern, slot);
                    if (tags.isEmpty()) return false;
                    var names = new ListTag();
                    tags.forEach(tag -> names.add(StringTag.valueOf(tag.toString())));
                    rule.put("tags", names);
                }
                data.put((output ? "o" : "i") + slot, rule);
            }
        }
        pattern.set(DEDataComponents.PROCESSING_PATTERN_MATCHING, data);
        return true;
    }

    private static boolean validModes(String modes) {
        return modes.length() <= MAX_SLOTS && modes.chars().allMatch(value -> value >= '0' && value <= '2');
    }

    public static boolean matchesInput(AEItemKey definition, AEKey expected, AEKey actual) {
        var encoded = definition.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null) return expected.equals(actual);
        boolean found = false;
        for (int slot = 0; slot < encoded.sparseInputs().size(); slot++) {
            var input = encoded.sparseInputs().get(slot);
            if (input == null || !input.what().equals(expected)) continue;
            found = true;
            if (!matches(mode(definition, slot), tags(definition, slot), expected, actual)) return false;
        }
        return found;
    }

    public static boolean matchesOutput(AEItemKey definition, int slot, AEKey expected, AEKey actual) {
        return matches(outputMode(definition, slot), outputTags(definition, slot), expected, actual);
    }

    /** Input index is the nonempty-slot index returned by the explicit processing input view. */
    public static boolean matchesInput(AEItemKey definition, int inputIndex, AEKey expected, AEKey actual) {
        var encoded = definition.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null) return expected.equals(actual);
        int index = 0;
        for (int slot = 0; slot < encoded.sparseInputs().size(); slot++) {
            var input = encoded.sparseInputs().get(slot);
            if (input == null) continue;
            if (index++ != inputIndex) continue;
            var rule = new ItemMatchingRule(mode(definition, slot), tags(definition, slot));
            return rule.matches(input.what(), expected) && rule.matches(input.what(), actual);
        }
        return false;
    }

    public static boolean matchesOutput(AEItemKey definition, AEKey expected, AEKey actual) {
        var encoded = definition.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null) return expected.equals(actual);
        boolean found = false;
        for (int slot = 0; slot < encoded.sparseOutputs().size(); slot++) {
            var output = encoded.sparseOutputs().get(slot);
            if (output == null || !output.what().equals(expected)) continue;
            found = true;
            if (!matchesOutput(definition, slot, expected, actual)) return false;
        }
        return found || expected.equals(actual);
    }

    public static boolean matches(ProcessingMatchMode mode, List<ResourceLocation> tags, AEKey expected, AEKey actual) {
        return new ItemMatchingRule(mode, tags).matches(expected, actual);
    }
}
