package com.fish_dan_.data_energistics.client.terminal.encoding;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.common.terminal.player.encoding.TerminalPatternContext;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;

import appeng.api.stacks.GenericStack;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Both viewers transfer ingredients and identity together; catalyst roles are never treated as inputs. */
@NullMarked
public final class TerminalViewerRecipeTransfer {

    private TerminalViewerRecipeTransfer() {}

    public static @Nullable CompoundTag prepare(AbstractContainerMenu menu,
                                                @Nullable Recipe<?> recipe,
                                                ObjectList<@Nullable GenericStack> inputs,
                                                ObjectList<@Nullable GenericStack> outputs,
                                                ResourceLocation type, @Nullable ResourceLocation recipeId) {
        TerminalWorkspace workspace = TerminalOverlayClient.encodingWorkspace(menu);
        if (workspace == null || inputs.size() > workspace.patternInputSlots() || outputs.size() > workspace.patternOutputSlots()) return null;
        if (workspace != TerminalWorkspace.PROCESSING && (recipe == null ||
                recipe.getType() != switch (workspace) {
                    case CRAFTING -> RecipeType.CRAFTING;
                    case SMITHING -> RecipeType.SMITHING;
                    case STONECUTTING -> RecipeType.STONECUTTING;
                    default -> throw new IllegalStateException("Unsupported vanilla encoder");
                }))
            return null;
        var registries = Objects.requireNonNull(Minecraft.getInstance().level).registryAccess();
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
