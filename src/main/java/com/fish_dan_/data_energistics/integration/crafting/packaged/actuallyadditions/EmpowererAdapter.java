package com.fish_dan_.data_energistics.integration.crafting.packaged.actuallyadditions;

import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import de.ellpeck.actuallyadditions.mod.crafting.EmpowererRecipe;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityDisplayStand;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityEmpowerer;
import de.ellpeck.actuallyadditions.mod.util.ItemStackHandlerAA;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/** The four real display stands supply power and consume the modifiers through the empowerer's normal tick. */
final class EmpowererAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("actuallyadditions", "empowering");

    @Override
    public ResourceLocation id() {
        return TYPE;
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE, ResourceLocation.fromNamespaceAndPath("actuallyadditions", "empowerer"));
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof TileEntityEmpowerer;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var positions = new ObjectArrayList<BlockPos>();
        positions.add(position);
        for (int index = 0; index < 4; index++) positions.add(position.relative(Direction.from2DDataValue(index), 3));
        return positions;
    }

    private static @Nullable ObjectList<ItemStackHandlerAA> inventories(ServerLevel level, BlockPos position) {
        if (!level.isLoaded(position) || !(level.getBlockEntity(position) instanceof TileEntityEmpowerer core)) return null;
        var inventories = new ObjectArrayList<ItemStackHandlerAA>(5);
        inventories.add(core.inv);
        for (int index = 0; index < 4; index++) {
            var standPos = position.relative(Direction.from2DDataValue(index), 3);
            if (!level.isLoaded(standPos) || !(level.getBlockEntity(standPos) instanceof TileEntityDisplayStand stand)) return null;
            inventories.add(stand.inv);
        }
        return inventories;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        var inventories = inventories(level, position);
        if (inventories == null || inventories.stream().anyMatch(inv -> !inv.getStackInSlot(0).isEmpty())) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof EmpowererRecipe recipe)) return null;
        var ingredients = ObjectArrayList.of(recipe.getInput(), recipe.getStandOne(), recipe.getStandTwo(),
                recipe.getStandThree(), recipe.getStandFour());
        var stacks = PackagedIngredientAssignment.match(ingredients, inputs);
        if (stacks == null || !matches(recipeId, stacks)) return null;
        var output = recipe.getOutput();
        if (!PackagedIngredientAssignment.outputsMatch(pattern, ObjectArrayList.of(output))) return null;
        var progress = new CompoundTag();
        var encoded = new ListTag();
        for (int index = 0; index < stacks.size(); index++) {
            var stack = stacks.get(index);
            if (!inventories.get(index).insertItem(0, stack, true).isEmpty()) return null;
            encoded.add(stack.save(level.registryAccess()));
        }
        progress.put("inputs", encoded);
        progress.put("output", output.save(level.registryAccess()));
        return progress;
    }

    private static boolean matches(ResourceLocation recipeId, ObjectList<ItemStack> stacks) {
        var actual = TileEntityEmpowerer.findMatchingRecipe(stacks.get(0), stacks.get(1), stacks.get(2), stacks.get(3), stacks.get(4));
        return actual != null && actual.id().equals(recipeId);
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        var inventories = inventories(operation.level(), operation.position());
        if (inventories == null) return false;
        var progress = operation.progress();
        var expected = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("output")).orElseThrow();
        if (progress.getBoolean("delivered")) {
            // The modifiers disappearing proves this cycle completed even if input and output are the same item.
            for (int index = 1; index < 5; index++) if (!inventories.get(index).getStackInSlot(0).isEmpty()) return false;
            var actual = inventories.getFirst().getStackInSlot(0);
            if (!PackagedOutputMatching.matches(operation, expected, actual)) return false;
            // The public manual-extraction path permits a result that is also an input to another empowerer recipe.
            var extracted = inventories.getFirst().extractItem(0, actual.getCount(), false, false);
            if (extracted.isEmpty()) return false;
            operation.returned(AEItemKey.of(extracted), extracted.getCount());
            operation.complete();
            return true;
        }
        for (var inventory : inventories) if (!inventory.getStackInSlot(0).isEmpty()) return false;
        var encoded = progress.getList("inputs", Tag.TAG_COMPOUND);
        if (encoded.size() != 5) throw new IllegalArgumentException("Invalid persisted empowerer inputs");
        var stacks = new ObjectArrayList<ItemStack>(5);
        for (int index = 0; index < 5; index++) {
            var stack = ItemStack.parse(operation.level().registryAccess(), encoded.getCompound(index)).orElseThrow();
            if (!inventories.get(index).insertItem(0, stack, true).isEmpty()) return false;
            stacks.add(stack);
        }
        if (!matches(operation.recipeId(), stacks)) throw new IllegalStateException("Empowerer recipe changed after admission");
        var current = operation.level().getRecipeManager().byKey(operation.recipeId());
        if (current.isEmpty() || !(current.get().value() instanceof EmpowererRecipe recipe) || !PackagedOutputMatching.matches(operation, expected, recipe.getOutput())) {
            throw new IllegalStateException("Empowerer output changed after admission");
        }
        // Center last: inserting the trigger cannot expose an incomplete set of modifiers to the machine.
        for (int step = 1; step <= 5; step++) {
            int index = step % 5;
            var stack = stacks.get(index);
            var remainder = inventories.get(index).insertItem(0, stack.copy(), false);
            int accepted = stack.getCount() - remainder.getCount();
            if (accepted > 0) operation.delivered(AEItemKey.of(stack), accepted);
            if (!remainder.isEmpty()) throw new IllegalStateException("Empowerer inventory changed after simulation");
        }
        progress.putBoolean("delivered", true);
        operation.changed();
        return true;
    }
}
