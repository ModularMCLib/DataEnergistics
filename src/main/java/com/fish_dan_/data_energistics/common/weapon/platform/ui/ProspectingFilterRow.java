package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.prospecting.ProspectingFilters;

import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

/** A ghost block marker or a block tag; selecting either locks the other until cleared. */
final class ProspectingFilterRow extends UIElement {

    private final Button block = new Button();
    private final TextField tag = new TextField();
    private final Button remove = new Button();
    private final boolean selectedBlock;
    private boolean researched;

    ProspectingFilterRow(Player player, int index, ProspectingFilters.Entry entry) {
        selectedBlock = entry.block().isPresent();
        addClass("platform-prospecting-filter");
        block.noText();
        block.addClasses("platform-action", "platform-prospecting-block");
        ItemStack stack = entry.block().map(BuiltInRegistries.BLOCK::get).map(value -> new ItemStack(value.asItem())).orElse(ItemStack.EMPTY);
        block.style(style -> style.tooltips(stack.isEmpty() ? Component.translatable("gui.data_energistics.weapon_platform.prospecting_block") : stack.getHoverName()));
        UIElement icon = new UIElement();
        icon.addClass("platform-prospecting-block-icon");
        icon.setAllowHitTest(false);
        icon.style(style -> style.backgroundTexture(new ItemStackTexture(stack)));
        block.addChild(icon);
        block.setOnClick(event -> {
            if (!player.level().isClientSide || !(player.containerMenu.getCarried().getItem() instanceof BlockItem item)) return;
            RPCPacketDistributor.rpcToServer(PlatformRpc.ORE_FILTER, index, 2, BuiltInRegistries.BLOCK.getKey(item.getBlock()).toString());
        });
        block.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 1 && block.isActive() && player.level().isClientSide) {
                RPCPacketDistributor.rpcToServer(PlatformRpc.ORE_FILTER, index, 2, "");
                event.stopPropagation();
            }
        });
        tag.addClasses("platform-editor-input", "platform-prospecting-tag");
        tag.textFieldStyle(style -> style.placeholder(Component.literal("#c:ores")));
        tag.setValue(entry.tag().map(Object::toString).orElse(""), false);
        tag.setTextResponder(value -> block.setActive(researched && value.isBlank()));
        tag.addEventListener(UIEvents.KEY_DOWN, event -> {
            if (!tag.isActive() || event.keyCode != GLFW.GLFW_KEY_ENTER && event.keyCode != GLFW.GLFW_KEY_KP_ENTER) return;
            String value = tag.getValue().strip();
            if (value.startsWith("#")) value = value.substring(1);
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.ORE_FILTER, index, 3, value);
            event.stopPropagation();
        });
        remove.setText(Component.literal("×"));
        remove.addClasses("platform-action", "platform-prospecting-remove");
        remove.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.prospecting_remove")));
        remove.setOnClick(event -> {
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.ORE_FILTER, index, 1, "");
        });
        addChildren(block, tag, remove);
    }

    void available(boolean researched) {
        this.researched = researched;
        block.setActive(researched && tag.getValue().isBlank());
        tag.setActive(researched && !selectedBlock);
        remove.setActive(researched);
    }
}
