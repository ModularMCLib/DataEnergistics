package com.fish_dan_.data_energistics.integration.technology.embers.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

import com.rekindled.embers.RegistryManager;
import com.rekindled.embers.api.tile.IBin;
import com.rekindled.embers.blockentity.AlchemyPedestalBlockEntity;
import com.rekindled.embers.blockentity.AlchemyPedestalTopBlockEntity;
import com.rekindled.embers.blockentity.AlchemyTabletBlockEntity;
import com.rekindled.embers.recipe.AlchemyContext;
import com.rekindled.embers.recipe.IAlchemyRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;

/** Places an Embers alchemy tablet recipe into the native tablet and pedestal inventories. */
public final class AlchemyTableAdapter implements PackagedMachineAdapter {

    public static final String SELECTED_RECIPE = "data_energistics_embers_recipe";
    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("embers", "alchemy");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("embers_alchemy_table");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof AlchemyTabletBlockEntity;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, position) || !(level.getBlockEntity(position) instanceof AlchemyTabletBlockEntity tablet)) return null;
        if (!tablet.inventory.getStackInSlot(0).isEmpty() || tablet.progress != 0 || tablet.outputMode || level.getBlockEntity(position.below()) instanceof IBin) return null;
        var holders = level.getRecipeManager().byKey(recipeId);
        if (holders.isEmpty() || !(holders.get().value() instanceof IAlchemyRecipe recipe) || recipe.getType() != RegistryManager.ALCHEMY.get()) return null;
        var remaining = new KeyCounter();
        for (var counter : inputs) for (var entry : counter) remaining.add(entry.getKey(), entry.getLongValue());
        ItemStack center = take(remaining, recipe.getCenterInput());
        if (center.isEmpty()) return null;
        var pedestals = emptyPedestals(level, position);
        var code = recipe.getCode(level.getSeed());
        if (code.size() != recipe.getInputs().size() || pedestals.size() < code.size()) return null;
        var plan = new ListTag();
        var contents = new ArrayList<IAlchemyRecipe.PedestalContents>();
        var returns = new ObjectArrayList<ItemStack>();
        for (int index = 0; index < recipe.getInputs().size(); index++) {
            ItemStack input = take(remaining, recipe.getInputs().get(index));
            if (input.isEmpty()) return null;
            ItemStack aspect = take(remaining, code.get(index));
            if (aspect.isEmpty()) return null;
            var slot = new CompoundTag();
            slot.putLong("position", pedestals.get(index).getBlockPos().asLong());
            slot.put("input", input.saveOptional(level.registryAccess()));
            slot.put("aspect", aspect.saveOptional(level.registryAccess()));
            plan.add(slot);
            contents.add(new IAlchemyRecipe.PedestalContents(aspect, input));
            returns.add(aspect.copy());
        }
        if (hasRemaining(remaining)) return null;
        var context = new AlchemyContext(center, contents, level.getSeed());
        if (!recipe.matchesCorrect(context, level)) return null;
        ItemStack result = recipe.getResultItem();
        if (result.isEmpty() || !PackagedOutputMatching.matchesWithAdditionalReturns(pattern, result, returns)) return null;
        var progress = new CompoundTag();
        progress.put("center", center.save(level.registryAccess()));
        progress.put("pedestals", plan);
        progress.put("result", result.save(level.registryAccess()));
        return progress;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var result = new ObjectArrayList<BlockPos>();
        result.add(position);
        ListTag list = preparation.getList("pedestals", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            BlockPos top = BlockPos.of(list.getCompound(index).getLong("position"));
            result.add(top);
            result.add(top.below());
        }
        return result;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!recognizes(operation.level(), operation.position()) || !(operation.level().getBlockEntity(operation.position()) instanceof AlchemyTabletBlockEntity tablet)) return false;
        CompoundTag progress = operation.progress();
        ListTag list = progress.getList("pedestals", Tag.TAG_COMPOUND);
        ItemStack expected = read(operation, "result");
        if (!progress.getBoolean("loaded")) {
            return deliverNext(operation, tablet, list);
        }
        IAlchemyRecipe recipe = recipe(operation);
        if (tablet.progress > 0) {
            if (tablet.cachedRecipe != null && tablet.cachedRecipe != recipe) throw new IllegalStateException("Embers tablet selected another alchemy recipe");
            return false;
        }
        if (!tablet.outputMode) return false;
        ItemStack actual = tablet.inventory.getStackInSlot(0);
        if (!PackagedOutputMatching.matches(operation, expected, actual)) return false;
        for (int index = 0; index < list.size(); index++) {
            CompoundTag slot = list.getCompound(index);
            BlockEntityBase pedestal = base(operation.level(), BlockPos.of(slot.getLong("position")).below());
            if (pedestal == null) return false;
            ItemStack aspect = ItemStack.parseOptional(operation.level().registryAccess(), slot.getCompound("aspect"));
            if (!ItemStack.matches(aspect, pedestal.inventory().getStackInSlot(0))) {
                throw new IllegalStateException("Embers aspect changed during the packaged operation");
            }
        }
        ItemStack extracted = tablet.inventory.extractItem(0, actual.getCount(), false);
        operation.returned(AEItemKey.of(extracted), extracted.getCount());
        for (int index = 0; index < list.size(); index++) {
            CompoundTag slot = list.getCompound(index);
            BlockEntityBase pedestal = base(operation.level(), BlockPos.of(slot.getLong("position")).below());
            ItemStack aspect = pedestal.inventory().extractItem(0, 1, false);
            operation.returned(AEItemKey.of(aspect), aspect.getCount());
        }
        tablet.getPersistentData().remove(SELECTED_RECIPE);
        tablet.setChanged();
        operation.complete();
        return true;
    }

    private static boolean deliverNext(PackagedMachineOperation operation, AlchemyTabletBlockEntity tablet, ListTag plan) {
        int cursor = operation.progress().getInt("cursor");
        int peripheralSteps = Math.multiplyExact(plan.size(), 2);
        if (cursor < peripheralSteps) {
            CompoundTag slot = plan.getCompound(cursor / 2);
            BlockPos topPosition = BlockPos.of(slot.getLong("position"));
            boolean aspectStep = cursor % 2 == 0;
            ItemStack stack = ItemStack.parseOptional(operation.level().registryAccess(), slot.getCompound(aspectStep ? "aspect" : "input"));
            ItemStackHandler inventory;
            if (aspectStep) {
                BlockEntityBase base = base(operation.level(), topPosition.below());
                if (base == null) return false;
                inventory = base.inventory();
            } else {
                if (!(operation.level().getBlockEntity(topPosition) instanceof AlchemyPedestalTopBlockEntity top)) return false;
                inventory = top.inventory;
            }
            return insertStep(operation, inventory, stack, cursor + 1);
        }
        if (cursor == peripheralSteps) {
            ItemStack center = read(operation, "center");
            tablet.cachedRecipe = recipe(operation);
            if (!insertStep(operation, tablet.inventory, center, cursor + 1)) return false;
            tablet.getPersistentData().putString(SELECTED_RECIPE, operation.recipeId().toString());
            tablet.setChanged();
            operation.progress().putBoolean("loaded", true);
            operation.changed();
            return true;
        }
        throw new IllegalArgumentException("Invalid Embers delivery cursor");
    }

    @Override
    public boolean recoverRemoved(PackagedMachineOperation operation) {
        for (BlockPos position : occupiedPositions(operation.level(), operation.position(), operation.progress())) {
            if (!operation.level().isLoaded(position)) return false;
        }
        ListTag plan = operation.progress().getList("pedestals", Tag.TAG_COMPOUND);
        int delivered = operation.progress().getInt("cursor");
        if (operation.level().getBlockEntity(operation.position()) instanceof AlchemyTabletBlockEntity tablet) {
            tablet.progress = 0;
            tablet.cachedRecipe = null;
            tablet.getPersistentData().remove(SELECTED_RECIPE);
            if (delivered > plan.size() * 2) returnInventory(operation, tablet.inventory);
            tablet.setChanged();
        }
        for (int index = 0; index < plan.size(); index++) {
            BlockPos topPosition = BlockPos.of(plan.getCompound(index).getLong("position"));
            if (delivered > index * 2) {
                BlockEntityBase pedestal = base(operation.level(), topPosition.below());
                if (pedestal != null) returnInventory(operation, pedestal.inventory());
            }
            if (delivered > index * 2 + 1 && operation.level().getBlockEntity(topPosition) instanceof AlchemyPedestalTopBlockEntity top) {
                returnInventory(operation, top.inventory);
            }
        }
        for (ItemEntity entity : operation.level().getEntitiesOfClass(ItemEntity.class, new AABB(operation.position()).inflate(16),
                entity -> PackagedEntityCapture.ownedBy(entity, operation.id()))) {
            ItemStack stack = entity.getItem().copy();
            entity.discard();
            operation.returned(AEItemKey.of(stack), stack.getCount());
        }
        return true;
    }

    private static void returnInventory(PackagedMachineOperation operation, ItemStackHandler inventory) {
        ItemStack actual = inventory.extractItem(0, inventory.getStackInSlot(0).getCount(), false);
        if (!actual.isEmpty()) operation.returned(AEItemKey.of(actual), actual.getCount());
    }

    private static boolean insertStep(PackagedMachineOperation operation, ItemStackHandler inventory, ItemStack stack, int nextCursor) {
        AEItemKey key = AEItemKey.of(stack);
        if (operation.available(key).compareTo(BigInteger.valueOf(stack.getCount())) < 0 || !inventory.getStackInSlot(0).isEmpty()) return false;
        if (!inventory.insertItem(0, stack.copy(), true).isEmpty()) return false;
        ItemStack rejected = inventory.insertItem(0, stack.copy(), false);
        if (!rejected.isEmpty()) throw new IllegalStateException("Embers inventory insertion changed after simulation");
        operation.delivered(key, stack.getCount());
        operation.progress().putInt("cursor", nextCursor);
        operation.changed();
        return true;
    }

    private static IAlchemyRecipe recipe(PackagedMachineOperation operation) {
        var holder = operation.level().getRecipeManager().byKey(operation.recipeId());
        if (holder.isEmpty() || !(holder.get().value() instanceof IAlchemyRecipe recipe) || recipe.getType() != RegistryManager.ALCHEMY.get()) {
            throw new IllegalStateException("Embers alchemy recipe changed after preparation");
        }
        return recipe;
    }

    private static ItemStack take(KeyCounter counters, Ingredient ingredient) {
        for (var entry : counters) if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key && ingredient.test(key.toStack())) {
            counters.remove(entry.getKey(), 1);
            return key.toStack();
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasRemaining(KeyCounter counters) {
        for (var entry : counters) if (entry.getLongValue() != 0) return true;
        return false;
    }

    private static ObjectList<AlchemyPedestalTopBlockEntity> emptyPedestals(ServerLevel level, BlockPos center) {
        var result = new ObjectArrayList<AlchemyPedestalTopBlockEntity>();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            BlockPos topPosition = center.offset(x, 1, z);
            if (!level.isLoaded(topPosition) || !level.isLoaded(topPosition.below())) return ObjectList.of();
            if (!(level.getBlockEntity(topPosition) instanceof AlchemyPedestalTopBlockEntity top)) continue;
            BlockEntityBase base = base(level, topPosition.below());
            if (base == null || !top.inventory.getStackInSlot(0).isEmpty() || !base.inventory().getStackInSlot(0).isEmpty()) return ObjectList.of();
            result.add(top);
        }
        return result;
    }

    private static ItemStack read(PackagedMachineOperation operation, String key) {
        return ItemStack.parse(operation.level().registryAccess(), operation.progress().getCompound(key)).orElseThrow();
    }

    private static BlockEntityBase base(ServerLevel level, BlockPos position) {
        if (level.getBlockEntity(position) instanceof AlchemyPedestalBlockEntity pedestal) return new BlockEntityBase(pedestal.inventory);
        return null;
    }

    private record BlockEntityBase(ItemStackHandler inventory) {}
}
