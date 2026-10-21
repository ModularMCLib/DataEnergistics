package com.fish_dan_.data_energistics.common.menu.storage;

import com.fish_dan_.data_energistics.common.blockentity.storage.MePatternBufferBlockEntity;
import com.fish_dan_.data_energistics.common.data.DEMenus;
import com.fish_dan_.data_energistics.common.gui.compartment.CompartmentHostUi;

import net.minecraft.world.entity.player.Inventory;

public class MePatternBufferMenu extends CompartmentMenu {

    public MePatternBufferMenu(int id, Inventory playerInventory, MePatternBufferBlockEntity host) {
        super(DEMenus.ME_PATTERN_BUFFER.get(), id, playerInventory, host);
        CompartmentHostUi.mountPatternBuffer(this);
    }
}
