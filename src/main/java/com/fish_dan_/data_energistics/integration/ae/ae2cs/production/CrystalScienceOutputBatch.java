package com.fish_dan_.data_energistics.integration.ae.ae2cs.production;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/** Plans one entropy reaction against a private copy so all outputs compete for the same free slots. */
@NullMarked
public final class CrystalScienceOutputBatch {

    private CrystalScienceOutputBatch() {}

    public static boolean canInsertAll(GenericStackInv inventory, List<GenericStack> outputs, IActionSource source) {
        GenericStackInv scratch = new GenericStackInv(null, inventory.getMode(), inventory.size()) {
            @Override
            public boolean isSupportedType(AEKeyType type) {
                return inventory.isSupportedType(type);
            }

            @Override
            public boolean isAllowedIn(int slot, AEKey key) {
                return inventory.isAllowedIn(slot, key);
            }

            @Override
            public long getMaxAmount(AEKey key) {
                return inventory.getMaxAmount(key);
            }
        };
        for (int slot = 0; slot < inventory.size(); slot++) {
            scratch.setStack(slot, inventory.getStack(slot));
        }
        for (GenericStack output : outputs) {
            if (scratch.insert(output.what(), output.amount(), Actionable.MODULATE, source) != output.amount()) {
                return false;
            }
        }
        return true;
    }
}
