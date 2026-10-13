package com.fish_dan_.data_energistics.integration.magic.astral.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedPatternInputCompletion;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationAdapter;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationContext;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationInspection;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationInspectionContext;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationPreparation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;
import com.fish_dan_.data_energistics.world.packaged.PackagedRecoveryJournal;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.AABB;

import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.recipe.infusion.InfusionRecipe;
import hellfirepvp.astralsorcery.common.tile.TileInfuser;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/** Delivers a single item and lets the native infuser own all liquid consumption and recipe progress. */
public final class AstralInfusionAdapter implements PackagedMachineAdapter, PackagedPatternInputCompletion, PatternUploadWorkstationAdapter {

    static final ResourceLocation RECIPE_TYPE = ResourceLocation.fromNamespaceAndPath("astralsorcery", "infusion");
    static final ResourceLocation WORKSTATION = ResourceLocation.fromNamespaceAndPath("astralsorcery", "infuser");
    private static final String DELIVERED = "delivered";

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("astral_sorcery_infusion");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(RECIPE_TYPE);
    }

    @Override
    public ObjectSet<ResourceLocation> workstationItemIds() {
        return ObjectSet.of(WORKSTATION);
    }

    @Override
    public PatternUploadWorkstationInspection inspect(PatternUploadWorkstationInspectionContext context) {
        return AstralInfusionUploadAdapter.inspect(context);
    }

    @Override
    public PatternUploadWorkstationPreparation prepare(PatternUploadWorkstationContext context) {
        return AstralInfusionUploadAdapter.prepare(context);
    }

    @Override
    public @Nullable ItemStack completeEncoding(ServerLevel level, ResourceLocation recipeId, ItemStack pattern) {
        var holder = recipe(level, recipeId);
        return holder == null ? null : AstralInfusionPatternEncoding.complete(holder.value(), pattern);
    }

    @Override
    public @Nullable ObjectList<GenericStack> completePatternInputs(ServerLevel level, ResourceLocation recipeId,
                                                                    ObjectList<GenericStack> inputs) {
        var holder = recipe(level, recipeId);
        return holder == null ? null : AstralInfusionPatternEncoding.completeInputs(holder.value(), inputs);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof TileInfuser;
    }

    @Override
    public boolean supportsRecipe(ServerLevel level, BlockPos position, ResourceLocation type, ResourceLocation recipeId) {
        var holder = recipe(level, recipeId);
        return RECIPE_TYPE.equals(type) && holder != null && recognizes(level, position) &&
                fluidLayoutMatches(level, position, holder.value());
    }

    @Override
    public long batchCapacity(ServerLevel level, BlockPos position, Direction face, ResourceLocation recipeId,
                              IPatternDetails pattern, KeyCounter[] prototype, long requestedCount) {
        return requestedCount > 0 ? 1 : 0;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face, ResourceLocation recipeId,
                                         IPatternDetails pattern, KeyCounter[] inputs) {
        var holder = recipe(level, recipeId);
        if (holder == null || !level.isLoaded(position) || !(level.getBlockEntity(position) instanceof TileInfuser infuser)) return null;
        if (!infuser.hasStructure() || !fluidLayoutMatches(level, position, holder.value()) ||
                infuser.getTileData().getActiveRecipe().isPresent() ||
                !infuser.getTileData().getInventory().getStackInSlot(0).isEmpty())
            return null;
        var assigned = PackagedIngredientAssignment.match(ObjectList.of(holder.value().getItemInput()), inputs);
        ItemStack output = holder.value().getOutput();
        if (assigned == null || output.isEmpty() || !PackagedOutputMatching.matches(pattern, output, output.getCount())) return null;
        var progress = new CompoundTag();
        progress.put("input", assigned.getFirst().save(level.registryAccess()));
        var outputs = new ListTag();
        outputs.add(output.save(level.registryAccess()));
        progress.put("outputs", outputs);
        progress.putBoolean(DELIVERED, false);
        return progress;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        ServerLevel level = operation.level();
        if (!loadedRadius(level, operation.position(), 2) ||
                !(level.getBlockEntity(operation.position()) instanceof TileInfuser infuser))
            return false;
        ItemStack input = input(operation);
        ItemStack present = infuser.getTileData().getInventory().getStackInSlot(0);
        if (operation.progress().getBoolean(DELIVERED)) {
            var active = infuser.getTileData().getActiveRecipe();
            if (active.isPresent()) {
                if (!operation.recipeId().equals(active.get().getRecipeId())) {
                    throw new IllegalStateException("Astral infuser started another recipe after delivery");
                }
                return false;
            }
            if (present.isEmpty()) return collectOutput(operation);
            if (!ItemStack.matches(input, present)) throw new IllegalStateException("Astral infuser input changed after delivery");
            return start(operation, infuser);
        }
        var holder = recipe(level, operation.recipeId());
        if (holder == null || !present.isEmpty() || infuser.getTileData().getActiveRecipe().isPresent() ||
                !infuser.hasStructure() || !fluidLayoutMatches(level, operation.position(), holder.value()))
            return false;
        AEItemKey key = AEItemKey.of(input);
        if (operation.available(key).longValueExact() < input.getCount()) {
            throw new IllegalStateException("Astral infusion operation no longer owns its prepared input");
        }
        var inventory = infuser.getTileData().getInventory();
        if (!inventory.insertItem(0, input.copy(), true).isEmpty()) return false;
        if (!inventory.insertItem(0, input.copy(), false).isEmpty()) {
            throw new IllegalStateException("Astral infuser rejected its simulated item insertion");
        }
        operation.delivered(key, input.getCount());
        operation.progress().putBoolean(DELIVERED, true);
        operation.changed();
        start(operation, infuser);
        return true;
    }

    @Override
    public boolean recoverRemoved(PackagedMachineOperation operation) {
        ServerLevel level = operation.level();
        if (!operation.progress().getBoolean("delivery_started")) return true;
        if (!loadedRadius(level, operation.position(), 8)) return false;
        ItemStack input = input(operation);
        ObjectList<ItemStack> expected = outputs(operation);
        ObjectList<ItemEntity> entities = ownedEntities(operation);
        for (ItemEntity entity : entities) {
            ItemStack stack = entity.getItem();
            if (!ItemStack.matches(input, stack) && !PackagedOutputMatching.acceptsPartial(operation, expected, ObjectList.of(stack))) {
                return false;
            }
        }
        if (level.getBlockEntity(operation.position()) instanceof TileInfuser infuser) {
            if (infuser.getTileData().getActiveRecipe().filter(active -> !operation.recipeId().equals(active.getRecipeId())).isPresent()) return false;
            var inventory = infuser.getTileData().getInventory();
            ItemStack present = inventory.getStackInSlot(0);
            if (!present.isEmpty() && !ItemStack.matches(input, present)) return false;
            infuser.getTileData().setActiveRecipe(null);
            infuser.getTileData().markForUpdate();
            if (!present.isEmpty()) {
                ItemStack recovered = inventory.extractItem(0, present.getCount(), false);
                if (!ItemStack.matches(input, recovered)) throw new IllegalStateException("Astral infusion recovery extraction changed");
                operation.returned(AEItemKey.of(recovered), recovered.getCount());
            }
        }
        for (ItemEntity entity : entities) {
            ItemStack stack = entity.getItem().copy();
            operation.returned(AEItemKey.of(stack), stack.getCount());
            entity.discard();
        }
        var journal = PackagedRecoveryJournal.get(level);
        var evidence = journal.read(operation.id());
        evidence.putBoolean("observed", true);
        journal.write(operation.id(), evidence);
        return true;
    }

    static @Nullable RecipeHolder<InfusionRecipe> recipe(ServerLevel level, ResourceLocation id) {
        var holder = level.getRecipeManager().byKey(id);
        return holder.isPresent() && holder.get().value() instanceof InfusionRecipe infusion &&
                infusion.getType() == RecipeTypesAS.INFUSION_TYPE.get() ? new RecipeHolder<>(id, infusion) : null;
    }

    static boolean fluidLayoutMatches(ServerLevel level, BlockPos position, InfusionRecipe recipe) {
        for (BlockPos offset : TileInfuser.getLiquidOffsets()) {
            BlockPos liquidPosition = position.offset(offset);
            if (!level.isLoaded(liquidPosition)) return false;
            var state = level.getFluidState(liquidPosition);
            if (state.isEmpty() || !state.is(recipe.getFluidInput())) return false;
        }
        return true;
    }

    private static boolean loadedRadius(ServerLevel level, BlockPos position, int radius) {
        for (int x = (position.getX() - radius) >> 4; x <= (position.getX() + radius) >> 4; x++) {
            for (int z = (position.getZ() - radius) >> 4; z <= (position.getZ() + radius) >> 4; z++) {
                if (!level.hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    private static boolean start(PackagedMachineOperation operation, TileInfuser infuser) {
        var holder = recipe(operation.level(), operation.recipeId());
        if (holder == null || !infuser.hasStructure() || !holder.value().matches(infuser.createInput(operation.level()), operation.level())) return false;
        PackagedEntityCapture.run(operation.level(), operation.id(), () -> infuser.startCrafting(operation.level(), holder));
        return infuser.getTileData().getActiveRecipe().map(active -> operation.recipeId().equals(active.getRecipeId())).orElse(false);
    }

    private static boolean collectOutput(PackagedMachineOperation operation) {
        ObjectList<ItemEntity> entities = ownedEntities(operation);
        if (entities.isEmpty()) return false;
        ObjectList<ItemStack> expected = outputs(operation);
        var actual = new ObjectArrayList<ItemStack>();
        for (ItemEntity entity : entities) actual.add(entity.getItem().copy());
        if (!PackagedOutputMatching.acceptsPartial(operation, expected, actual)) {
            throw new IllegalStateException("Unexpected Astral infuser output");
        }
        if (!PackagedOutputMatching.matches(operation, expected, actual)) return false;
        for (ItemEntity entity : entities) {
            ItemStack stack = entity.getItem().copy();
            operation.returned(AEItemKey.of(stack), stack.getCount());
            entity.discard();
        }
        operation.complete();
        return true;
    }

    private static ObjectList<ItemEntity> ownedEntities(PackagedMachineOperation operation) {
        return new ObjectArrayList<>(operation.level().getEntitiesOfClass(ItemEntity.class,
                new AABB(operation.position()).inflate(8), entity -> PackagedEntityCapture.ownedBy(entity, operation.id())));
    }

    private static ItemStack input(PackagedMachineOperation operation) {
        return ItemStack.parse(operation.level().registryAccess(), operation.progress().getCompound("input"))
                .filter(stack -> stack.getCount() == 1)
                .orElseThrow(() -> new IllegalArgumentException("Invalid persisted Astral infusion input"));
    }

    private static ObjectList<ItemStack> outputs(PackagedMachineOperation operation) {
        CompoundTag progress = operation.progress();
        var result = new ObjectArrayList<ItemStack>();
        // The earlier infusion adapter saved this field as a single compound instead of a list.
        if (progress.contains("outputs", Tag.TAG_COMPOUND)) {
            result.add(ItemStack.parse(operation.level().registryAccess(), progress.getCompound("outputs"))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid persisted Astral infusion output")));
        } else {
            for (Tag tag : progress.getList("outputs", Tag.TAG_COMPOUND)) {
                result.add(ItemStack.parse(operation.level().registryAccess(), tag)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid persisted Astral infusion output")));
            }
        }
        if (result.size() != 1) throw new IllegalArgumentException("Invalid persisted Astral infusion output count");
        return result;
    }
}
