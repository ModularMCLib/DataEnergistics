package com.fish_dan_.data_energistics.common.weapon.platform.equipment.protection;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.NullMarked;

/** Keeps vanilla's fixed inventory shape while guarding its publicly exposed backing lists. */
@NullMarked
public final class ProtectedEquipmentList extends NonNullList<ItemStack> {

    private final ServerPlayer player;
    private final int group;

    public ProtectedEquipmentList(ServerPlayer player, NonNullList<ItemStack> contents, int group) {
        super(contents, ItemStack.EMPTY);
        this.player = player;
        this.group = group;
    }

    public static NonNullList<ItemStack> wrap(Player player, NonNullList<ItemStack> contents, int group) {
        return player instanceof ServerPlayer serverPlayer ? new ProtectedEquipmentList(serverPlayer, contents, group) : contents;
    }

    @Override
    public ItemStack get(int index) {
        ItemStack stack = super.get(index);
        EquipmentProtection.bind(player, stack, group, index, "", false);
        return stack;
    }

    @Override
    public ItemStack set(int index, ItemStack stack) {
        ItemStack previous = get(index);
        return EquipmentProtection.denyReplacement(player, group, index, previous, stack) ? ItemStack.EMPTY : super.set(index, stack);
    }

    @Override
    public void clear() {
        for (int index = 0; index < size(); index++) set(index, ItemStack.EMPTY);
    }
}
