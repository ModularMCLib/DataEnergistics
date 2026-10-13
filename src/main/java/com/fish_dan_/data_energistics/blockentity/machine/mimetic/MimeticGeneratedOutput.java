package com.fish_dan_.data_energistics.blockentity.machine.mimetic;

import com.fish_dan_.data_energistics.ae2.key.ExperienceKey;
import com.fish_dan_.data_energistics.api.production.DataProductionOutput;

import appeng.api.stacks.AEItemKey;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

/** Compatibility facade for callers of the former item/experience batch API. */
public record MimeticGeneratedOutput(DataProductionOutput output) {

    private static DataProductionOutput fromLegacy(Object2LongMap<AEItemKey> items, long experience) {
        DataProductionOutput.Accumulator result = DataProductionOutput.accumulator();
        items.forEach(result::add);
        if (experience < 0) {
            throw new IllegalArgumentException("Experience cannot be negative");
        }
        if (experience > 0) {
            result.add(ExperienceKey.INSTANCE, experience);
        }
        return result.build();
    }

    public static MimeticGeneratedOutput empty() {
        return new MimeticGeneratedOutput(DataProductionOutput.empty());
    }

    public static MimeticGeneratedOutput fromStacks(Iterable<ItemStack> stacks) {
        return new MimeticGeneratedOutput(DataProductionOutput.fromStacks(stacks));
    }

    public static MimeticGeneratedOutput fromOutput(DataProductionOutput output) {
        return output.isEmpty() ? empty() : new MimeticGeneratedOutput(output);
    }

    public static MimeticGeneratedOutput fromStacks(Iterable<ItemStack> stacks, long experience) {
        return accumulator().addStacks(stacks).addExperience(experience).build();
    }

    public Object2LongMap<AEItemKey> items() {
        return output.itemAmounts();
    }

    public long experience() {
        return output.amount(ExperienceKey.INSTANCE);
    }

    public boolean isEmpty() {
        return output.isEmpty();
    }

    public MimeticGeneratedOutput merge(MimeticGeneratedOutput other) {
        return new MimeticGeneratedOutput(output.merge(other.output));
    }

    public MimeticGeneratedOutput repeat(int repetitions) {
        return new MimeticGeneratedOutput(output.repeat(repetitions));
    }

    public long itemAmount() {
        return output.itemAmount();
    }

    public static Accumulator accumulator() {
        return new Accumulator();
    }

    public static final class Accumulator {

        private final DataProductionOutput.Accumulator delegate = DataProductionOutput.accumulator();

        public Accumulator addStacks(Iterable<ItemStack> stacks) {
            delegate.addStacks(stacks);
            return this;
        }

        public Accumulator addRepeatedStacks(Iterable<ItemStack> stacks, int repetitions) {
            delegate.addRepeatedStacks(stacks, repetitions);
            return this;
        }

        public Accumulator add(MimeticGeneratedOutput output) {
            delegate.add(output.output);
            return this;
        }

        public Accumulator addRepeated(MimeticGeneratedOutput output, int repetitions) {
            delegate.addRepeated(output.output, repetitions);
            return this;
        }

        public Accumulator addExperience(long amount) {
            if (amount < 0) {
                throw new IllegalArgumentException("Experience cannot be negative");
            }
            if (amount > 0) {
                delegate.add(ExperienceKey.INSTANCE, amount);
            }
            return this;
        }

        public MimeticGeneratedOutput build() {
            return new MimeticGeneratedOutput(delegate.build());
        }
    }
}
