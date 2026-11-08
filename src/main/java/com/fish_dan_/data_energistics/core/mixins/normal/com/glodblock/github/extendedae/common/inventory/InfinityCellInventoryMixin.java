package com.fish_dan_.data_energistics.core.mixins.normal.com.glodblock.github.extendedae.common.inventory;

import com.fish_dan_.data_energistics.integration.ae.ae2.grid.UnlimitedExtractableStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import com.glodblock.github.extendedae.common.inventory.InfinityCellInventory;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;

/** Exposes ExtendedAE infinity-cell extraction as an explicit non-consuming storage capability. */
@Restriction(require = @Condition("extendedae"))
@Mixin(value = InfinityCellInventory.class, remap = false)
@NullMarked
public abstract class InfinityCellInventoryMixin implements UnlimitedExtractableStorage {

    @Override
    public boolean supportsUnlimitedExtraction(AEKey key, IActionSource source) {
        return ((MEStorage) this).extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source) == Long.MAX_VALUE;
    }
}
