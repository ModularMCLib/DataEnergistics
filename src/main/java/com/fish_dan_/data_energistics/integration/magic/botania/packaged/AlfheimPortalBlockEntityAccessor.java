package com.fish_dan_.data_energistics.integration.magic.botania.packaged;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Read-only inspection of pending trade ingredients; callers must not mutate the returned list. */
public interface AlfheimPortalBlockEntityAccessor {

    List<ItemStack> dataEnergistics$pendingTradeItems();
}
