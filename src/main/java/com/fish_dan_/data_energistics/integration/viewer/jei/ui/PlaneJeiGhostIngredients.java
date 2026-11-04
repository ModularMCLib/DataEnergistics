package com.fish_dan_.data_energistics.integration.viewer.jei.ui;

import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.integration.xei.jei.JEIUIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.jei.LDLibJEIPlugin;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;

/** Adds native fluid ghost drops and lookup to the plane's shared item/fluid mark slots. */
@OnlyIn(Dist.CLIENT)
public final class PlaneJeiGhostIngredients {

    private PlaneJeiGhostIngredients() {}

    /** Called once while constructing a client slot, after LDLib's ordinary item-ghost handlers. */
    public static void attach(ItemSlot slot) {
        // Replace ItemSlot's item-only lookup before installing type-specific native queries.
        for (var listener : slot.getBubbleListeners(JEIUIEvents.CLICKABLE_INGREDIENT)) {
            slot.removeEventListener(JEIUIEvents.CLICKABLE_INGREDIENT, listener);
        }
        LDLibJEIPlugin.ghostIngredient(slot, NeoForgeTypes.FLUID_STACK, ingredient -> !ingredient.getIngredient().isEmpty(), fluid -> {
            AEFluidKey key = AEFluidKey.of(fluid);
            if (key != null) slot.setValue(key.wrapForDisplayOrFilter());
        });
        LDLibJEIPlugin.clickableIngredient(slot, () -> {
            GenericStack resource = GenericStack.fromItemStack(slot.getValue());
            if (resource != null && resource.what() instanceof AEItemKey item) {
                return LDLibJEIPlugin.createTypedIngredient(VanillaTypes.ITEM_STACK, item.toStack()).orElse(null);
            }
            return null;
        });
        LDLibJEIPlugin.clickableIngredient(slot, () -> {
            GenericStack resource = GenericStack.fromItemStack(slot.getValue());
            if (resource != null && resource.what() instanceof AEFluidKey fluid) {
                return LDLibJEIPlugin.createTypedIngredient(NeoForgeTypes.FLUID_STACK, fluid.toStack(AEFluidKey.AMOUNT_BUCKET)).orElse(null);
            }
            return null;
        });
    }
}
