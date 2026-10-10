package com.fish_dan_.data_energistics.mixin.minecraft.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the vanilla container-screen height setter to client screen patches that resize generated backgrounds.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("imageHeight")
    void dataEnergistics$setImageHeight(int height);
}
