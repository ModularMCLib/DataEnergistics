package com.fish_dan_.data_energistics.core.mixins.normal.appeng.client.gui;

import com.fish_dan_.data_energistics.api.registry.provider.client.PatternProviderScreenRegistration;
import com.fish_dan_.data_energistics.client.registry.ClientPluginRegistrations;
import com.fish_dan_.data_energistics.client.screen.Ae2NativeSlotHighlight;
import com.fish_dan_.data_energistics.client.screen.Ldlib2AeProtocolScreen;
import com.fish_dan_.data_energistics.client.screen.patternprovider.PatternProviderScreenAccess;
import com.fish_dan_.data_energistics.integration.ae.ae2.gui.bridge.AeItemSlot;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.WidgetContainer;
import appeng.menu.slot.IOptionalSlot;
import appeng.menu.slot.ResizableSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AEBaseScreen.class)
@NullMarked
public abstract class AEBaseScreenMixin implements PatternProviderScreenAccess {

    @Shadow
    @Final
    protected WidgetContainer widgets;

    @Shadow
    protected abstract <B extends Button> B addToLeftToolbar(B button);

    @Unique
    private @Nullable PatternProviderScreenRegistration dataEnergistics$pendingProviderScreen;

    @Unique
    private @Nullable Runnable dataEnergistics$providerScreenUpdate;

    @Override
    public void dataEnergistics$attachProviderScreen(PatternProviderScreenRegistration registration) {
        dataEnergistics$pendingProviderScreen = registration;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$resolveProviderScreen(CallbackInfo ci) {
        dataEnergistics$pendingProviderScreen = ClientPluginRegistrations.providerScreen(
                ((AEBaseScreen<?>) (Object) this).getMenu().getClass());
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void dataEnergistics$initializeProviderScreen(CallbackInfo ci) {
        if (dataEnergistics$pendingProviderScreen != null) {
            dataEnergistics$providerScreenUpdate = dataEnergistics$pendingProviderScreen.initialize(
                    (AEBaseScreen<?>) (Object) this, widgets, this::addToLeftToolbar);
            dataEnergistics$pendingProviderScreen = null;
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lappeng/client/gui/WidgetContainer;updateBeforeRender()V"))
    private void dataEnergistics$updateProviderScreen(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                      float partialTicks, CallbackInfo ci) {
        if (dataEnergistics$providerScreenUpdate != null) {
            dataEnergistics$providerScreenUpdate.run();
        }
    }

    @Unique
    private static final String DATA_ENERGISTICS_SCREEN_PACKAGE = "com.fish_dan_.data_energistics.client.screen";
    @Unique
    private static final int DATA_ENERGISTICS_SLOT_HIGHLIGHT_BORDER = 0xFF57E0BB;
    @Unique
    private static final int DATA_ENERGISTICS_SLOT_HIGHLIGHT_FILL = 0x6693FFDE;

    @Inject(method = "renderSlotHighlight", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$renderSlotHighlight(GuiGraphics guiGraphics, Slot slot, int mouseX, int mouseY,
                                                     float partialTick, CallbackInfo ci) {
        if (this instanceof Ae2NativeSlotHighlight) {
            return;
        }

        String screenPackage = this.getClass().getPackageName();
        if (!screenPackage.equals(DATA_ENERGISTICS_SCREEN_PACKAGE) && !screenPackage.startsWith(DATA_ENERGISTICS_SCREEN_PACKAGE + ".")) {
            return;
        }

        ci.cancel();
        if (!slot.isHighlightable()) {
            return;
        }

        int width = 16;
        int height = 16;
        if (slot instanceof ResizableSlot resizableSlot) {
            width = resizableSlot.getWidth();
            height = resizableSlot.getHeight();
        }

        int x = slot.x;
        int y = slot.y;
        guiGraphics.hLine(x, x + width, y - 1, DATA_ENERGISTICS_SLOT_HIGHLIGHT_BORDER);
        guiGraphics.hLine(x - 1, x + width, y + height, DATA_ENERGISTICS_SLOT_HIGHLIGHT_BORDER);
        guiGraphics.vLine(x - 1, y - 2, y + height, DATA_ENERGISTICS_SLOT_HIGHLIGHT_BORDER);
        guiGraphics.vLine(x + width, y - 2, y + height, DATA_ENERGISTICS_SLOT_HIGHLIGHT_BORDER);
        guiGraphics.fillGradient(RenderType.guiOverlay(), x, y, x + width, y + height,
                DATA_ENERGISTICS_SLOT_HIGHLIGHT_FILL, DATA_ENERGISTICS_SLOT_HIGHLIGHT_FILL, 0);
    }

    @Inject(method = "renderTooltips", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$suppressLegacyTooltips(GuiGraphics guiGraphics,
                                                        int mouseX,
                                                        int mouseY,
                                                        CallbackInfo callbackInfo) {
        if ((Object) this instanceof Ldlib2AeProtocolScreen<?>) {
            callbackInfo.cancel();
            dataEnergistics$drawCarriedTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    @Unique
    private void dataEnergistics$drawCarriedTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        AEBaseScreen<?> screen = (AEBaseScreen<?>) (Object) this;
        ItemStack carried = screen.getMenu().getCarried();
        if (carried.isEmpty()) {
            return;
        }
        Slot hoveredSlot = screen.getSlotUnderMouse();
        if (hoveredSlot == null) {
            return;
        }
        IModularUIHolderMenu holder = (IModularUIHolderMenu) screen.getMenu();
        if (!(holder.getItemSlot(hoveredSlot) instanceof AeItemSlot itemSlot)) {
            return;
        }
        HoverTooltips tooltip = itemSlot.createTooltip(carried);
        if (tooltip == null || tooltip.tooltipTexts().isEmpty()) {
            return;
        }
        DrawerHelper.drawTooltip(
                guiGraphics,
                mouseX,
                mouseY,
                tooltip.tooltipTexts(),
                tooltip.tooltipStack(),
                tooltip.tooltipComponent(),
                tooltip.tooltipFont());
    }

    @Inject(method = "drawOptionalSlotBackground", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$suppressOptionalSlotBackground(GuiGraphics guiGraphics,
                                                                IOptionalSlot optionalSlot,
                                                                boolean alwaysDraw,
                                                                CallbackInfo callbackInfo) {
        if ((Object) this instanceof Ldlib2AeProtocolScreen<?>) {
            callbackInfo.cancel();
        }
    }
}
