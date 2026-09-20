package com.fish_dan_.data_energistics.menu.patternprovider;

import com.fish_dan_.data_energistics.ae2.patternprovider.packaged.DigitalPackagedPatternProviderLogic;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.slot.AppEngSlot;

import net.minecraft.world.entity.player.Inventory;

/** The standalone provider exposes AE2's pattern, return, blocking and lock settings only. */
public final class DigitalPackagedPatternProviderMenu extends PatternProviderMenu {

    private final DigitalPackagedPatternProviderLogic packagedLogic;
    @GuiSync(800)
    public int pendingOperations;

    public DigitalPackagedPatternProviderMenu(int id, Inventory inventory, PatternProviderLogicHost host) {
        super(DEMenus.DIGITAL_PACKAGED_PATTERN_PROVIDER.get(), id, inventory, host);
        this.packagedLogic = (DigitalPackagedPatternProviderLogic) host.getLogic();
        var returnInventory = this.packagedLogic.getReturnInv().createMenuWrapper();
        int visibleReturnSlots = getSlots(SlotSemantics.STORAGE).size();
        for (int slot = visibleReturnSlots; slot < returnInventory.size(); slot++) {
            addSlot(new AppEngSlot(returnInventory, slot), SlotSemantics.STORAGE);
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.pendingOperations = this.packagedLogic.dispatchState().pendingOperations();
        }
        super.broadcastChanges();
    }
}
