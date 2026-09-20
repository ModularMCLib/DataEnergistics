package com.fish_dan_.data_energistics.integration.crafting.packaged.actuallyadditions;

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
import net.minecraft.nbt.LongTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.AABB;

import de.ellpeck.actuallyadditions.api.lens.Lens;
import de.ellpeck.actuallyadditions.api.lens.LensConversion;
import de.ellpeck.actuallyadditions.mod.blocks.BlockLaserRelay;
import de.ellpeck.actuallyadditions.mod.crafting.ColorChangeRecipe;
import de.ellpeck.actuallyadditions.mod.crafting.LaserRecipe;
import de.ellpeck.actuallyadditions.mod.items.lens.LensColor;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityAtomicReconstructor;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Runs the real Atomic Reconstructor beam against item entities using its installed lens.
 * Only the machine is reserved: a shot and its output collection run synchronously, and air in the beam is not a
 * removable structure part. The live beam checks exclude foreign items before every shot.
 */
final class AtomicReconstructorAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation LASER = ResourceLocation.fromNamespaceAndPath("actuallyadditions", "laser");
    private static final ResourceLocation COLOR = ResourceLocation.fromNamespaceAndPath("actuallyadditions", "color_change");
    private static final String MODE = "mode";
    private static final String INPUT = "input";
    private static final String RESULT = "result";
    private static final int ENERGY_START = 1_000;
    private static final int ENERGY_COLOR = 200;

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("actually_additions_atomic_reconstructor");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(LASER, COLOR);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof TileEntityAtomicReconstructor;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!level.isLoaded(position) || !(level.getBlockEntity(position) instanceof TileEntityAtomicReconstructor machine)) return null;
        Lens lens = machine.getLens();
        InputTotals totals = totals(inputs);
        if (totals == null || totals.total() <= 0) return null;
        BlockPos target = position.relative(machine.getOrientation());
        if (!safeBeam(level, position, machine.getOrientation(), lens)) return null;

        if (lens instanceof LensColor && totals.unit() != null) {
            var holder = recipe(level, recipeId, ColorChangeRecipe.class);
            if (holder == null || !holder.value().matches(totals.unit()) ||
                    ColorChangeRecipe.getRecipeForStack(totals.unit()).filter(selected -> selected.id().equals(recipeId)).isEmpty())
                return null;
            ItemStack result = holder.value().getResultItem(level.registryAccess()).copyWithCount(1);
            if (result.isEmpty() || !outputsMatch(pattern, result, totals.total())) return null;
            return singleProgress(machine, target, "color", totals.unit(), result, totals.total(), ENERGY_START + ENERGY_COLOR);
        }
        if (lens instanceof LensConversion && totals.unit() != null) {
            var holder = recipe(level, recipeId, LaserRecipe.class);
            if (holder == null || !holder.value().matches(totals.unit()) ||
                    LaserRecipe.getRecipeForStack(totals.unit()).filter(selected -> selected.id().equals(recipeId)).isEmpty())
                return null;
            ItemStack result = holder.value().getResultItem(level.registryAccess()).copyWithCount(1);
            if (result.isEmpty() || !outputsMatch(pattern, result, totals.total())) return null;
            if (holder.value().getEnergy() <= 0) return null;
            long energy = ENERGY_START + (long) holder.value().getEnergy();
            if (energy > Integer.MAX_VALUE) return null;
            return singleProgress(machine, target, "laser", totals.unit(), result, totals.total(), (int) energy);
        }
        return null;
    }

    @Override
    public long batchCapacity(ServerLevel level, BlockPos position, Direction face, ResourceLocation recipeId,
                              IPatternDetails pattern, KeyCounter[] prototype, long requestedCount) {
        var progress = prepare(level, position, face, recipeId, pattern, prototype);
        if (progress == null) return 0;
        var machine = (TileEntityAtomicReconstructor) level.getBlockEntity(position);
        int energy = progress.getInt("energy");
        if (machine.getEnergy() < energy) return 0;
        var input = ItemStack.parse(level.registryAccess(), progress.getCompound(INPUT)).orElseThrow();
        var output = ItemStack.parse(level.registryAccess(), progress.getCompound(RESULT)).orElseThrow();
        int capacity = Math.min(input.getMaxStackSize(), output.getMaxStackSize());
        if (progress.getString(MODE).equals("laser")) capacity = Math.min(capacity, (machine.getEnergy() - ENERGY_START) / (energy - ENERGY_START));
        return Math.min(requestedCount, capacity / progress.getLong("cycles"));
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!(operation.level().getBlockEntity(operation.position()) instanceof TileEntityAtomicReconstructor machine)) {
            return false;
        }
        CompoundTag progress = operation.progress();
        long cycles = progress.getLong("cycles");
        if (cycles <= 0) throw new IllegalArgumentException("Invalid Atomic Reconstructor cycle count");
        BlockPos target = BlockPos.of(progress.getLong("target"));
        if (!target.equals(operation.position().relative(machine.getOrientation())) ||
                !safeBeam(operation.level(), operation.position(), machine.getOrientation(), machine.getLens())) {
            return false;
        }
        String mode = progress.getString(MODE);
        ItemStack input = read(operation, INPUT);
        ItemStack expected = read(operation, RESULT);
        int energy = progress.getInt("energy");
        if (input.getCount() != 1 || expected.isEmpty() || energy <= 0 || mode.isEmpty() ||
                mode.equals("laser") && energy <= ENERGY_START) {
            throw new IllegalArgumentException("Invalid Atomic Reconstructor progress");
        }
        if (!lensMatches(machine.getLens(), mode) || machine.getEnergy() < energy || !machine.getLens().canInvoke(machine, machine.getOrientation(), ENERGY_START)) {
            return false;
        }
        // The native static recipe cache can change on a data-pack reload between admission and this shot.
        if (mode.equals("color")) {
            var selected = ColorChangeRecipe.getRecipeForStack(input);
            if (selected.isEmpty() || !selected.get().id().equals(operation.recipeId()) ||
                    !PackagedOutputMatching.matches(operation, expected, selected.get().value().getResultItem(operation.level().registryAccess()).copyWithCount(1)))
                throw new IllegalStateException("Atomic color recipe changed after admission");
        } else {
            var selected = LaserRecipe.getRecipeForStack(input);
            if (selected.isEmpty() || !selected.get().id().equals(operation.recipeId()) ||
                    selected.get().value().getEnergy() != energy - ENERGY_START ||
                    !PackagedOutputMatching.matches(operation, expected, selected.get().value().getResultItem(operation.level().registryAccess()).copyWithCount(1)))
                throw new IllegalStateException("Atomic conversion recipe changed after admission");
        }
        int capacity = Math.min(input.getMaxStackSize(), expected.getMaxStackSize());
        if (mode.equals("laser")) capacity = Math.min(capacity, (machine.getEnergy() - ENERGY_START) / (energy - ENERGY_START));
        int batch = (int) Math.min(cycles, capacity);
        if (batch <= 0) return false;
        if (operation.available(AEItemKey.of(input)).compareTo(BigInteger.valueOf(batch)) < 0) {
            throw new IllegalStateException("Atomic Reconstructor dispatch exceeds owned materials");
        }
        var existing = owned(operation.level(), operation.id(), target);
        if (!existing.isEmpty()) throw new IllegalStateException("Atomic Reconstructor target still has prior operation entities");

        int beforeEnergy = machine.getEnergy();
        var spawned = new ObjectArrayList<ItemEntity>();
        PackagedEntityCapture.run(operation.level(), operation.id(), () -> {
            spawned.add(spawn(operation.level(), target, input.copyWithCount(batch)));
            operation.delivered(AEItemKey.of(input), batch);
            machine.activateOnPulse();
        });
        var drops = owned(operation.level(), operation.id(), target);
        for (ItemEntity entity : spawned) {
            if (!entity.isRemoved() && drops.contains(entity)) {
                throw new IllegalStateException("Atomic Reconstructor did not consume its physical input entity");
            }
        }
        if (drops.isEmpty()) throw new IllegalStateException("Atomic Reconstructor lens did not produce a captured item result");
        if (!PackagedOutputMatching.matches(operation, ObjectList.of(expected.copyWithCount(batch)),
                drops.stream().map(ItemEntity::getItem).toList()))
            throw new IllegalStateException("Atomic Reconstructor produced an unexpected item result");
        if (machine.getEnergy() >= beforeEnergy) throw new IllegalStateException("Atomic Reconstructor did not consume its energy");
        for (ItemEntity drop : drops) {
            ItemStack actual = drop.getItem().copy();
            drop.discard();
            operation.returned(AEItemKey.of(actual), actual.getCount());
        }
        progress.putLong("cycles", cycles - batch);
        operation.changed();
        if (cycles == batch) operation.complete();
        return true;
    }

    private static CompoundTag singleProgress(TileEntityAtomicReconstructor machine, BlockPos target, String mode,
                                              ItemStack input, ItemStack result, long cycles, int energy) {
        var progress = new CompoundTag();
        progress.putString(MODE, mode);
        progress.put("target", LongTag.valueOf(target.asLong()));
        progress.put(INPUT, input.copyWithCount(1).save(machine.getLevel().registryAccess()));
        progress.put(RESULT, result.copyWithCount(result.getCount()).save(machine.getLevel().registryAccess()));
        progress.putLong("cycles", cycles);
        progress.putInt("energy", energy);
        return progress;
    }

    private static boolean lensMatches(Lens lens, String mode) {
        return mode.equals("color") && lens instanceof LensColor || mode.equals("laser") && lens instanceof LensConversion;
    }

    private static boolean safeBeam(ServerLevel level, BlockPos origin, Direction direction, Lens lens) {
        BlockPos inputPosition = origin.relative(direction);
        if (!level.isLoaded(inputPosition) || !level.getBlockState(inputPosition).isAir()) return false;
        for (var target : affectedBlocks(level, origin, direction, lens)) {
            if (!level.isLoaded(target)) return false;
            var state = level.getBlockState(target);
            if (state.isAir()) continue;
            // Native conversion ignores ordinary terrain. Reject only blocks the installed lens could transform.
            var blockItem = new ItemStack(state.getBlock());
            if (lens instanceof LensConversion && !(state.getBlock() instanceof BlockLaserRelay) &&
                    LaserRecipe.getRecipeForStack(blockItem).isPresent())
                return false;
            if (lens instanceof LensColor && ColorChangeRecipe.getRecipeForStack(blockItem).isPresent()) return false;
        }
        var end = origin.relative(direction, lens.getDistance());
        var area = new AABB(origin.getX(), origin.getY(), origin.getZ(), end.getX() + 1, end.getY() + 1, end.getZ() + 1)
                .inflate(0.02).expandTowards(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        return level.getEntitiesOfClass(ItemEntity.class, area).isEmpty();
    }

    private static ObjectList<BlockPos> affectedBlocks(ServerLevel level, BlockPos origin, Direction direction, Lens lens) {
        var positions = new ObjectArrayList<BlockPos>();
        int radius = lens instanceof LensConversion ? 1 : 0;
        int x = direction.getAxis() == Direction.Axis.X ? 0 : radius;
        int y = direction.getAxis() == Direction.Axis.Y ? 0 : radius;
        int z = direction.getAxis() == Direction.Axis.Z ? 0 : radius;
        for (int distance = 1; distance <= lens.getDistance(); distance++) {
            var center = origin.relative(direction, distance);
            for (var target : BlockPos.betweenClosed(center.offset(-x, -y, -z), center.offset(x, y, z))) positions.add(target.immutable());
            if (lens instanceof LensConversion && level.isLoaded(center) && !level.getBlockState(center).isAir()) break;
        }
        return positions;
    }

    private static ItemEntity spawn(ServerLevel level, BlockPos target, ItemStack stack) {
        var entity = new ItemEntity(level, target.getX() + 0.5, target.getY() + 0.25, target.getZ() + 0.5, stack.copy());
        entity.setNoPickUpDelay();
        if (!level.addFreshEntity(entity)) throw new IllegalStateException("Atomic Reconstructor input entity was rejected");
        return entity;
    }

    private static ObjectList<ItemEntity> owned(ServerLevel level, UUID operation, BlockPos target) {
        return new ObjectArrayList<>(level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(1), entity -> PackagedEntityCapture.ownedBy(entity, operation)));
    }

    private static boolean outputsMatch(IPatternDetails pattern, ItemStack output, long count) {
        return PackagedOutputMatching.matches(pattern, output, count);
    }

    private static @Nullable InputTotals totals(KeyCounter[] inputs) {
        var counts = new Object2LongLinkedOpenHashMap<AEItemKey>();
        long total = 0;
        for (KeyCounter counter : inputs) for (var entry : counter) {
            if (!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() <= 0 ||
                    entry.getLongValue() > Long.MAX_VALUE - total)
                return null;
            counts.addTo(key, entry.getLongValue());
            total += entry.getLongValue();
        }
        if (counts.isEmpty()) return null;
        AEItemKey unit = counts.size() == 1 ? counts.firstKey() : null;
        return unit == null ? new InputTotals(counts, total, null) : new InputTotals(counts, total, unit.toStack().copyWithCount(1));
    }

    /** The recipe manager erases the holder's recipe type; the preceding type check makes this cast safe. */
    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> @Nullable RecipeHolder<T> recipe(ServerLevel level, ResourceLocation id, Class<T> type) {
        var holder = level.getRecipeManager().byKey(id);
        return holder.isPresent() && type.isInstance(holder.get().value()) ? (RecipeHolder<T>) holder.get() : null;
    }

    private static ItemStack read(PackagedMachineOperation operation, String key) {
        return ItemStack.parse(operation.level().registryAccess(), operation.progress().getCompound(key))
                .orElseThrow(() -> new IllegalArgumentException("Invalid Atomic Reconstructor " + key));
    }

    private record InputTotals(Object2LongLinkedOpenHashMap<AEItemKey> counts, long total, @Nullable ItemStack unit) {

    }
}
