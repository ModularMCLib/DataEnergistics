package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule.Transition;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/**
 * Versioned, complete value encoding for frozen rules. Decoding never re-runs an adapter, so an
 * in-flight session retains the precise rule it accepted even after plugin or recipe changes.
 */
public final class ReusableInputRuleNbtCodec {

    private ReusableInputRuleNbtCodec() {}

    /** @return new mutable NBT tree containing every semantic value, including exact components */
    public static CompoundTag encode(ReusableInputRule rule, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", rule.id().toString());
        tag.putLong("revision", rule.revision());
        tag.putString("kind", rule.kind().name());
        tag.put("initial", rule.initialKey().toTagGeneric(registries));
        tag.putInt("damage_per_use", rule.damagePerUse());
        tag.putInt("break_at_damage", rule.breakAtDamage());
        tag.put("exhaustion_outputs", encodeOutputs(rule.exhaustionByproductsFast(), registries));
        ListTag transitions = new ListTag();
        for (Transition transition : rule.transitionsFast()) {
            CompoundTag entry = new CompoundTag();
            entry.put("input", transition.input().toTagGeneric(registries));
            entry.putBoolean("exhausted", transition.successor() == null);
            if (transition.successor() != null) {
                entry.put("successor", transition.successor().toTagGeneric(registries));
            }
            entry.put("outputs", encodeOutputs(transition.byproductsFast(), registries));
            transitions.add(entry);
        }
        tag.put("transitions", transitions);
        return tag;
    }

    /**
     * @return fully validated immutable rule
     * @throws IllegalArgumentException when a decoded key or rule value cannot be used
     */
    public static ReusableInputRule decode(CompoundTag tag, HolderLookup.Provider registries) {
        List<Transition> transitions = new ObjectArrayList<>();
        for (Tag encoded : tag.getList("transitions", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) encoded;
            boolean exhausted = entry.getBoolean("exhausted");
            transitions.add(new Transition(decodeItem(entry, "input", registries),
                    exhausted ? null : decodeItem(entry, "successor", registries),
                    decodeOutputs(entry, "outputs", registries)));
        }
        return new ReusableInputRule(ResourceLocation.parse(tag.getString("id")), tag.getLong("revision"),
                ReusableInputRule.Kind.valueOf(tag.getString("kind")), decodeItem(tag, "initial", registries),
                tag.getInt("damage_per_use"), tag.getInt("break_at_damage"),
                decodeOutputs(tag, "exhaustion_outputs", registries), transitions);
    }

    private static ListTag encodeOutputs(List<GenericStack> outputs, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        outputs.forEach(output -> result.add(GenericStack.writeTag(registries, output)));
        return result;
    }

    private static List<GenericStack> decodeOutputs(CompoundTag tag, String field, HolderLookup.Provider registries) {
        List<GenericStack> result = new ObjectArrayList<>();
        for (Tag encoded : tag.getList(field, Tag.TAG_COMPOUND)) {
            GenericStack output = GenericStack.readTag(registries, (CompoundTag) encoded);
            if (output == null || output.amount() <= 0L) {
                throw new IllegalArgumentException("Reusable rule contains an invalid byproduct");
            }
            result.add(output);
        }
        return result;
    }

    private static AEItemKey decodeItem(CompoundTag tag, String field, HolderLookup.Provider registries) {
        if (!(AEKey.fromTagGeneric(registries, tag.getCompound(field)) instanceof AEItemKey item)) {
            throw new IllegalArgumentException("Reusable rule contains an unknown item key: " + field);
        }
        return item;
    }
}
