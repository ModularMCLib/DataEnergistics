package com.fish_dan_.data_energistics.integration.viewer.emi.ui;

import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.integration.xei.emi.EMIUIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.emi.LDLibEMIPlugin;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;

/** Uses AE2's ingredient conversion so fluid marks remain fluid keys instead of bucket items. */
@OnlyIn(Dist.CLIENT)
public final class PlaneEmiGhostIngredients {

    private PlaneEmiGhostIngredients() {}

    /** Called once on the client; these handlers accept both native item and native fluid ingredients. */
    public static void attach(ItemSlot slot) {
        for (String event : new String[] { EMIUIEvents.STACK_PROVIDER, EMIUIEvents.RENDER_DRAG_HANDLER, EMIUIEvents.DROP_STACK_HANDLER }) {
            for (var listener : slot.getBubbleListeners(event)) slot.removeEventListener(event, listener);
        }
        LDLibEMIPlugin.renderDragHandler(slot, PlaneEmiGhostIngredients::accepts);
        LDLibEMIPlugin.dropStackHandler(slot, PlaneEmiGhostIngredients::accepts, ingredient -> {
            GenericStack resource = EmiStackHelper.toGenericStack((EmiStack) ingredient);
            if (resource != null) slot.setValue(resource.what().wrapForDisplayOrFilter());
        });
        LDLibEMIPlugin.stackProvider(slot, () -> {
            GenericStack resource = GenericStack.fromItemStack(slot.getValue());
            if (resource == null) return null;
            return new EmiStackInteraction(EmiStackHelper.toEmiStack(
                    new GenericStack(resource.what(), resource.what().getAmountPerUnit())), null, false);
        });
    }

    private static boolean accepts(EmiIngredient ingredient) {
        return ingredient instanceof EmiStack stack && EmiStackHelper.toGenericStack(stack) != null;
    }
}
