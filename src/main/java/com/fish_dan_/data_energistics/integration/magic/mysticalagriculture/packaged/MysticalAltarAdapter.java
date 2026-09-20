package com.fish_dan_.data_energistics.integration.magic.mysticalagriculture.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.blakebr0.cucumber.inventory.BaseItemStackHandler;
import com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity;
import com.blakebr0.mysticalagriculture.tileentity.AwakeningAltarTileEntity;
import com.blakebr0.mysticalagriculture.tileentity.AwakeningPedestalTileEntity;
import com.blakebr0.mysticalagriculture.tileentity.EssenceVesselTileEntity;
import com.blakebr0.mysticalagriculture.tileentity.InfusionAltarTileEntity;
import com.blakebr0.mysticalagriculture.tileentity.InfusionPedestalTileEntity;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/** Delivers one cycle at a time and lets MA's own ticking, recipe and essence consumption produce every result. */
public final class MysticalAltarAdapter implements PackagedMachineAdapter {

    private final AltarKind kind;

    public MysticalAltarAdapter(AltarKind kind) {
        this.kind = kind;
    }

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("mystical_agriculture_" + this.kind.recipeType.getPath());
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(this.kind.recipeType);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && this.kind.recognizes(level.getBlockEntity(position));
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        Layout layout = layout(level, position);
        if (layout == null || !layout.empty()) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty()) return null;
        Recipe<CraftingInput> recipe = this.kind.recipe(holder.get().value());
        if (recipe == null) return null;
        AltarRecipePlan plan = AltarRecipePlan.prepare(level, recipe, pattern, inputs);
        if (plan == null) return null;
        var slots = new ListTag();
        for (int index = 0; index < 9; index++) {
            ItemStack input = plan.inputs().get(index);
            if (!input.isEmpty() && !layout.tiles().get(index).getInventory().insertItem(0, input, true).isEmpty()) return null;
            var slot = new CompoundTag();
            slot.putLong("position", layout.tiles().get(index).getBlockPos().asLong());
            slot.put("input", input.saveOptional(level.registryAccess()));
            slot.put("remaining", plan.remaining().get(index).saveOptional(level.registryAccess()));
            slots.add(slot);
        }
        var progress = new CompoundTag();
        progress.put("slots", slots);
        progress.put("result", plan.result().save(level.registryAccess()));
        progress.putLong("cycles", plan.cycles());
        return progress;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var positions = new ObjectArrayList<BlockPos>();
        positions.add(position);
        var slots = preparation.getList("slots", Tag.TAG_COMPOUND);
        for (int index = 0; index < slots.size(); index++) positions.add(BlockPos.of(slots.getCompound(index).getLong("position")));
        return positions;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        Layout layout = layout(operation.level(), operation.position());
        if (layout == null) return false;
        CompoundTag progress = operation.progress();
        ListTag slots = progress.getList("slots", Tag.TAG_COMPOUND);
        if (slots.size() != 9 || progress.getLong("cycles") <= 0) {
            throw new IllegalArgumentException("Invalid persisted MA altar plan");
        }
        for (int index = 0; index < 9; index++) {
            if (slots.getCompound(index).getLong("position") != layout.tiles().get(index).getBlockPos().asLong()) return false;
        }
        ItemStack result = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("result"))
                .orElseThrow(() -> new IllegalArgumentException("Missing MA altar output"));
        return progress.getBoolean("delivered") ? collect(operation, layout, slots, result) :
                deliver(operation, layout, slots, result);
    }

    private boolean deliver(PackagedMachineOperation operation, Layout layout, ListTag slots, ItemStack result) {
        if (!layout.empty()) return false;
        ObjectList<ItemStack> inputs = new ObjectArrayList<>();
        var required = new KeyCounter();
        for (int index = 0; index < 9; index++) {
            ItemStack stack = ItemStack.parseOptional(operation.level().registryAccess(), slots.getCompound(index).getCompound("input"));
            inputs.add(stack);
            if (!stack.isEmpty()) {
                required.add(AEItemKey.of(stack), stack.getCount());
                if (!layout.tiles().get(index).getInventory().insertItem(0, stack, true).isEmpty()) return false;
            }
        }
        for (var entry : required) {
            if (operation.available(entry.getKey()).compareTo(BigInteger.valueOf(entry.getLongValue())) < 0) {
                throw new IllegalStateException("MA altar plan exceeds remaining provider inputs");
            }
        }
        var holder = operation.level().getRecipeManager().byKey(operation.recipeId());
        Recipe<CraftingInput> recipe = holder.isEmpty() ? null : this.kind.recipe(holder.get().value());
        CraftingInput input = CraftingInput.of(3, 3, inputs);
        if (recipe == null || !recipe.matches(input, operation.level()) ||
                !PackagedOutputMatching.matches(operation, result, recipe.assemble(input, operation.level().registryAccess()))) {
            throw new IllegalStateException("MA altar recipe changed after accepting its inputs");
        }
        var remaining = recipe.getRemainingItems(input);
        if (remaining.size() != input.size()) throw new IllegalStateException("MA altar remainder shape changed");
        for (int index = 0; index < 9; index++) {
            ItemStack expected = ItemStack.parseOptional(operation.level().registryAccess(), slots.getCompound(index).getCompound("remaining"));
            ItemStack actual = index < remaining.size() ? remaining.get(index) : ItemStack.EMPTY;
            if (!ItemStack.matches(expected, actual)) throw new IllegalStateException("MA altar remainder changed");
        }
        // Finish the peripheral inputs before inserting the center, which can trigger a redstone-powered altar.
        for (int step = 1; step <= 9; step++) {
            int index = step % 9;
            ItemStack stack = inputs.get(index);
            if (stack.isEmpty()) continue;
            ItemStack rejected = layout.tiles().get(index).getInventory().insertItem(0, stack.copy(), false);
            int delivered = stack.getCount() - rejected.getCount();
            if (delivered > 0) operation.delivered(AEItemKey.of(stack), delivered);
            if (!rejected.isEmpty()) throw new IllegalStateException("MA altar insertion changed after simulation");
        }
        if (layout.altar() instanceof InfusionAltarTileEntity infusion) {
            if (infusion.getActiveRecipe() != recipe) throw new IllegalStateException("MA altar selected another recipe");
            infusion.activate();
        } else if (layout.altar() instanceof AwakeningAltarTileEntity awakening) {
            if (awakening.getActiveRecipe() != recipe) throw new IllegalStateException("MA altar selected another recipe");
            awakening.activate();
        }
        layout.altar().setChanged();
        operation.progress().putBoolean("delivered", true);
        operation.changed();
        return true;
    }

    private static boolean collect(PackagedMachineOperation operation, Layout layout, ListTag slots, ItemStack result) {
        BaseItemStackHandler altarInventory = layout.altar().getInventory();
        ItemStack output = altarInventory.getStackInSlot(1);
        if (output.isEmpty()) return false;
        if (!PackagedOutputMatching.matches(operation, result, output)) throw new IllegalStateException("Unexpected item in MA altar output");
        // Validate the entire completed cycle before taking anything; unrelated inserted items remain in the world.
        for (int index = 0; index < 9; index++) {
            ItemStack expected = ItemStack.parseOptional(operation.level().registryAccess(), slots.getCompound(index).getCompound("remaining"));
            if (!ItemStack.matches(expected, layout.tiles().get(index).getInventory().getStackInSlot(0))) {
                throw new IllegalStateException("MA altar remainder no longer belongs to this operation");
            }
        }
        harvest(operation, altarInventory, 1);
        for (var tile : layout.tiles()) harvest(operation, tile.getInventory(), 0);
        long remainingCycles = operation.progress().getLong("cycles") - 1;
        operation.progress().putLong("cycles", remainingCycles);
        operation.progress().putBoolean("delivered", false);
        operation.changed();
        if (remainingCycles == 0) operation.complete();
        return true;
    }

    private static void harvest(PackagedMachineOperation operation, BaseItemStackHandler inventory, int slot) {
        ItemStack expected = inventory.getStackInSlot(slot);
        if (expected.isEmpty()) return;
        int count = expected.getCount();
        // Cucumber's public container path permits reclaiming crafting remainders from input-only slots.
        ItemStack extracted = inventory.extractItem(slot, count, false, true);
        if (!extracted.isEmpty()) operation.returned(AEItemKey.of(extracted), extracted.getCount());
        if (extracted.getCount() != count) throw new IllegalStateException("MA altar return extraction was incomplete");
    }

    private @Nullable Layout layout(ServerLevel level, BlockPos position) {
        if (!recognizes(level, position)) return null;
        BlockEntity blockEntity = level.getBlockEntity(position);
        BaseInventoryTileEntity altar;
        ObjectList<BaseInventoryTileEntity> pedestals = new ObjectArrayList<>();
        ObjectList<BaseInventoryTileEntity> vessels = new ObjectArrayList<>();
        if (blockEntity instanceof InfusionAltarTileEntity infusion) {
            altar = infusion;
            for (BlockPos pedestalPosition : infusion.getPedestalPositions()) {
                if (!level.isLoaded(pedestalPosition)) return null;
                if (!(level.getBlockEntity(pedestalPosition) instanceof InfusionPedestalTileEntity pedestal)) return null;
                pedestals.add(pedestal);
            }
            if (pedestals.size() != 8) return null;
        } else if (blockEntity instanceof AwakeningAltarTileEntity awakening) {
            altar = awakening;
            for (BlockPos pedestalPosition : awakening.getPedestalPositions()) {
                if (!level.isLoaded(pedestalPosition)) return null;
                BlockEntity peripheral = level.getBlockEntity(pedestalPosition);
                if (peripheral instanceof AwakeningPedestalTileEntity pedestal) pedestals.add(pedestal);
                else if (peripheral instanceof EssenceVesselTileEntity vessel) vessels.add(vessel);
                else return null;
            }
            if (pedestals.size() != 4 || vessels.size() != 4) return null;
        } else return null;
        ObjectList<BaseInventoryTileEntity> tiles = new ObjectArrayList<>();
        tiles.add(altar);
        tiles.addAll(pedestals);
        tiles.addAll(vessels);
        return new Layout(altar, tiles);
    }

    private record Layout(BaseInventoryTileEntity altar, ObjectList<BaseInventoryTileEntity> tiles) {

        boolean empty() {
            if (!this.altar.getInventory().getStackInSlot(1).isEmpty()) return false;
            return this.tiles.stream().allMatch(tile -> tile.getInventory().getStackInSlot(0).isEmpty());
        }
    }
}
