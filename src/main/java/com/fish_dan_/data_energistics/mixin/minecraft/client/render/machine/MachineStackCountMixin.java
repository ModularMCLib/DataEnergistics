package com.fish_dan_.data_energistics.mixin.minecraft.client.render.machine;

import com.fish_dan_.data_energistics.client.gui.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.client.screen.machine.DataIntegratedChargerScreen;
import com.fish_dan_.data_energistics.client.screen.machine.DataRipperReassemblerScreen;
import com.fish_dan_.data_energistics.util.FormattingUtils;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Formats enlarged machine stacks in slots and on the cursor without changing their actual counts. */
@NullMarked
@Mixin(AbstractContainerScreen.class)
public abstract class MachineStackCountMixin {

    @WrapOperation(method = { "renderSlotContents", "renderFloatingItem" },
                   at = @At(value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void dataEnergistics$formatMachineCount(GuiGraphics graphics, Font font, ItemStack stack, int x, int y,
                                                    @Nullable String countText, Operation<Void> original) {
        if (stack.getCount() < 100 ||
                !((Object) this instanceof DataRipperReassemblerScreen<?> || (Object) this instanceof DataIntegratedChargerScreen)) {
            original.call(graphics, font, stack, x, y, countText);
            return;
        }
        original.call(graphics, font, stack, x, y, "");
        String count = FormattingUtils.format(stack.getCount());
        GenericStackDisplayHelper.renderSmallOverlay(graphics, x, y,
                countText == null ? count : countText.replace(Integer.toString(stack.getCount()), count));
    }
}
