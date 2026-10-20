package com.fish_dan_.data_energistics.common.terminal.player.transfer;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

import java.util.function.BooleanSupplier;

/** A live menu's pending recipe; changing its inputs or recipe cancels the later refill. */
@NullMarked
public final class TerminalRecipeRefill {

    private final long frequency;
    private final ObjectList<TerminalIngredientSupply.Target> targets;
    private final ObjectList<ItemStack> expected = new ObjectArrayList<>();
    private final BooleanSupplier recipeCurrent;
    private final boolean networkOnly;

    public TerminalRecipeRefill(long frequency, ObjectList<TerminalIngredientSupply.Target> targets, BooleanSupplier recipeCurrent, boolean networkOnly) {
        this.frequency = frequency;
        this.targets = new ObjectImmutableList<>(targets);
        this.recipeCurrent = recipeCurrent;
        this.networkOnly = networkOnly;
        for (var target : targets) expected.add(target.slot().getItem().copy());
    }

    public long frequency() {
        return frequency;
    }

    public ObjectList<TerminalIngredientSupply.Target> targets() {
        return targets;
    }

    public boolean networkOnly() {
        return networkOnly;
    }

    public boolean valid() {
        if (!recipeCurrent.getAsBoolean()) return false;
        for (int index = 0; index < targets.size(); index++)
            if (!ItemStack.matches(expected.get(index), targets.get(index).slot().getItem())) return false;
        return true;
    }
}
