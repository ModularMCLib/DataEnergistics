package com.fish_dan_.data_energistics.mixin.minecraft.client.render.tooltip;

import com.fish_dan_.data_energistics.client.terminal.rendering.TerminalTooltipRenderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Preserves native tooltip components, item context and visibility hooks during the foreground replay. */
@NullMarked
@Mixin(GuiGraphics.class)
public abstract class PlayerTerminalTooltipMixin {

    @Shadow
    private ItemStack tooltipStack;

    @WrapMethod(method = "renderTooltipInternal")
    private void dataEnergistics$captureTooltip(Font font, List<ClientTooltipComponent> components, int mouseX, int mouseY,
                                                ClientTooltipPositioner positioner, Operation<Void> original) {
        ItemStack stack = tooltipStack;
        TerminalTooltipRenderer.capture((GuiGraphics) (Object) this, () -> {
            ItemStack previous = tooltipStack;
            tooltipStack = stack;
            try {
                original.call(font, components, mouseX, mouseY, positioner);
            } finally {
                tooltipStack = previous;
            }
        });
    }

    @WrapOperation(method = "renderTooltipInternal",
                   at = @At(value = "INVOKE",
                            target = "Lnet/neoforged/neoforge/client/ClientHooks;onRenderTooltipPre(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/gui/GuiGraphics;IIIILjava/util/List;Lnet/minecraft/client/gui/Font;Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)Lnet/neoforged/neoforge/client/event/RenderTooltipEvent$Pre;"))
    private RenderTooltipEvent.Pre dataEnergistics$deferAcceptedTooltip(ItemStack stack, GuiGraphics graphics, int x, int y,
                                                                        int screenWidth, int screenHeight, List<ClientTooltipComponent> components,
                                                                        Font font, ClientTooltipPositioner positioner, Operation<RenderTooltipEvent.Pre> original) {
        return TerminalTooltipRenderer.prepare(() -> original.call(stack, graphics, x, y, screenWidth, screenHeight, components, font, positioner));
    }
}
