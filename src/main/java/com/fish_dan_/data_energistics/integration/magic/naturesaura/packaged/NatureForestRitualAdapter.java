package com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedBlockChanges;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;
import com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged.storage.NatureRitualLedger;
import com.fish_dan_.data_energistics.mixin.magic.naturesaura.WoodStandRitualAccessor;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.EncodedProcessingPattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.phys.AABB;

import de.ellpeck.naturesaura.blocks.ModBlocks;
import de.ellpeck.naturesaura.blocks.multi.Multiblocks;
import de.ellpeck.naturesaura.blocks.tiles.BlockEntityWoodStand;
import de.ellpeck.naturesaura.recipes.ModRecipes;
import de.ellpeck.naturesaura.recipes.TreeRitualRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Comparator;

/** Native tree growth and ritual execution, bound permanently to the soil below the sapling. */
public final class NatureForestRitualAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("naturesaura", "tree_ritual");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("naturesaura_forest_ritual");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos anchor) {
        if (!level.isLoaded(anchor) || level.getBlockState(anchor).isAir()) return false;
        for (BlockPos stand : positions(anchor, 'W')) {
            if (!level.isLoaded(stand) || !(level.getBlockEntity(stand) instanceof BlockEntityWoodStand)) return false;
        }
        for (BlockPos powder : positions(anchor, 'G')) {
            if (!level.isLoaded(powder) || !ModBlocks.GOLD_POWDER.defaultBlockState().canSurvive(level, powder)) return false;
        }
        return true;
    }

    @Override
    public @Nullable ItemStack completeEncoding(ServerLevel level, ResourceLocation recipeId, ItemStack encodedPattern) {
        var holder = level.getRecipeManager().byKey(recipeId);
        var processing = encodedPattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing == null || holder.isEmpty() || !(holder.get().value() instanceof TreeRitualRecipe recipe)) return null;
        var required = new ObjectArrayList<>(recipe.ingredients);
        var supplied = new KeyCounter();
        for (var input : processing.sparseInputs()) if (input != null) supplied.add(input.what(), input.amount());
        var augmented = new ObjectArrayList<@Nullable GenericStack>(processing.sparseInputs());
        // JEI versions differ in whether the sapling and powder are included already.
        if (PackagedIngredientAssignment.match(required, new KeyCounter[] { supplied }) != null) {
            ItemStack[] saplings = recipe.saplingType.getItems();
            if (saplings.length == 0) return null;
            var key = AEItemKey.of(saplings[0]);
            supplied.add(key, 1);
            augmented.add(new GenericStack(key, 1));
        }
        required.add(recipe.saplingType);
        if (PackagedIngredientAssignment.match(required, new KeyCounter[] { supplied }) != null) {
            int count = positions(BlockPos.ZERO, 'G').size();
            var key = AEItemKey.of(ModBlocks.GOLD_POWDER.asItem());
            supplied.add(key, count);
            augmented.add(new GenericStack(key, count));
        }
        addPowder(required);
        if (PackagedIngredientAssignment.match(required, new KeyCounter[] { supplied }) == null) return null;
        var copy = encodedPattern.copy();
        copy.set(AEComponents.ENCODED_PROCESSING_PATTERN, new EncodedProcessingPattern(augmented, processing.sparseOutputs()));
        return copy;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos anchor, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, anchor)) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof TreeRitualRecipe recipe) || recipe.getType() != ModRecipes.TREE_RITUAL_TYPE) return null;
        var stands = positions(anchor, 'W');
        var powders = positions(anchor, 'G');
        if (recipe.ingredients.isEmpty() || recipe.ingredients.size() > stands.size()) return null;
        var required = new ObjectArrayList<>(recipe.ingredients);
        required.add(recipe.saplingType);
        addPowder(required);
        var assigned = PackagedIngredientAssignment.match(required, inputs);
        if (assigned == null || pattern.getOutputs().size() != 1 || !PackagedOutputMatching.matches(pattern, recipe.output, recipe.output.getCount())) return null;
        ItemStack sapling = assigned.get(recipe.ingredients.size());
        if (!(sapling.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof SaplingBlock)) return null;
        BlockPos saplingPos = anchor.above();
        if (!level.getBlockState(saplingPos).isAir() || !blockItem.getBlock().defaultBlockState().canSurvive(level, saplingPos)) return null;
        for (BlockPos stand : stands) if (!((BlockEntityWoodStand) level.getBlockEntity(stand)).items.getStackInSlot(0).isEmpty()) return null;
        for (BlockPos powder : powders) if (!level.getBlockState(powder).isAir()) return null;
        if (!selectsRecipe(level, recipeId, sapling, assigned.subList(0, recipe.ingredients.size()), stands, anchor)) return null;
        var progress = new CompoundTag();
        progress.putLong("anchor", anchor.asLong());
        progress.putLong("sapling", saplingPos.asLong());
        progress.putString("recipe", recipeId.toString());
        progress.putLongArray("stands", stands.stream().mapToLong(BlockPos::asLong).toArray());
        progress.putLongArray("powders", powders.stream().mapToLong(BlockPos::asLong).toArray());
        var changing = new ObjectArrayList<>(powders);
        changing.add(saplingPos);
        progress.putLongArray("changing_positions", changing.stream().mapToLong(BlockPos::asLong).toArray());
        progress.putLong("capture_min", anchor.offset(-12, 0, -12).asLong());
        progress.putLong("capture_max", anchor.offset(13, 33, 13).asLong());
        progress.put("sapling_item", sapling.save(level.registryAccess()));
        var materials = new ListTag();
        for (int index = 0; index < recipe.ingredients.size(); index++) materials.add(assigned.get(index).save(level.registryAccess()));
        progress.put("materials", materials);
        progress.put("output", recipe.output.save(level.registryAccess()));
        progress.putString("phase", "materials");
        return progress;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos anchor, CompoundTag preparation) {
        var occupied = new ObjectArrayList<BlockPos>();
        occupied.add(anchor);
        occupied.add(anchor.above());
        for (long stand : preparation.getLongArray("stands")) occupied.add(BlockPos.of(stand));
        for (long powder : preparation.getLongArray("powders")) {
            occupied.add(BlockPos.of(powder));
            occupied.add(BlockPos.of(powder).below());
        }
        return occupied;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        var progress = operation.progress();
        BlockPos anchor = BlockPos.of(progress.getLong("anchor"));
        BlockPos saplingPos = anchor.above();
        if (!anchor.equals(operation.position()) || !saplingPos.equals(BlockPos.of(progress.getLong("sapling")))) throw new IllegalArgumentException("Invalid forest ritual anchor");
        // Native completion removes powder and tree. Harvest before checking the consumed structure.
        if (progress.getString("phase").equals("ritual")) {
            if (collect(operation)) {
                NatureRitualLedger.get(operation.level()).release(operation.id());
                operation.complete();
                return true;
            }
            return resumeRitual(operation, saplingPos);
        }
        for (var occupied : occupiedPositions(operation.level(), anchor, progress)) if (!operation.level().isLoaded(occupied)) return false;
        if (!recognizes(operation.level(), anchor)) {
            PackagedMachineClaims.get(operation.level()).blockReplaced(anchor);
            return true;
        }
        var materials = progress.getList("materials", Tag.TAG_COMPOUND);
        long[] stands = progress.getLongArray("stands");
        for (int index = 0; index < progress.getInt("material_cursor"); index++) {
            var stand = (BlockEntityWoodStand) operation.level().getBlockEntity(BlockPos.of(stands[index]));
            var expected = ItemStack.parseOptional(operation.level().registryAccess(), materials.getCompound(index));
            if (!ItemStack.matches(expected, stand.items.getStackInSlot(0))) {
                PackagedMachineClaims.get(operation.level()).blockReplaced(anchor);
                return true;
            }
        }
        long[] deliveredPowders = progress.getLongArray("powders");
        for (int index = 0; index < progress.getInt("powder_cursor"); index++) {
            if (!operation.level().getBlockState(BlockPos.of(deliveredPowders[index])).is(ModBlocks.GOLD_POWDER)) {
                PackagedMachineClaims.get(operation.level()).blockReplaced(anchor);
                return true;
            }
        }
        if (progress.getString("phase").equals("materials")) {
            int index = progress.getInt("material_cursor");
            if (index < materials.size()) {
                var stand = (BlockEntityWoodStand) operation.level().getBlockEntity(BlockPos.of(stands[index]));
                ItemStack stack = ItemStack.parseOptional(operation.level().registryAccess(), materials.getCompound(index));
                var key = AEItemKey.of(stack);
                if (operation.available(key).compareTo(BigInteger.ONE) < 0 || !stand.items.getStackInSlot(0).isEmpty()) return false;
                if (!stand.items.insertItem(0, stack.copy(), true).isEmpty()) return false;
                if (!stand.items.insertItem(0, stack.copy(), false).isEmpty()) throw new IllegalStateException("Wood stand refused material");
                operation.delivered(key, 1);
                progress.putInt("material_cursor", index + 1);
                operation.changed();
                return true;
            }
            progress.putString("phase", "powder");
        }
        if (progress.getString("phase").equals("powder")) {
            long[] powders = progress.getLongArray("powders");
            int index = progress.getInt("powder_cursor");
            if (index < powders.length) {
                BlockPos pos = BlockPos.of(powders[index]);
                var key = AEItemKey.of(ModBlocks.GOLD_POWDER.asItem());
                if (operation.available(key).compareTo(BigInteger.ONE) < 0 || !operation.level().getBlockState(pos).isAir()) return false;
                PackagedMachineClaims.get(operation.level()).markChanging(ObjectList.of(pos), operation.id());
                if (!operation.level().setBlockAndUpdate(pos, ModBlocks.GOLD_POWDER.defaultBlockState())) return false;
                operation.delivered(key, 1);
                progress.putInt("powder_cursor", index + 1);
                operation.changed();
                return true;
            }
            progress.putString("phase", "sapling");
        }
        ItemStack sapling = read(operation, "sapling_item");
        var saplingBlock = ((BlockItem) sapling.getItem()).getBlock();
        if (progress.getString("phase").equals("sapling")) {
            if (!operation.level().getBlockState(saplingPos).isAir() || !saplingBlock.defaultBlockState().canSurvive(operation.level(), saplingPos)) return false;
            if (operation.available(AEItemKey.of(sapling)).compareTo(BigInteger.ONE) < 0) return false;
            PackagedMachineClaims.get(operation.level()).markChanging(ObjectList.of(saplingPos), operation.id());
            if (!operation.level().setBlockAndUpdate(saplingPos, saplingBlock.defaultBlockState())) return false;
            operation.delivered(AEItemKey.of(sapling), 1);
            progress.putString("phase", "growing");
            progress.putBoolean("sapling_placed", true);
            operation.changed();
        }
        if (!Multiblocks.TREE_RITUAL.isComplete(operation.level(), saplingPos)) return false;
        var state = operation.level().getBlockState(saplingPos);
        if (state.is(saplingBlock)) {
            for (int x = (saplingPos.getX() - 8) >> 4; x <= (saplingPos.getX() + 8) >> 4; x++) {
                for (int z = (saplingPos.getZ() - 8) >> 4; z <= (saplingPos.getZ() + 8) >> 4; z++) {
                    if (!operation.level().hasChunk(x, z)) return false;
                }
            }
            PackagedMachineClaims.get(operation.level()).markChanging(ObjectList.of(saplingPos), operation.id());
            // Two native stages, no bone meal item or fake player. The grower emits BlockGrowFeatureEvent.
            var changes = PackagedBlockChanges.record(operation.level(), () -> PackagedEntityCapture.run(operation.level(), operation.id(), () -> PackagedMachineClaims.get(operation.level()).nativeChange(operation.id(), () -> {
                for (int attempt = 0; attempt < 2; attempt++) {
                    var current = operation.level().getBlockState(saplingPos);
                    if (!current.is(saplingBlock)) break;
                    ((SaplingBlock) saplingBlock).advanceTree(operation.level(), saplingPos, current, operation.level().random);
                }
            })));
            if (!operation.level().getBlockState(saplingPos).is(saplingBlock)) {
                var grown = new ListTag();
                changes.forEach(change -> {
                    var pos = change.position();
                    var previous = change.previous();
                    var current = change.current();
                    if (previous != current && (current.is(BlockTags.LOGS) || current.getBlock() instanceof LeavesBlock)) {
                        var entry = new CompoundTag();
                        entry.putLong("position", pos.asLong());
                        entry.put("previous", NbtUtils.writeBlockState(previous));
                        entry.put("grown", NbtUtils.writeBlockState(current));
                        grown.add(entry);
                    }
                });
                progress.put("grown_tree", grown);
                operation.changed();
            }
        }
        if (!operation.level().getBlockState(saplingPos).is(BlockTags.LOGS) || !operation.level().getBlockState(saplingPos.above()).is(BlockTags.LOGS)) return false;
        for (long standPos : stands) {
            var stand = (BlockEntityWoodStand) operation.level().getBlockEntity(BlockPos.of(standPos));
            var nativeState = (WoodStandRitualAccessor) stand;
            var selected = nativeState.dataEnergistics$recipe();
            if (selected == null || !saplingPos.equals(nativeState.dataEnergistics$ritualPosition())) continue;
            if (!selected.id().equals(operation.recipeId())) throw new IllegalStateException("Native forest ritual selected a different recipe");
            progress.putLong("controller", standPos);
            progress.putInt("native_timer", nativeState.dataEnergistics$timer());
            progress.putString("phase", "ritual");
            stand.setChanged();
            operation.changed();
            return true;
        }
        // Growth finished without the expected native controller: cancel and return only real assets.
        PackagedMachineClaims.get(operation.level()).blockReplaced(anchor);
        return true;
    }

    private static boolean resumeRitual(PackagedMachineOperation operation, BlockPos sapling) {
        var progress = operation.progress();
        BlockPos controller = BlockPos.of(progress.getLong("controller"));
        if (!operation.level().isLoaded(controller) || !(operation.level().getBlockEntity(controller) instanceof BlockEntityWoodStand stand)) return false;
        var state = (WoodStandRitualAccessor) stand;
        if (state.dataEnergistics$recipe() != null) {
            int timer = state.dataEnergistics$timer();
            if (timer == progress.getInt("native_timer")) return false;
            progress.putInt("native_timer", timer);
            operation.changed();
            return true;
        }
        if (!Multiblocks.TREE_RITUAL.forEach(sapling, '\0', (part, matcher) -> operation.level().isLoaded(part)) || !Multiblocks.TREE_RITUAL.isComplete(operation.level(), sapling) || !operation.level().getBlockState(sapling).is(BlockTags.LOGS) || !operation.level().getBlockState(sapling.above()).is(BlockTags.LOGS)) return false;
        var holder = operation.level().getRecipeManager().byKey(operation.recipeId());
        if (holder.isEmpty() || !(holder.get().value() instanceof TreeRitualRecipe recipe)) return false;
        int timer = progress.getInt("native_timer");
        var saved = stand.getPersistentData();
        if (saved.hasUUID("packaged_ritual_owner") && operation.id().equals(saved.getUUID("packaged_ritual_owner")) && operation.recipeId().toString().equals(saved.getString("packaged_ritual_recipe"))) timer = Math.max(timer, saved.getInt("packaged_ritual_timer"));
        if (timer < 0 || timer >= recipe.time) throw new IllegalStateException("Invalid native forest ritual timer");
        stand.setRitual(sapling, new RecipeHolder<>(operation.recipeId(), recipe));
        state.dataEnergistics$timer(timer);
        stand.setChanged();
        operation.changed();
        return true;
    }

    private static boolean selectsRecipe(ServerLevel level, ResourceLocation recipeId, ItemStack sapling,
                                         ObjectList<ItemStack> materials, ObjectList<BlockPos> stands, BlockPos anchor) {
        for (var holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.TREE_RITUAL_TYPE)) {
            if (!holder.value().saplingType.test(sapling)) continue;
            var required = new ObjectArrayList<>(holder.value().ingredients);
            boolean matches = Multiblocks.TREE_RITUAL.forEach(anchor.above(), 'W', (pos, matcher) -> {
                int slot = stands.indexOf(pos);
                if (slot >= materials.size()) return true;
                ItemStack material = materials.get(slot);
                for (int index = required.size() - 1; index >= 0; index--) {
                    if (required.get(index).test(material)) {
                        required.remove(index);
                        return true;
                    }
                }
                return false;
            });
            if (matches && required.isEmpty()) return holder.id().equals(recipeId);
        }
        return false;
    }

    private static boolean collect(PackagedMachineOperation operation) {
        var progress = operation.progress();
        var ledger = NatureRitualLedger.get(operation.level());
        var evidence = ledger.read(operation.id());
        BlockPos min = BlockPos.of(progress.getLong("capture_min"));
        BlockPos max = BlockPos.of(progress.getLong("capture_max"));
        var area = new AABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
        ItemStack expected = read(operation, "output");
        int recovered = progress.getInt("recovered");
        if (evidence.contains("unspawned_result", Tag.TAG_COMPOUND)) {
            var result = ItemStack.parse(operation.level().registryAccess(), evidence.getCompound("unspawned_result")).orElseThrow();
            operation.returned(AEItemKey.of(result), result.getCount());
            recovered = Math.addExact(recovered, result.getCount());
            evidence.remove("unspawned_result");
            ledger.write(operation.id(), evidence);
        }
        for (var entity : operation.level().getEntitiesOfClass(ItemEntity.class, area, item -> PackagedEntityCapture.ownedBy(item, operation.id()))) {
            ItemStack stack = entity.getItem().copy();
            if (entity.getPersistentData().getBoolean("data_energistics_ritual_result") && PackagedOutputMatching.sameKey(operation, expected, stack)) recovered = Math.addExact(recovered, stack.getCount());
            entity.discard();
            operation.returned(AEItemKey.of(stack), stack.getCount());
        }
        if (progress.getInt("recovered") != recovered) {
            progress.putInt("recovered", recovered);
            operation.changed();
        }
        if (recovered > expected.getCount()) throw new IllegalStateException("Excess forest ritual output");
        return evidence.getBoolean("completed") && recovered == expected.getCount();
    }

    @Override
    public boolean recoverRemoved(PackagedMachineOperation operation) {
        var progress = operation.progress();
        var ledger = NatureRitualLedger.get(operation.level());
        if (progress.getString("phase").equals("ritual")) {
            if (collect(operation)) {
                ledger.release(operation.id());
                return true;
            }
            if (ledger.read(operation.id()).getBoolean("completed")) return false;
        }
        long[] stands = progress.getLongArray("stands");
        for (long encoded : stands) {
            BlockPos pos = BlockPos.of(encoded);
            if (!operation.level().isLoaded(pos)) return false;
            if (operation.level().getBlockEntity(pos) instanceof BlockEntityWoodStand stand) {
                var state = (WoodStandRitualAccessor) stand;
                if (state.dataEnergistics$recipe() != null && operation.position().above().equals(state.dataEnergistics$ritualPosition())) {
                    state.dataEnergistics$recipe(null);
                    state.dataEnergistics$ritualPosition(null);
                    state.dataEnergistics$timer(0);
                    stand.setChanged();
                }
            }
        }
        var evidence = ledger.read(operation.id());
        for (var encoded : evidence.getList("consumed_materials", Tag.TAG_COMPOUND)) {
            var consumed = ItemStack.parse(operation.level().registryAccess(), (CompoundTag) encoded).orElseThrow();
            operation.returned(AEItemKey.of(consumed), consumed.getCount());
        }
        evidence.remove("consumed_materials");
        ledger.write(operation.id(), evidence);
        for (int index = progress.getInt("material_recovery_cursor"); index < progress.getInt("material_cursor"); index++) {
            BlockPos pos = BlockPos.of(stands[index]);
            if (!operation.level().isLoaded(pos)) return false;
            if (operation.level().getBlockEntity(pos) instanceof BlockEntityWoodStand stand) {
                var expected = ItemStack.parseOptional(operation.level().registryAccess(), progress.getList("materials", Tag.TAG_COMPOUND).getCompound(index));
                if (ItemStack.matches(expected, stand.items.getStackInSlot(0))) {
                    ItemStack stack = stand.items.extractItem(0, 1, false);
                    if (!stack.isEmpty()) operation.returned(AEItemKey.of(stack), stack.getCount());
                }
            }
            progress.putInt("material_recovery_cursor", index + 1);
            operation.changed();
        }
        long[] powders = progress.getLongArray("powders");
        for (int index = progress.getInt("powder_recovery_cursor"); index < progress.getInt("powder_cursor"); index++) {
            BlockPos pos = BlockPos.of(powders[index]);
            if (!operation.level().isLoaded(pos)) return false;
            if (operation.level().getBlockState(pos).is(ModBlocks.GOLD_POWDER) && operation.level().removeBlock(pos, false)) operation.returned(AEItemKey.of(ModBlocks.GOLD_POWDER.asItem()), 1);
            progress.putInt("powder_recovery_cursor", index + 1);
            operation.changed();
        }
        BlockPos saplingPos = operation.position().above();
        ItemStack sapling = read(operation, "sapling_item");
        if (!operation.level().isLoaded(saplingPos)) return false;
        if (progress.getBoolean("sapling_placed") && !progress.getBoolean("sapling_recovered")) {
            boolean recoveredSapling = false;
            if (operation.level().getBlockState(saplingPos).is(((BlockItem) sapling.getItem()).getBlock())) {
                recoveredSapling = operation.level().removeBlock(saplingPos, false);
            } else {
                var grown = progress.getList("grown_tree", Tag.TAG_COMPOUND);
                boolean intact = !grown.isEmpty();
                for (var encoded : grown) {
                    var entry = (CompoundTag) encoded;
                    var pos = BlockPos.of(entry.getLong("position"));
                    if (!operation.level().isLoaded(pos)) return false;
                    var generated = NbtUtils.readBlockState(operation.level().holderLookup(Registries.BLOCK), entry.getCompound("grown"));
                    var current = operation.level().getBlockState(pos);
                    // Leaf distance is recomputed on later ticks without changing the generated tree asset.
                    if (current != generated && (!(generated.getBlock() instanceof LeavesBlock) || !current.is(generated.getBlock()))) intact = false;
                }
                // Never refund a sapling while leaving its generated tree available to harvest.
                if (intact) {
                    for (var encoded : grown) {
                        var entry = (CompoundTag) encoded;
                        var pos = BlockPos.of(entry.getLong("position"));
                        var previous = NbtUtils.readBlockState(operation.level().holderLookup(Registries.BLOCK), entry.getCompound("previous"));
                        if (!operation.level().setBlock(pos, pos.equals(saplingPos) ? Blocks.AIR.defaultBlockState() : previous, 2)) throw new IllegalStateException("Unable to revert ritual tree");
                    }
                    recoveredSapling = true;
                }
            }
            if (recoveredSapling) operation.returned(AEItemKey.of(sapling), 1);
            progress.putBoolean("sapling_recovered", true);
            operation.changed();
        }
        var area = new AABB(operation.position()).inflate(12, 33, 12);
        for (var entity : operation.level().getEntitiesOfClass(ItemEntity.class, area, item -> PackagedEntityCapture.ownedBy(item, operation.id()))) {
            ItemStack stack = entity.getItem().copy();
            entity.discard();
            operation.returned(AEItemKey.of(stack), stack.getCount());
        }
        ledger.release(operation.id());
        return true;
    }

    private static void addPowder(ObjectList<Ingredient> ingredients) {
        for (int index = 0; index < positions(BlockPos.ZERO, 'G').size(); index++) ingredients.add(Ingredient.of(ModBlocks.GOLD_POWDER));
    }

    private static ObjectList<BlockPos> positions(BlockPos anchor, char marker) {
        var result = new ObjectArrayList<BlockPos>();
        Multiblocks.TREE_RITUAL.forEach(anchor.above(), marker, (position, matcher) -> {
            result.add(position.immutable());
            return true;
        });
        result.sort(Comparator.comparingLong(BlockPos::asLong));
        return result;
    }

    private static ItemStack read(PackagedMachineOperation operation, String name) {
        return ItemStack.parse(operation.level().registryAccess(), operation.progress().getCompound(name)).orElseThrow();
    }
}
