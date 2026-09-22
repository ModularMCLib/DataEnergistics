package com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;
import com.fish_dan_.data_energistics.mixin.magic.naturesaura.NatureAltarRecipeAccessor;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import de.ellpeck.naturesaura.blocks.ModBlocks;
import de.ellpeck.naturesaura.blocks.multi.Multiblocks;
import de.ellpeck.naturesaura.blocks.tiles.BlockEntityNatureAltar;
import de.ellpeck.naturesaura.recipes.AltarRecipe;
import de.ellpeck.naturesaura.recipes.ModRecipes;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/** Feeds one real Nature's Aura altar cycle and collects the native result from its single slot. */
public final class NatureAltarAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation RECIPE_TYPE = ResourceLocation.fromNamespaceAndPath("naturesaura", "altar");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("naturesaura_altar");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(RECIPE_TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockState(position).is(ModBlocks.NATURE_ALTAR) && Multiblocks.ALTAR.forEach(position, '\0', (part, matcher) -> level.isLoaded(part)) && Multiblocks.ALTAR.isComplete(level, position);
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, position) || !(level.getBlockEntity(position) instanceof BlockEntityNatureAltar altar) || !altar.isComplete || !altar.items.getStackInSlot(0).isEmpty()) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof AltarRecipe recipe) || recipe.getType() != ModRecipes.ALTAR_TYPE) return null;
        var supplied = new KeyCounter();
        for (var counter : inputs) for (var entry : counter) {
            if (!(entry.getKey() instanceof AEItemKey) || entry.getLongValue() <= 0) return null;
            supplied.add(entry.getKey(), entry.getLongValue());
        }
        if (supplied.size() != 1 || supplied.iterator().next().getLongValue() != 1) return null;
        ItemStack input = ((AEItemKey) supplied.iterator().next().getKey()).toStack();
        if (input.getCount() != 1 || !recipe.input.test(input) || pattern.getOutputs().size() != 1 || !PackagedOutputMatching.matches(pattern, recipe.output, recipe.output.getCount())) return null;
        var selected = ((NatureAltarRecipeAccessor) altar).dataEnergistics$recipeFor(input);
        if (selected == null || !selected.id().equals(recipeId) || ItemStack.matches(input, recipe.output)) return null;
        var progress = new CompoundTag();
        progress.put("input", input.save(level.registryAccess()));
        progress.put("output", recipe.output.save(level.registryAccess()));
        var catalysts = new ObjectArrayList<BlockPos>();
        for (int x = -2; x <= 2; x += 4) for (int z = -2; z <= 2; z += 4) catalysts.add(position.offset(x, 1, z));
        if (catalysts.stream().anyMatch(part -> !level.isLoaded(part))) return null;
        progress.putLongArray("changing_positions", catalysts.stream().mapToLong(BlockPos::asLong).toArray());
        return progress;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var positions = new ObjectArrayList<BlockPos>();
        Multiblocks.ALTAR.forEach(position, '\0', (part, matcher) -> {
            positions.add(part.immutable());
            return true;
        });
        for (long catalyst : preparation.getLongArray("changing_positions")) positions.add(BlockPos.of(catalyst));
        return positions;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!recognizes(operation.level(), operation.position()) || !(operation.level().getBlockEntity(operation.position()) instanceof BlockEntityNatureAltar altar)) return false;
        ItemStack expected = read(operation, "output");
        if (operation.progress().getBoolean("waiting")) {
            ItemStack actual = altar.items.getStackInSlot(0).copy();
            if (!PackagedOutputMatching.matches(operation, expected, actual)) return false;
            altar.items.setStackInSlot(0, ItemStack.EMPTY);
            operation.returned(AEItemKey.of(actual), actual.getCount());
            operation.complete();
            return true;
        }
        ItemStack input = read(operation, "input");
        var selected = ((NatureAltarRecipeAccessor) altar).dataEnergistics$recipeFor(input);
        if (selected == null || !selected.id().equals(operation.recipeId())) return false;
        AEItemKey key = AEItemKey.of(input);
        if (operation.available(key).compareTo(BigInteger.ONE) < 0) throw new IllegalStateException("Missing Nature's Aura altar input");
        if (!altar.items.insertItem(0, input.copy(), true).isEmpty()) return false;
        altar.items.insertItem(0, input.copy(), false);
        operation.delivered(key, 1);
        operation.progress().putBoolean("waiting", true);
        operation.changed();
        return true;
    }

    @Override
    public boolean recoverRemoved(PackagedMachineOperation operation) {
        if (!operation.level().isLoaded(operation.position())) return false;
        if (operation.level().getBlockEntity(operation.position()) instanceof BlockEntityNatureAltar altar && operation.progress().getBoolean("waiting")) {
            ItemStack stack = altar.items.getStackInSlot(0).copy();
            if (!stack.isEmpty()) {
                if (!ItemStack.matches(stack, read(operation, "input")) && !ItemStack.matches(stack, read(operation, "output"))) return false;
                altar.items.setStackInSlot(0, ItemStack.EMPTY);
                operation.returned(AEItemKey.of(stack), stack.getCount());
            }
        }
        for (var entity : operation.level().getEntitiesOfClass(ItemEntity.class, new AABB(operation.position()).inflate(4), item -> PackagedEntityCapture.ownedBy(item, operation.id()))) {
            var stack = entity.getItem().copy();
            entity.discard();
            operation.returned(AEItemKey.of(stack), stack.getCount());
        }
        return true;
    }

    private static ItemStack read(PackagedMachineOperation operation, String key) {
        return ItemStack.parse(operation.level().registryAccess(), operation.progress().getCompound(key))
                .orElseThrow(() -> new IllegalArgumentException("Invalid Nature's Aura altar item: " + key));
    }
}
