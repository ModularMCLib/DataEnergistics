package com.fish_dan_.data_energistics.common.item.powered.sanctifier;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.item.powered.PoweredSwordItem;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Cosmetic inventory snapshot; remote clients receive only the three display bits, never inventory contents. */
public record SanctifierCarryState(boolean carried, boolean powered, boolean deployed) {

    public static final SanctifierCarryState EMPTY = new SanctifierCarryState(false, false, false);

    public static SanctifierCarryState fromInventory(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.is(DEItems.DATA_SANCTIFIER.get())) return of(held);
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(DEItems.DATA_SANCTIFIER.get())) return of(stack);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(DEItems.DATA_SANCTIFIER.get())) return of(stack);
        }
        return EMPTY;
    }

    public static SanctifierCarryState of(ItemStack stack) {
        return new SanctifierCarryState(true, ((PoweredSwordItem) stack.getItem()).getAECurrentPower(stack) > 0,
                stack.has(DEDataComponents.SANCTIFIER_BLADE.get()));
    }

    public int flags() {
        return (carried ? 1 : 0) | (powered ? 2 : 0) | (deployed ? 4 : 0);
    }

    public static SanctifierCarryState fromFlags(int flags) {
        if (flags < 0 || flags > 7 || (flags & 1) == 0 && flags != 0) throw new IllegalArgumentException("Invalid sanctifier carry flags: " + flags);
        return new SanctifierCarryState((flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
    }

    public void accept(Player player) {
        player.setData(DEAttachments.SANCTIFIER_CARRY, this);
    }

    public static SanctifierCarryState tracked(Player player) {
        return player.getData(DEAttachments.SANCTIFIER_CARRY);
    }
}
