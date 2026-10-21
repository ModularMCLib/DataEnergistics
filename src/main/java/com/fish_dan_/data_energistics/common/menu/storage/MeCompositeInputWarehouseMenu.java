package com.fish_dan_.data_energistics.common.menu.storage;

import com.fish_dan_.data_energistics.common.blockentity.storage.MeCompositeInputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.common.data.DEMenus;
import com.fish_dan_.data_energistics.common.gui.compartment.CompartmentHostUi;

import net.minecraft.world.entity.player.Inventory;

public class MeCompositeInputWarehouseMenu extends CompartmentMenu {

    public MeCompositeInputWarehouseMenu(int id, Inventory playerInventory, MeCompositeInputWarehouseBlockEntity host) {
        super(DEMenus.ME_COMPOSITE_INPUT_WAREHOUSE.get(), id, playerInventory, host);
        CompartmentHostUi.mountMeInput(this);
    }
}
