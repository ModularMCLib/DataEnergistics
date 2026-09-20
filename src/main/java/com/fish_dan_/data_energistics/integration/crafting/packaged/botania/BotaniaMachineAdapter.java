package com.fish_dan_.data_energistics.integration.crafting.packaged.botania;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;
import com.fish_dan_.data_energistics.mixin.botania.AlfheimPortalAccessor;
import com.fish_dan_.data_energistics.mixin.botania.RunicAltarAccessor;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.EncodedProcessingPattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;
import vazkii.botania.api.block.PetalApothecary;
import vazkii.botania.api.recipe.ManaInfusionRecipe;
import vazkii.botania.api.recipe.PetalApothecaryRecipe;
import vazkii.botania.api.recipe.RunicAltarRecipe;
import vazkii.botania.api.state.BotaniaStateProperties;
import vazkii.botania.api.state.enums.AlfheimPortalState;
import vazkii.botania.api.state.enums.TerraPlateState;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.block_entity.AlfheimPortalBlockEntity;
import vazkii.botania.common.block.block_entity.PetalApothecaryBlockEntity;
import vazkii.botania.common.block.block_entity.RunicAltarBlockEntity;
import vazkii.botania.common.block.block_entity.TerrestrialAgglomerationPlateBlockEntity;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Predicate;

/** Five native Botania machines share persisted material accounting while retaining their own physical processing. */
final class BotaniaMachineAdapter implements PackagedMachineAdapter {

    private final BotaniaMachineKind kind;

    BotaniaMachineAdapter(BotaniaMachineKind kind) {
        this.kind = kind;
    }

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("botania_" + this.kind.category.getPath());
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        if (this.kind == BotaniaMachineKind.TERRA) return ObjectSet.of(this.kind.category,
                ResourceLocation.fromNamespaceAndPath("botania", "terra_plate"),
                ResourceLocation.fromNamespaceAndPath("botania", "terrestrial_agglomeration"));
        return this.kind == BotaniaMachineKind.RUNE ? ObjectSet.of(this.kind.category, RunicAltarRecipe.HEAD_TYPE_ID) : ObjectSet.of(this.kind.category);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && this.kind.accepts(level.getBlockEntity(position));
    }

    @Override
    public @Nullable ItemStack completeEncoding(ServerLevel level, ResourceLocation recipeId, ItemStack encodedPattern) {
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !this.kind.accepts(holder.get().value())) return null;
        if (this.kind != BotaniaMachineKind.PETAL && this.kind != BotaniaMachineKind.RUNE) return encodedPattern;
        var processing = encodedPattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing == null) return null;
        var selected = new ObjectArrayList<ItemStack>();
        for (var input : processing.sparseInputs()) {
            if (input == null) continue;
            if (!(input.what() instanceof AEItemKey item) || input.amount() <= 0 || input.amount() > 64) return null;
            selected.add(item.toStack((int) input.amount()));
        }
        // Both fresh viewer inputs and already-augmented patterns are accepted without duplicating consumables.
        var augmented = BotaniaPatternEncoding.augment(level, holder.get(), selected);
        if (augmented == null) {
            var reagent = holder.get().value() instanceof PetalApothecaryRecipe petal ? petal.getReagent() :
                    ((RunicAltarRecipe) holder.get().value()).getReagent();
            for (int mask = 1; mask <= 3 && augmented == null; mask++) {
                var stripped = new ObjectArrayList<>(selected.stream().map(ItemStack::copy).toList());
                ItemStack selectedReagent = (mask & 1) != 0 ? removeOne(stripped, reagent::test) : null;
                if ((mask & 1) != 0 && selectedReagent.isEmpty()) continue;
                if ((mask & 2) != 0 && (this.kind != BotaniaMachineKind.PETAL || removeOne(stripped, stack -> stack.is(Items.WATER_BUCKET)).isEmpty())) continue;
                augmented = BotaniaPatternEncoding.augment(level, holder.get(), stripped, selectedReagent);
            }
            if (augmented == null) return null;
        }
        var completedInputs = BotaniaPatternEncoding.appendMissing(processing.sparseInputs(), augmented.inputs(), 81);
        var completedOutputs = BotaniaPatternEncoding.appendReturned(processing.sparseOutputs(), augmented.outputs(), 27);
        if (completedInputs == null || completedOutputs == null) return null;
        ItemStack copy = encodedPattern.copy();
        copy.set(AEComponents.ENCODED_PROCESSING_PATTERN, new EncodedProcessingPattern(
                completedInputs, completedOutputs));
        return copy;
    }

    private static ItemStack removeOne(List<ItemStack> stacks, Predicate<ItemStack> predicate) {
        for (int index = 0; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            if (!predicate.test(stack)) continue;
            ItemStack removed = stack.copyWithCount(1);
            stack.shrink(1);
            if (stack.isEmpty()) stacks.remove(index);
            return removed;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        BlockEntity machine = machine(level, position);
        if (machine == null || !ready(machine)) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !this.kind.accepts(holder.get().value())) return null;
        BotaniaRecipePlan plan = BotaniaRecipePlan.prepare(level, machine, holder.get(), pattern, inputs, this.kind == BotaniaMachineKind.PETAL);
        if (plan != null && plan.waterBucket() && ((PetalApothecaryBlockEntity) machine).getFluid() != PetalApothecary.State.EMPTY) return null;
        if (plan == null && this.kind == BotaniaMachineKind.PETAL &&
                ((PetalApothecaryBlockEntity) machine).getFluid() == PetalApothecary.State.WATER) {
            plan = BotaniaRecipePlan.prepare(level, machine, holder.get(), pattern, inputs, false);
        }
        if (plan == null || !physicalCapacity(machine, holder.get(), plan.ingredientCount())) return null;
        var progress = new CompoundTag();
        var encodedInputs = new ListTag();
        var encodedOutputs = new ListTag();
        plan.inputs().forEach(stack -> encodedInputs.add(stack.save(level.registryAccess())));
        plan.outputs().forEach(stack -> encodedOutputs.add(stack.save(level.registryAccess())));
        progress.put("inputs", encodedInputs);
        progress.put("outputs", encodedOutputs);
        progress.putInt("ingredients", plan.ingredientCount());
        progress.putBoolean("water", plan.waterBucket());
        progress.putLong("cycles", plan.cycles());
        return progress;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        BlockEntity machine = machine(operation.level(), operation.position());
        if (machine == null) return false;
        var progress = operation.progress();
        if (progress.getLong("cycles") <= 0) throw new IllegalArgumentException("Invalid Botania cycle count");
        if (progress.getBoolean("delivered")) {
            if (machine instanceof RunicAltarBlockEntity altar && !progress.getBoolean("activated")) {
                if (altar.getTargetMana() <= 0 || altar.getCurrentMana() < altar.getTargetMana()) return false;
                var inputs = BotaniaOperationItems.read(operation, "inputs");
                ItemStack reagent = inputs.get(progress.getInt("ingredients"));
                BotaniaOperationItems.spawn(operation, reagent, operation.position().getCenter());
                PackagedEntityCapture.run(operation.level(), operation.id(), () -> altar.onUsedByWand(null, ItemStack.EMPTY, Direction.UP));
                progress.putBoolean("activated", true);
                operation.changed();
            }
            if (!BotaniaOperationItems.collect(operation)) return false;
            long cycles = progress.getLong("cycles") - 1;
            progress.putLong("cycles", cycles);
            progress.putBoolean("delivered", false);
            progress.putBoolean("activated", false);
            progress.remove("recovered");
            progress.remove("entities");
            operation.changed();
            if (cycles == 0) operation.complete();
            return true;
        }
        if (!ready(machine)) return false;
        var inputs = BotaniaOperationItems.read(operation, "inputs");
        int count = progress.getInt("ingredients");
        if (count <= 0 || count > inputs.size() || inputs.size() > 64) throw new IllegalArgumentException("Invalid Botania ingredient count");
        var required = new KeyCounter();
        for (ItemStack stack : inputs) required.add(AEItemKey.of(stack), stack.getCount());
        for (var entry : required) {
            if (operation.available(entry.getKey()).compareTo(BigInteger.valueOf(entry.getLongValue())) < 0) throw new IllegalStateException("Missing owned Botania materials");
        }
        var holder = operation.level().getRecipeManager().byKey(operation.recipeId());
        if (holder.isEmpty() || !this.kind.accepts(holder.get().value())) throw new IllegalStateException("Botania recipe disappeared");
        var outputs = BotaniaRecipePlan.nativeOutputs(operation.level(), machine, holder.get(), inputs.subList(0, count));
        if (outputs == null) return false;
        if (progress.getBoolean("water")) outputs.add(new ItemStack(Items.BUCKET));
        var storedOutputs = BotaniaOperationItems.read(operation, "outputs");
        if (outputs.size() != storedOutputs.size()) throw new IllegalStateException("Botania output shape changed");
        if (!PackagedOutputMatching.matches(operation, storedOutputs, outputs)) throw new IllegalStateException("Botania output changed after preparation");
        if (!physicalCapacity(machine, holder.get(), count)) return false;
        if (machine instanceof PetalApothecaryBlockEntity apothecary) {
            if (progress.getBoolean("water")) {
                if (apothecary.getFluid() != PetalApothecary.State.EMPTY) return false;
                var bucket = BotaniaOperationItems.spawn(operation, inputs.getLast(), operation.position().getCenter());
                PackagedEntityCapture.run(operation.level(), operation.id(), () -> apothecary.collideEntityItem(bucket));
                if (apothecary.getFluid() != PetalApothecary.State.WATER) throw new IllegalStateException("Apothecary refused water bucket");
                BotaniaOperationItems.recoveredInputContainer(operation, bucket, new ItemStack(Items.BUCKET));
            } else if (apothecary.getFluid() != PetalApothecary.State.WATER) return false;
            for (int index = 0; index <= count; index++) {
                var entity = BotaniaOperationItems.spawn(operation, inputs.get(index), operation.position().getCenter());
                PackagedEntityCapture.run(operation.level(), operation.id(), () -> apothecary.collideEntityItem(entity));
                if (entity.isAlive() && !entity.getItem().isEmpty()) throw new IllegalStateException("Apothecary refused ingredient or closing reagent");
            }
        } else if (machine instanceof RunicAltarBlockEntity altar) {
            for (int index = 0; index < count; index++) {
                ItemStack stack = inputs.get(index).copy();
                altar.addItem(null, stack, null);
                int delivered = inputs.get(index).getCount() - stack.getCount();
                if (delivered > 0) operation.delivered(AEItemKey.of(inputs.get(index)), delivered);
                if (!stack.isEmpty()) throw new IllegalStateException("Runic altar refused a planned ingredient");
            }
        } else if (machine instanceof ManaPoolBlockEntity pool) {
            var item = BotaniaOperationItems.spawn(operation, inputs.getFirst(), operation.position().getCenter());
            PackagedEntityCapture.run(operation.level(), operation.id(), () -> pool.collideEntityItem(item));
            if (item.isAlive() && !item.getItem().isEmpty()) throw new IllegalStateException("Mana pool refused a planned infusion");
        } else {
            var target = machine instanceof AlfheimPortalBlockEntity ? operation.position().getCenter().add(0, 1, 0) :
                    operation.position().getCenter().add(0, -0.3, 0);
            for (int index = 0; index < count; index++) BotaniaOperationItems.spawn(operation, inputs.get(index), target);
            if (machine instanceof TerrestrialAgglomerationPlateBlockEntity plate) plate.tryStartProcessing();
        }
        progress.putBoolean("delivered", true);
        operation.changed();
        return true;
    }

    private @Nullable BlockEntity machine(ServerLevel level, BlockPos position) {
        if (!recognizes(level, position)) return null;
        int radius = this.kind == BotaniaMachineKind.PORTAL ? 5 : 1;
        for (int x = (position.getX() - radius) >> 4; x <= (position.getX() + radius) >> 4; x++) {
            for (int z = (position.getZ() - radius) >> 4; z <= (position.getZ() + radius) >> 4; z++) if (!level.hasChunk(x, z)) return null;
        }
        return level.getBlockEntity(position);
    }

    private static boolean ready(BlockEntity machine) {
        if (machine instanceof PetalApothecaryBlockEntity petal) return petal.isEmpty() && petal.getFluid() != PetalApothecary.State.LAVA;
        if (machine instanceof RunicAltarBlockEntity altar) return altar.isEmpty() && altar.getTargetMana() == 0 &&
                ((RunicAltarAccessor) altar).dataEnergistics$craftingCooldown() == 0;
        if (machine instanceof TerrestrialAgglomerationPlateBlockEntity plate) return plate.getBlockState().getValue(BotaniaStateProperties.TERRA_PLATE_STATE) == TerraPlateState.IDLE &&
                TerrestrialAgglomerationPlateBlockEntity.MULTIBLOCK.get().validate(machine.getLevel(), machine.getBlockPos().below()) != null &&
                machine.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(machine.getBlockPos())).isEmpty();
        if (machine instanceof AlfheimPortalBlockEntity portal) return portal.getBlockState().getValue(BotaniaStateProperties.ALFPORTAL_STATE) != AlfheimPortalState.OFF &&
                portal.ticksOpen > AlfheimPortalBlockEntity.TICKS_UNTIL_FULLY_OPENED &&
                ((AlfheimPortalAccessor) portal).dataEnergistics$pendingTradeItems().isEmpty() &&
                AlfheimPortalBlockEntity.MULTIBLOCK.get().validate(machine.getLevel(), machine.getBlockPos()) != null;
        return machine instanceof ManaPoolBlockEntity;
    }

    private static boolean physicalCapacity(BlockEntity machine, RecipeHolder<?> holder, int ingredientCount) {
        if (machine instanceof PetalApothecaryBlockEntity petal) return ingredientCount <= petal.inventorySize();
        if (machine instanceof RunicAltarBlockEntity altar) return ingredientCount <= altar.inventorySize();
        if (machine instanceof ManaPoolBlockEntity pool && holder.value() instanceof ManaInfusionRecipe recipe) return pool.getCurrentMana() >= recipe.getManaToConsume();
        if (machine instanceof AlfheimPortalBlockEntity portal) {
            var pools = new ObjectArrayList<ManaPoolBlockEntity>();
            for (BlockPos pos : BlockPos.betweenClosed(portal.getBlockPos().offset(-5, -5, -5), portal.getBlockPos().offset(5, 5, 5))) {
                if (portal.getLevel().getBlockState(pos).is(BotaniaBlocks.NATURA_PYLON) &&
                        portal.getLevel().getBlockEntity(pos.below()) instanceof ManaPoolBlockEntity pool)
                    pools.add(pool);
            }
            if (pools.size() < AlfheimPortalBlockEntity.MIN_REQUIRED_PYLONS) return false;
            int cost = Math.max(1, AlfheimPortalBlockEntity.MANA_COST / pools.size());
            return pools.stream().allMatch(pool -> pool.getCurrentMana() >= cost);
        }
        return true;
    }
}
