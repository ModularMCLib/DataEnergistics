package com.fish_dan_.data_energistics.client.terminal.encoding;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.common.terminal.player.encoding.TerminalPatternContext;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.slot.FakeSlot;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Both viewers transfer ingredients and identity together; catalyst roles are never treated as inputs. */
@NullMarked
public final class TerminalViewerRecipeTransfer {

    private TerminalViewerRecipeTransfer() {}

    public static @Nullable GenericStack choose(AbstractContainerMenu menu, ObjectList<GenericStack> alternatives, long amount) {
        GenericStack selected = null;
        long best = -1;
        for (var stack : alternatives) {
            long rank = stack.what() instanceof AEItemKey key ? TerminalOverlayClient.ingredientRank(menu, key) : 0;
            if (rank > best) {
                selected = new GenericStack(stack.what(), amount > 0 ? amount : stack.amount());
                best = rank;
            }
        }
        return selected;
    }

    /** The viewer's own handler supplies slot order; only physical item inputs are eligible for network refill. */
    public static @Nullable CompoundTag hostTargets(AbstractContainerMenu menu, @Nullable Recipe<?> recipe,
                                                    ObjectList<@Nullable Slot> slots, ObjectList<@Nullable GenericStack> inputs) {
        if (slots.isEmpty() || inputs.size() > slots.size()) return null;
        var player = Minecraft.getInstance().player;
        ListTag targets = new ListTag();
        for (int index = 0; index < slots.size(); index++) {
            int source = index;
            if (slots.size() == 9 && inputs.size() != 9 && recipe instanceof ShapedRecipe shaped) {
                int row = index / 3;
                int column = index % 3;
                source = row < shaped.getHeight() && column < shaped.getWidth() ? row * shaped.getWidth() + column : -1;
            }
            GenericStack input = source >= 0 && source < inputs.size() ? inputs.get(source) : null;
            Slot slot = slots.get(index);
            if (slot == null) {
                if (input != null) return null;
                continue;
            }
            if (slot.index < 0 || slot.index >= menu.slots.size() || menu.getSlot(slot.index) != slot ||
                    slot.container == player.getInventory() || slot instanceof FakeSlot || slot instanceof ResultSlot || !slot.isActive())
                return null;
            if (input != null && (!(input.what() instanceof AEItemKey key) || !slot.mayPlace(key.toStack(1)))) return null;
            CompoundTag target = new CompoundTag();
            target.putInt("slot", slot.index);
            target.put("stack", GenericStack.writeTag(player.registryAccess(), input));
            targets.add(target);
        }
        if (targets.isEmpty()) return null;
        CompoundTag data = new CompoundTag();
        data.put("targets", targets);
        return data;
    }

    public static @Nullable CompoundTag prepare(AbstractContainerMenu menu,
                                                @Nullable Recipe<?> recipe,
                                                ObjectList<@Nullable GenericStack> inputs,
                                                ObjectList<@Nullable GenericStack> outputs,
                                                ResourceLocation type, @Nullable ResourceLocation recipeId) {
        if (TerminalOverlayClient.encodingWorkspace(menu) == null) return null;
        TerminalWorkspace workspace = recipe != null && recipe.getType() == RecipeType.CRAFTING ? TerminalWorkspace.CRAFTING :
                recipe != null && recipe.getType() == RecipeType.SMITHING ? TerminalWorkspace.SMITHING :
                        recipe != null && recipe.getType() == RecipeType.STONECUTTING ? TerminalWorkspace.STONECUTTING : TerminalWorkspace.PROCESSING;
        if (inputs.size() > workspace.patternInputSlots() || outputs.size() > workspace.patternOutputSlots()) return null;
        var registries = Minecraft.getInstance().level.registryAccess();
        ListTag inputData = new ListTag();
        for (int index = 0; index < workspace.patternInputSlots(); index++) {
            int source = index;
            if (workspace == TerminalWorkspace.CRAFTING && recipe instanceof ShapedRecipe shaped && inputs.size() != 9) {
                int row = index / 3;
                int column = index % 3;
                source = row < shaped.getHeight() && column < shaped.getWidth() ? row * shaped.getWidth() + column : -1;
            }
            inputData.add(GenericStack.writeTag(registries, source >= 0 && source < inputs.size() ? inputs.get(source) : null));
        }
        ListTag outputData = new ListTag();
        for (int index = 0; index < workspace.patternOutputSlots(); index++)
            outputData.add(GenericStack.writeTag(registries, index < outputs.size() ? outputs.get(index) : null));
        CompoundTag data = new CompoundTag();
        data.putString("workspace", workspace.id());
        data.put("inputs", inputData);
        data.put("outputs", outputData);
        data.put("context", new TerminalPatternContext(type, recipeId).save());
        return data;
    }
}
