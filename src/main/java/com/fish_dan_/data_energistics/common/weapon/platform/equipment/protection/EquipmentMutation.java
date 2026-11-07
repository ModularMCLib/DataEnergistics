package com.fish_dan_.data_energistics.common.weapon.platform.equipment.protection;

import com.fish_dan_.data_energistics.common.data.DEAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

/** A synchronous player action may change protected equipment, including nested menu and item-use calls. */
public final class EquipmentMutation implements AutoCloseable {

    private final @Nullable EquipmentProtectionState state;

    public EquipmentMutation(Player player) {
        state = player instanceof ServerPlayer serverPlayer ? serverPlayer.getData(DEAttachments.EQUIPMENT_PROTECTION) : null;
        if (state != null) state.permits++;
    }

    @Override
    public void close() {
        if (state != null) state.permits--;
    }
}
