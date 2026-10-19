package com.fish_dan_.data_energistics.common.terminal.player.transfer;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.slot.FakeSlot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ResultSlot;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

/** Resolves viewer-declared input positions against the server's current host menu and slot rules. */
@NullMarked
public final class TerminalViewerTransfer {

    private TerminalViewerTransfer() {}

    public static ObjectList<TerminalIngredientSupply.Target> targets(ServerPlayer player, CompoundTag data) {
        var entries = data.getList("targets", Tag.TAG_COMPOUND);
        if (entries.isEmpty() || entries.size() > 256) throw new IllegalArgumentException("Invalid terminal recipe target count");
        var result = new ObjectArrayList<TerminalIngredientSupply.Target>();
        var seen = new IntOpenHashSet();
        for (Tag entry : entries) {
            CompoundTag tag = (CompoundTag) entry;
            int index = tag.getInt("slot");
            if (index < 0 || index >= player.containerMenu.slots.size() || !seen.add(index))
                throw new IllegalArgumentException("Invalid terminal recipe target slot");
            var slot = player.containerMenu.getSlot(index);
            if (slot.container == player.getInventory() || slot instanceof FakeSlot || slot instanceof ResultSlot || !slot.isActive())
                throw new IllegalArgumentException("Terminal recipe target is not a host input");
            GenericStack stack = GenericStack.readTag(player.registryAccess(), tag.getCompound("stack"));
            if (stack == null) {
                if (slot.hasItem() && !slot.mayPlace(slot.getItem())) throw new IllegalArgumentException("Cannot clear a result slot");
                result.add(new TerminalIngredientSupply.Target(slot, null, 0));
            } else {
                if (!(stack.what() instanceof AEItemKey key) || stack.amount() <= 0 || stack.amount() > Integer.MAX_VALUE)
                    throw new IllegalArgumentException("Invalid physical recipe ingredient");
                result.add(new TerminalIngredientSupply.Target(slot, key, (int) stack.amount()));
            }
        }
        return result;
    }
}
