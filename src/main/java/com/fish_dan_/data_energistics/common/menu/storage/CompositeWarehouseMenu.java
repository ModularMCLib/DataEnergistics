package com.fish_dan_.data_energistics.common.menu.storage;

import com.fish_dan_.data_energistics.common.blockentity.storage.CompositeWarehouseBlockEntity;
import com.fish_dan_.data_energistics.common.data.DEMenus;
import com.fish_dan_.data_energistics.common.gui.compartment.CompartmentHostUi;

import net.minecraft.world.entity.player.Inventory;

public class CompositeWarehouseMenu extends CompartmentMenu {

    public CompositeWarehouseMenu(int id, Inventory playerInventory, CompositeWarehouseBlockEntity host) {
        super(DEMenus.COMPOSITE_WAREHOUSE.get(), id, playerInventory, host);
        CompartmentHostUi.mountCompositeWarehouse(this);
    }
}
