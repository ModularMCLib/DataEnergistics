package com.fish_dan_.data_energistics.client.terminal.storage;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InventoryAction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import com.mojang.blaze3d.platform.InputConstants;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Resolves overlay input to the same inventory actions as AE's MEStorageScreen. */
@NullMarked
public final class TerminalStorageInput {

    private final BiConsumer<InventoryAction, @Nullable AEKey> request;
    private final Predicate<AEKey> keyVisible;

    public TerminalStorageInput(BiConsumer<InventoryAction, @Nullable AEKey> request, Predicate<AEKey> keyVisible) {
        this.request = request;
        this.keyVisible = keyVisible;
    }

    public void click(@Nullable GenericStack stack, boolean craftable, boolean craftableView, int button) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = Objects.requireNonNull(minecraft.player);
        AEKey key = stack == null ? null : stack.what();
        if (minecraft.options.keyPickItem.matchesMouse(button)) {
            pick(stack, craftable);
            return;
        }
        if (button != 0 && button != 1) return;
        boolean shift = Screen.hasShiftDown();
        boolean emptyHand = player.containerMenu.getCarried().isEmpty();
        if (button == 0 && ContainerItemStrategies.isKeySupported(key)) {
            request.accept(!shift ? InventoryAction.FILL_ITEM : emptyHand ?
                    InventoryAction.FILL_ENTIRE_ITEM_MOVE_TO_PLAYER : InventoryAction.FILL_ENTIRE_ITEM, key);
            return;
        }
        if (button == 1 && !emptyHand) {
            var emptying = ContainerItemStrategies.getEmptyingAction(player.containerMenu.getCarried());
            if (emptying != null && keyVisible.test(emptying.what())) {
                request.accept(shift ? InventoryAction.EMPTY_ENTIRE_ITEM : InventoryAction.EMPTY_ITEM, null);
                return;
            }
        }
        if (stack == null) {
            if (!shift && !emptyHand)
                request.accept(button == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE : InventoryAction.PICKUP_OR_SET_DOWN, null);
            return;
        }
        if (InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_SPACE))
            request.accept(InventoryAction.MOVE_REGION, key);
        else if (shift)
            request.accept(button == 1 ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK, key);
        else if (button == 0 && emptyHand && craftable && (craftableView || stack.amount() == 0))
            request.accept(InventoryAction.AUTO_CRAFT, key);
        else request.accept(button == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE : InventoryAction.PICKUP_OR_SET_DOWN, key);
    }

    public void pick(@Nullable GenericStack stack, boolean craftable) {
        if (stack == null) return;
        if (craftable) request.accept(InventoryAction.AUTO_CRAFT, stack.what());
        else if (Objects.requireNonNull(Minecraft.getInstance().player).getAbilities().instabuild)
            request.accept(InventoryAction.CREATIVE_DUPLICATE, stack.what());
    }

    public boolean scroll(@Nullable GenericStack stack, double delta) {
        if (!Screen.hasShiftDown() || delta == 0) return false;
        InventoryAction action = delta > 0 ? InventoryAction.ROLL_DOWN : InventoryAction.ROLL_UP;
        for (int index = 0; index < (int) Math.abs(delta); index++) request.accept(action, stack == null ? null : stack.what());
        return true;
    }
}
