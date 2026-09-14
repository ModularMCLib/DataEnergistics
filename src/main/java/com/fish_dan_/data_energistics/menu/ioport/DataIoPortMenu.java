package com.fish_dan_.data_energistics.menu.ioport;

import com.fish_dan_.data_energistics.blockentity.ioport.DataIoPortBlockEntity;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.Settings;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.OutputSlot;
import appeng.menu.slot.RestrictedInputSlot;

import net.minecraft.world.entity.player.Inventory;

/** Menu for the data IO port, matching the AE2 I/O port cell layout. */
public final class DataIoPortMenu extends UpgradeableMenu<DataIoPortBlockEntity> {

    @GuiSync(2)
    public FullnessMode fullnessMode = FullnessMode.EMPTY;
    @GuiSync(3)
    public OperationMode operationMode = OperationMode.EMPTY;

    public DataIoPortMenu(int id, Inventory playerInventory, DataIoPortBlockEntity host) {
        super(DEMenus.DATA_IO_PORT.get(), id, playerInventory, host);
    }

    @Override
    protected void setupConfig() {
        var cells = getHost().getSubInventory(ISegmentedInventory.CELLS);
        for (int slot = 0; slot < DataIoPortBlockEntity.CELL_SLOTS; slot++) {
            addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.STORAGE_CELLS, cells, slot),
                    SlotSemantics.MACHINE_INPUT);
        }
        for (int slot = 0; slot < DataIoPortBlockEntity.CELL_SLOTS; slot++) {
            addSlot(new OutputSlot(cells, DataIoPortBlockEntity.CELL_SLOTS + slot,
                    RestrictedInputSlot.PlacableItemType.STORAGE_CELLS.icon), SlotSemantics.MACHINE_OUTPUT);
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager manager) {
        setRedStoneMode(manager.getSetting(Settings.REDSTONE_CONTROLLED));
        this.fullnessMode = manager.getSetting(Settings.FULLNESS_MODE);
        this.operationMode = manager.getSetting(Settings.OPERATION_MODE);
    }
}
