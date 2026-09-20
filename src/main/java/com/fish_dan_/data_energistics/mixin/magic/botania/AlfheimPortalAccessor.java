package com.fish_dan_.data_energistics.mixin.magic.botania;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import vazkii.botania.common.block.block_entity.AlfheimPortalBlockEntity;

import java.util.List;

/** Read-only inspection of pending trade ingredients; callers must not mutate the returned list. */
@Mixin(value = AlfheimPortalBlockEntity.class, remap = false)
public interface AlfheimPortalAccessor {

    @Accessor("portalStacks")
    List<ItemStack> dataEnergistics$pendingTradeItems();
}
