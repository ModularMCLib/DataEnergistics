package com.fish_dan_.data_energistics.item.powered.sanctifier;

import com.fish_dan_.data_energistics.item.powered.PoweredEnergyItem;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Two cosmetic bits; the server owns inventory inspection and clients retain tracking snapshots. */
public record SanctifierCarryState(boolean carried, boolean powered) {

    private static final String TRACKED_FLAGS = "data_energistics:sanctifier_carry";

    /** Reads only the hotbar. Equipment and inventory contents are never changed. */
    public static SanctifierCarryState fromHotbar(Player player) {
        ItemStack selected = player.getMainHandItem();
        if (selected.is(DEItems.DATA_SANCTIFIER.get())) return of(selected);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(DEItems.DATA_SANCTIFIER.get())) return of(stack);
        }
        return new SanctifierCarryState(false, false);
    }

    private static SanctifierCarryState of(ItemStack stack) {
        return new SanctifierCarryState(true,
                stack.getItem() instanceof PoweredEnergyItem powered && powered.getAECurrentPower(stack) > 0);
    }

    public int flags() {
        return (this.carried ? 1 : 0) | (this.powered ? 2 : 0);
    }

    /** Validates the network boundary; impossible power-without-carry snapshots are rejected. */
    public static SanctifierCarryState fromFlags(int flags) {
        if (flags < 0 || flags > 3 || flags == 2) {
            throw new IllegalArgumentException("Invalid sanctifier carry flags: " + flags);
        }
        return new SanctifierCarryState((flags & 1) != 0, (flags & 2) != 0);
    }

    /** Applies a client tracking snapshot to this entity's lifetime, without retaining its world. */
    public void accept(Player player) {
        player.getPersistentData().putInt(TRACKED_FLAGS, flags());
    }

    /** Remote hotbars are not normally synchronized; presentation reads the server snapshot. */
    public static SanctifierCarryState tracked(Player player) {
        return fromFlags(player.getPersistentData().getInt(TRACKED_FLAGS));
    }
}
