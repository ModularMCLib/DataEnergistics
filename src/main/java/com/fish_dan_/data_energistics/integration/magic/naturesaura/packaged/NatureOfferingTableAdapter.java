package com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;
import com.fish_dan_.data_energistics.mixin.magic.naturesaura.OfferingTableQueueAccessor;
import com.fish_dan_.data_energistics.world.packaged.PackagedRecoveryJournal;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.EncodedProcessingPattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import de.ellpeck.naturesaura.Helper;
import de.ellpeck.naturesaura.blocks.ModBlocks;
import de.ellpeck.naturesaura.blocks.multi.Multiblocks;
import de.ellpeck.naturesaura.blocks.tiles.BlockEntityOfferingTable;
import de.ellpeck.naturesaura.recipes.ModRecipes;
import de.ellpeck.naturesaura.recipes.OfferingRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/** One native offering of up to sixteen materials, triggered by exactly one physical spirit entity. */
public final class NatureOfferingTableAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("naturesaura", "offering");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("naturesaura_offering_table");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE);
    }

    @Override
    public @Nullable ItemStack completeEncoding(ServerLevel level, ResourceLocation recipeId, ItemStack encodedPattern) {
        var holder = level.getRecipeManager().byKey(recipeId);
        var processing = encodedPattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing == null || holder.isEmpty() || !(holder.get().value() instanceof OfferingRecipe recipe)) return null;
        var supplied = new KeyCounter();
        for (var input : processing.sparseInputs()) if (input != null) supplied.add(input.what(), input.amount());
        if (assign(recipe, supplied) != null) return encodedPattern;
        if (supplied.size() != 1) return null;
        var material = supplied.iterator().next();
        if (!(material.getKey() instanceof AEItemKey key) || !recipe.input.test(key.toStack()) || material.getLongValue() <= 0 || material.getLongValue() > 16) return null;
        var starts = recipe.startItem.getItems();
        if (starts.length == 0) return null;
        var inputs = new ObjectArrayList<@Nullable GenericStack>(processing.sparseInputs());
        inputs.add(new GenericStack(AEItemKey.of(starts[0]), 1));
        var copy = encodedPattern.copy();
        copy.set(AEComponents.ENCODED_PROCESSING_PATTERN, new EncodedProcessingPattern(inputs, processing.sparseOutputs()));
        return copy;
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockState(position).is(ModBlocks.OFFERING_TABLE) && Multiblocks.OFFERING_TABLE.forEach(position, '\0', (part, matcher) -> level.isLoaded(part)) && Multiblocks.OFFERING_TABLE.isComplete(level, position);
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, position) || !(level.getBlockEntity(position) instanceof BlockEntityOfferingTable table) || !table.items.getStackInSlot(0).isEmpty() || !queueEmpty(level, table)) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof OfferingRecipe recipe) || recipe.getType() != ModRecipes.OFFERING_TYPE) return null;
        var supplied = new KeyCounter();
        for (var counter : inputs) for (var entry : counter) {
            if (!(entry.getKey() instanceof AEItemKey) || entry.getLongValue() <= 0 || entry.getLongValue() > 17) return null;
            supplied.add(entry.getKey(), entry.getLongValue());
        }
        var assigned = assign(recipe, supplied);
        if (assigned == null) return null;
        var selected = level.getRecipeManager().getRecipesFor(ModRecipes.OFFERING_TYPE, null, level).stream()
                .filter(candidate -> candidate.value().input.test(assigned.material())).findFirst();
        if (selected.isEmpty() || !selected.get().id().equals(recipeId)) return null;
        int unit = Helper.getIngredientAmount(recipe.input);
        if (unit <= 0 || assigned.material().getCount() % unit != 0) return null;
        int expected = Math.multiplyExact(recipe.output.getCount(), assigned.material().getCount() / unit);
        if (pattern.getOutputs().size() != 1 || !PackagedOutputMatching.matches(pattern, recipe.output, expected)) return null;
        if (!level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(1), entity -> recipe.startItem.test(entity.getItem())).isEmpty()) return null;
        var progress = new CompoundTag();
        progress.put("input", assigned.material().save(level.registryAccess()));
        progress.put("start", assigned.spirit().save(level.registryAccess()));
        progress.put("output", recipe.output.save(level.registryAccess()));
        progress.putInt("expected", expected);
        progress.putString("phase", "material");
        progress.putInt("capture_radius", 16);
        return progress;
    }

    private static @Nullable OfferingInput assign(OfferingRecipe recipe, KeyCounter supplied) {
        for (var candidate : supplied) {
            if (!(candidate.getKey() instanceof AEItemKey spirit) || candidate.getLongValue() < 1 || !recipe.startItem.test(spirit.toStack())) continue;
            AEItemKey material = null;
            long count = 0;
            boolean valid = true;
            for (var entry : supplied) {
                long amount = entry.getLongValue() - (entry.getKey().equals(spirit) ? 1 : 0);
                if (amount == 0) continue;
                if (amount < 0 || amount > 16 || material != null || !(entry.getKey() instanceof AEItemKey key) || !recipe.input.test(key.toStack())) {
                    valid = false;
                    break;
                }
                material = key;
                count = amount;
            }
            if (valid && material != null) return new OfferingInput(material.toStack(Math.toIntExact(count)), spirit.toStack());
        }
        return null;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var occupied = new ObjectArrayList<BlockPos>();
        Multiblocks.OFFERING_TABLE.forEach(position, '\0', (part, matcher) -> {
            occupied.add(part.immutable());
            return true;
        });
        return occupied;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!operation.level().isLoaded(operation.position()) || !(operation.level().getBlockEntity(operation.position()) instanceof BlockEntityOfferingTable table)) return false;
        var progress = operation.progress();
        if (progress.getString("phase").equals("waiting")) {
            boolean complete = collect(operation, false);
            if (!complete || !table.items.getStackInSlot(0).isEmpty() || !queueEmpty(operation.level(), table)) return false;
            operation.complete();
            return true;
        }
        if (!recognizes(operation.level(), operation.position())) return false;
        ItemStack input = read(operation, "input");
        ItemStack spirit = read(operation, "start");
        if (progress.getString("phase").equals("material")) {
            var required = new KeyCounter();
            required.add(AEItemKey.of(input), input.getCount());
            required.add(AEItemKey.of(spirit), 1);
            for (var entry : required) if (operation.available(entry.getKey()).compareTo(BigInteger.valueOf(entry.getLongValue())) < 0) return false;
            if (!table.items.getStackInSlot(0).isEmpty() || !table.items.insertItem(0, input.copy(), true).isEmpty()) return false;
            if (!table.items.insertItem(0, input.copy(), false).isEmpty()) throw new IllegalStateException("Offering Table refused prepared materials");
            operation.delivered(AEItemKey.of(input), input.getCount());
            progress.putString("phase", "spirit");
            operation.changed();
        }
        if (!ItemStack.matches(input, table.items.getStackInSlot(0)) || operation.available(AEItemKey.of(spirit)).compareTo(BigInteger.ONE) < 0) return false;
        BlockPos pos = operation.position();
        var holder = operation.level().getRecipeManager().byKey(operation.recipeId()).orElseThrow();
        var recipe = (OfferingRecipe) holder.value();
        if (!operation.level().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1), item -> recipe.startItem.test(item.getItem())).isEmpty()) return false;
        var entity = new ItemEntity(operation.level(), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, spirit.copy());
        entity.setNoPickUpDelay();
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        PackagedEntityCapture.claim(entity, operation.id());
        if (!operation.level().addFreshEntity(entity)) return false;
        operation.delivered(AEItemKey.of(spirit), 1);
        progress.putUUID("spirit_entity", entity.getUUID());
        progress.putString("phase", "waiting");
        operation.changed();
        return true;
    }

    private static boolean collect(PackagedMachineOperation operation, boolean recovering) {
        var progress = operation.progress();
        var pos = operation.position();
        int radius = progress.getInt("capture_radius");
        var area = new AABB(pos.getX() - radius, operation.level().getMinBuildHeight() - 1, pos.getZ() - radius,
                pos.getX() + radius + 1, operation.level().getMaxBuildHeight() + 16, pos.getZ() + radius + 1);
        int recovered = progress.getInt("recovered");
        ItemStack expected = read(operation, "output");
        for (var entity : operation.level().getEntitiesOfClass(ItemEntity.class, area, item -> PackagedEntityCapture.ownedBy(item, operation.id()))) {
            if (!recovering && progress.hasUUID("spirit_entity") && progress.getUUID("spirit_entity").equals(entity.getUUID())) continue;
            ItemStack actual = entity.getItem().copy();
            if (PackagedOutputMatching.sameKey(operation, expected, actual)) recovered = Math.addExact(recovered, actual.getCount());
            entity.discard();
            operation.returned(AEItemKey.of(actual), actual.getCount());
        }
        if (recovered != progress.getInt("recovered")) {
            progress.putInt("recovered", recovered);
            operation.changed();
        }
        if (!recovering && recovered > progress.getInt("expected")) throw new IllegalStateException("Excess Offering Table output");
        return recovered == progress.getInt("expected");
    }

    @Override
    public boolean recoverRemoved(PackagedMachineOperation operation) {
        if (!operation.level().isLoaded(operation.position())) return false;
        var table = operation.level().getBlockEntity(operation.position());
        var ledger = PackagedRecoveryJournal.get(operation.level());
        var queued = ledger.read(operation.id()).getList("offering_queue", Tag.TAG_COMPOUND);
        if (table instanceof BlockEntityOfferingTable offering) {
            var nativeQueue = ((OfferingTableQueueAccessor) offering).dataEnergistics$queuedOutputs();
            if (!nativeQueue.isEmpty()) {
                queued.clear();
                for (var stack : nativeQueue) queued.add(stack.save(operation.level().registryAccess()));
                nativeQueue.clear();
                offering.setChanged();
            }
            if (!operation.progress().getString("phase").equals("material")) {
                ItemStack remaining = offering.items.getStackInSlot(0).copy();
                if (!remaining.isEmpty()) {
                    if (!ItemStack.matches(remaining, read(operation, "input"))) return false;
                    offering.items.setStackInSlot(0, ItemStack.EMPTY);
                    operation.returned(AEItemKey.of(remaining), remaining.getCount());
                }
            }
        }
        for (var encoded : queued) {
            var stack = ItemStack.parse(operation.level().registryAccess(), (CompoundTag) encoded).orElseThrow();
            operation.returned(AEItemKey.of(stack), stack.getCount());
        }
        collect(operation, true);
        return true;
    }

    private static boolean queueEmpty(ServerLevel level, BlockEntityOfferingTable table) {
        return ((OfferingTableQueueAccessor) table).dataEnergistics$queuedOutputs().isEmpty();
    }

    private static ItemStack read(PackagedMachineOperation operation, String name) {
        return ItemStack.parse(operation.level().registryAccess(), operation.progress().getCompound(name)).orElseThrow();
    }

    private record OfferingInput(ItemStack material, ItemStack spirit) {}
}
