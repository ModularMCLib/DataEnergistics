package com.fish_dan_.data_energistics.integration.ae.ae2.gui.resource;

import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * Local resource mark shared by configuration screens. The ItemStack is only an AE key transport;
 * callers own click handling, viewer integration and synchronization on the owning UI thread.
 */
public class AeKeyGhostSlot extends ItemSlot {

    /** Returns the native marked resource, or null for an empty slot. */
    public @Nullable AEKey getKey() {
        GenericStack resource = GenericStack.fromItemStack(getValue());
        return resource == null ? null : resource.what();
    }

    /** Updates a mark without moving inventory; snapshot refreshes pass false to suppress listeners. */
    public void setKey(@Nullable AEKey key, boolean notify) {
        setValue(key == null ? ItemStack.EMPTY : key.wrapForDisplayOrFilter(), notify);
    }

    /** Marks carry no quantity, including when a viewer provides a wrapped non-item resource. */
    @Override
    public AeKeyGhostSlot setValue(@Nullable ItemStack value, boolean notify) {
        GenericStack resource = value == null ? null : GenericStack.fromItemStack(value);
        super.setValue(resource == null ? ItemStack.EMPTY : resource.what().wrapForDisplayOrFilter(), notify);
        return this;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public ObjectList<Component> getFullTooltipTexts(boolean withItemTooltips) {
        ObjectList<Component> lines = new ObjectArrayList<>();
        AEKey key = getKey();
        if (withItemTooltips && getSlotStyle().showItemTooltips() && key != null) {
            lines.addAll(AEKeyRendering.getTooltip(key));
        }
        lines.addAll(getStyle().tooltips().asList());
        return lines;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void onHoverTooltips(UIEvent event) {
        var lines = getFullTooltipTexts();
        if (!lines.isEmpty()) event.hoverTooltips = new HoverTooltips(lines, null, null, null);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawBackgroundAdditional(GUIContext context) {
        AEKey key = getKey();
        context.pose.pushPose();
        context.pose.translate(getContentX(), getContentY(), 0);
        context.pose.scale(getContentWidth() / 16F, getContentHeight() / 16F, 1);
        if (key == null || !getSlotStyle().showSlotOverlayOnlyEmpty()) drawSlotOverlay(context);
        if (key != null) {
            // AE fluid renderers draw immediately; flush LDLib's buffered surfaces before invoking them.
            context.graphics.flush();
            context.pose.pushPose();
            context.pose.translate(0, 0, 1);
            // Compress item geometry without changing its lighting or the parent window's depth interval.
            context.pose.mulPose(new Matrix4f().scaling(1, 1, 0.001F));
            AEKeyRendering.drawInGui(context.mc, context.graphics, 0, 0, key);
            context.graphics.flush();
            context.pose.popPose();
            // AE's opaque fluid blitter disables blending; subsequent UI overlays still need alpha blending.
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        if (isActive() && (isHover() || isSelfOrChildHover())) {
            context.pose.translate(0, 0, 2);
            drawHover(context);
        }
        context.pose.popPose();
    }
}
