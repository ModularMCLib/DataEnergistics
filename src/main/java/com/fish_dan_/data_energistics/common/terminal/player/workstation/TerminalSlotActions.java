package com.fish_dan_.data_energistics.common.terminal.player.workstation;

import com.fish_dan_.data_energistics.common.terminal.player.encoding.TerminalPatternContext;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalGhostInventory;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Physical cursor clicks and ghost configuration use separate paths and never borrow host slot indices.
 */
public final class TerminalSlotActions {

    private TerminalSlotActions() {}

    public static void physical(ServerPlayer player, TerminalPlayerState state, CompoundTag data) {
        String group = data.getString("inventory");
        boolean patterns = group.equals("encoded");
        if (!patterns && !group.equals("inputs"))
            throw new IllegalArgumentException("Unknown terminal physical inventory");
        if (patterns ? !state.workspace().supportsEncoding() : state.workspace() == TerminalWorkspace.PROCESSING)
            return;
        SimpleContainer inventory = patterns ? state.work().patterns : state.work().inputs;
        int index = data.getInt("index");
        int button = data.getInt("button");
        if (index < 0 || index >= inventory.getContainerSize() || button < 0 || button > 1)
            throw new IllegalArgumentException("Invalid terminal slot click");
        ItemStack before = inventory.getItem(index).copy();
        ItemStack stack = inventory.getItem(index);
        if (data.getBoolean("shift")) {
            int moved = Math.min(stack.getCount(), TerminalNetworkStorage.inventoryCapacity(player, stack));
            if (moved > 0) {
                ItemStack output = inventory.removeItem(index, moved);
                player.getInventory().add(output);
                if (!output.isEmpty()) throw new IllegalStateException("Terminal slot lost reserved player capacity");
            }
        } else {
            ItemStack cursor = player.containerMenu.getCarried();
            if (cursor.isEmpty()) {
                int count = button == 1 ? (stack.getCount() + 1) / 2 : stack.getCount();
                if (count > 0) player.containerMenu.setCarried(inventory.removeItem(index, count));
            } else if (!patterns) {
                int maximum = state.workspace() == TerminalWorkspace.ANVIL && index == 0 ? 1 : cursor.getMaxStackSize();
                if (stack.isEmpty() || ItemStack.isSameItemSameComponents(stack, cursor)) {
                    int moved = Math.min(button == 1 ? 1 : cursor.getCount(), maximum - stack.getCount());
                    if (moved > 0) {
                        inventory.setItem(index, cursor.copyWithCount(stack.getCount() + moved));
                        cursor.shrink(moved);
                    }
                } else if (button == 0 && cursor.getCount() <= maximum) {
                    inventory.setItem(index, cursor);
                    player.containerMenu.setCarried(stack);
                    cursor = stack;
                }
                if (cursor.isEmpty()) player.containerMenu.setCarried(ItemStack.EMPTY);
            } else if (ItemStack.isSameItemSameComponents(stack, cursor)) {
                int moved = Math.min(button == 1 ? 1 : stack.getCount(), cursor.getMaxStackSize() - cursor.getCount());
                if (moved > 0) {
                    cursor.grow(moved);
                    inventory.removeItem(index, moved);
                }
            }
        }
        inventory.setChanged();
        if (!patterns && !ItemStack.isSameItemSameComponents(before, inventory.getItem(index))) {
            state.work().patternInputs.set(index, null);
            state.work().context(TerminalPatternContext.EMPTY);
            state.work().recipeIndex(0);
            if (index == 0 && state.workspace() == TerminalWorkspace.ANVIL) {
                ItemStack left = inventory.getItem(0);
                state.work().name(left.isEmpty() ? "" : left.getHoverName().getString());
                state.work().boost(0);
            }
        }
    }

    public static String ghost(ServerPlayer player, TerminalPlayerState state, CompoundTag data) {
        String group = data.getString("inventory");
        boolean inputs = group.equals("inputs");
        boolean outputs = group.equals("outputs");
        if (inputs && !state.workspace().supportsEncoding() || outputs && state.workspace() != TerminalWorkspace.PROCESSING)
            throw new IllegalArgumentException("The active page does not support this configuration inventory");
        TerminalGhostInventory inventory = switch (group) {
            case "inputs" -> state.work().patternInputs;
            case "outputs" -> state.work().patternOutputs;
            case "pickup" -> state.magnet().pickup;
            case "insert" -> state.magnet().insert;
            default -> throw new IllegalArgumentException("Unknown terminal ghost inventory");
        };
        int index = data.getInt("index");
        if (index < 0 || index >= inventory.size()) throw new IllegalArgumentException("Invalid terminal ghost index");
        if (inputs && state.workspace() != TerminalWorkspace.PROCESSING) {
            ItemStack physical = state.work().inputs.getItem(index);
            if (!physical.isEmpty()) {
                if (TerminalNetworkStorage.inventoryCapacity(player, physical) < physical.getCount()) return "inventory_full";
                ItemStack returned = state.work().inputs.removeItemNoUpdate(index);
                player.getInventory().add(returned);
                if (!returned.isEmpty()) throw new IllegalStateException("Terminal configuration lost reserved return capacity");
            }
        }
        if (data.getInt("button") == 1 || data.contains("amount", Tag.TAG_LONG) && data.getLong("amount") == 0) {
            inventory.set(index, null);
            if (data.getString("inventory").equals("inputs") || data.getString("inventory").equals("outputs"))
                state.work().context(TerminalPatternContext.EMPTY);
            state.work().recipeIndex(0);
            return "";
        }
        GenericStack resource;
        if (data.contains("stack", Tag.TAG_COMPOUND))
            resource = GenericStack.readTag(player.registryAccess(), data.getCompound("stack"));
        else {
            resource = GenericStack.fromItemStack(player.containerMenu.getCarried());
            if (resource == null) resource = inventory.get(index);
        }
        if (resource == null) return "";
        long amount = data.contains("amount", Tag.TAG_LONG) ? data.getLong("amount") : resource.amount();
        if (amount <= 0) throw new IllegalArgumentException("Invalid ghost amount");
        if (inputs && state.workspace() != TerminalWorkspace.PROCESSING) {
            if (!(resource.what() instanceof AEItemKey)) throw new IllegalArgumentException("Vanilla recipes require item ingredients");
            amount = 1;
        }
        inventory.set(index, new GenericStack(resource.what(), amount));
        if (data.getString("inventory").equals("inputs") || data.getString("inventory").equals("outputs"))
            state.work().context(TerminalPatternContext.EMPTY);
        state.work().recipeIndex(0);
        return "";
    }
}
