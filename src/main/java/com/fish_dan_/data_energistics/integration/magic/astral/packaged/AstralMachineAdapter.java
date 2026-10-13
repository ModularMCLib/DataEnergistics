package com.fish_dan_.data_energistics.integration.magic.astral.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedPatternInputCompletion;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;
import com.fish_dan_.data_energistics.integration.magic.astral.AstralLiquidSupplyTransaction;
import com.fish_dan_.data_energistics.world.packaged.PackagedRecoveryJournal;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import hellfirepvp.astralsorcery.common.entity.item.ItemEntityAltarInput;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import hellfirepvp.astralsorcery.common.tile.TileAltar;
import hellfirepvp.astralsorcery.common.tile.TileChalice;
import hellfirepvp.astralsorcery.common.tile.TileFocusRelay;
import hellfirepvp.astralsorcery.common.util.inventory.InventoryView;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/**
 * Places one Astral altar cycle and leaves every native condition and result in Astral's tick path.
 */
public final class AstralMachineAdapter implements PackagedMachineAdapter, PackagedPatternInputCompletion {

    private static final int ALTAR_LOADED_RADIUS = 8;
    private static final String DELIVERED = "delivered";
    private static final ResourceLocation ALTAR_ILLUMINATION = ResourceLocation.fromNamespaceAndPath("astralsorcery", "altar_illumination");
    private static final ResourceLocation ALTAR_RESONANCE = ResourceLocation.fromNamespaceAndPath("astralsorcery", "altar_resonance");
    private static final ResourceLocation ALTAR_LUMINANCE = ResourceLocation.fromNamespaceAndPath("astralsorcery", "altar_luminance");
    private static final ResourceLocation ALTAR_RADIANCE = ResourceLocation.fromNamespaceAndPath("astralsorcery", "altar_radiance");
    private static final ObjectSet<ResourceLocation> ALTAR_WORKSTATIONS = ObjectSet.of(
            ALTAR_ILLUMINATION, ALTAR_RESONANCE, ALTAR_LUMINANCE, ALTAR_RADIANCE);

    private final AstralMachineKind kind;

    public AstralMachineAdapter(AstralMachineKind kind) {
        this.kind = kind;
    }

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("astral_sorcery_" + this.kind.recipeType.getPath());
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(this.kind.recipeType);
    }

    @Override
    public ObjectSet<ResourceLocation> workstationItemIds() {
        return ALTAR_WORKSTATIONS;
    }

    @Override
    public @Nullable ItemStack completeEncoding(ServerLevel level, ResourceLocation recipeId, ItemStack encodedPattern) {
        return AstralPatternEncoding.complete(level, recipeId, encodedPattern);
    }

    @Override
    public @Nullable ObjectList<GenericStack> completePatternInputs(ServerLevel level, ResourceLocation recipeId,
                                                                    ObjectList<GenericStack> inputs) {
        return AstralPatternEncoding.completeInputs(level, recipeId, inputs);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        if (!level.isLoaded(position)) return false;
        BlockEntity blockEntity = level.getBlockEntity(position);
        return blockEntity instanceof TileAltar;
    }

    @Override
    public boolean supportsRecipe(ServerLevel level, BlockPos position, ResourceLocation recipeTypeId,
                                  ResourceLocation recipeId) {
        if (!this.recipeTypes().contains(recipeTypeId) || !this.recognizes(level, position)) return false;
        var holder = level.getRecipeManager().byKey(recipeId);
        return holder.isPresent() && this.kind.accepts(holder.get().value());
    }

    @Override
    public long batchCapacity(ServerLevel level, BlockPos position, Direction face, ResourceLocation recipeId,
                              IPatternDetails pattern, KeyCounter[] prototype, long requestedCount) {
        return requestedCount == 1 ? 1 : 0;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        return prepareAltar(level, position, recipeId, pattern, inputs);
    }

    @Override
    public LongList occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var positions = new LongArrayList();
        positions.add(position.asLong());
        ListTag relay = preparation.getList("relay", Tag.TAG_COMPOUND);
        var offsets = TileAltar.getRelayGridOffsets();
        for (int index = 0; index < relay.size(); index++) {
            if (ItemStack.parseOptional(level.registryAccess(), relay.getCompound(index)).isEmpty()) continue;
            BlockPos offset = offsets.get(index);
            if (offset != null) positions.add(position.offset(offset).asLong());
        }
        return positions;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        return advanceAltar(operation);
    }

    @Override
    public boolean recoverRemoved(PackagedMachineOperation operation) {
        ServerLevel level = operation.level();
        for (long packed : occupiedPositions(level, operation.position(), operation.progress())) {
            if (!level.isLoaded(BlockPos.of(packed))) return false;
        }
        ListTag fluidDeposits = operation.progress().getList("pattern_fluid_deposits", Tag.TAG_COMPOUND);
        for (int index = 0; index < fluidDeposits.size(); index++) {
            if (!level.isLoaded(BlockPos.of(fluidDeposits.getCompound(index).getLong("position")))) return false;
        }
        CompoundTag evidence = PackagedRecoveryJournal.get(level).read(operation.id());
        evidence.putBoolean("observed", true);
        PackagedRecoveryJournal.get(level).write(operation.id(), evidence);

        ObjectList<InventoryView> inventories = new ObjectArrayList<>();
        if (level.getBlockEntity(operation.position()) instanceof TileAltar altar) {
            altar.abortCrafting();
            inventories.add(altar.getTileData().getAltarInventory());
            ListTag relay = operation.progress().getList("relay", Tag.TAG_COMPOUND);
            var offsets = TileAltar.getRelayGridOffsets();
            for (int index = 0; index < relay.size(); index++) {
                if (ItemStack.parseOptional(level.registryAccess(), relay.getCompound(index)).isEmpty()) continue;
                BlockPos offset = offsets.get(index);
                if (offset != null && level.getBlockEntity(operation.position().offset(offset)) instanceof TileFocusRelay focusRelay) {
                    inventories.add(focusRelay.getTileData().getInventory());
                }
            }
        }

        ObjectList<ItemStack> known = knownAssets(operation);
        for (InventoryView inventory : inventories) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack present = inventory.getStackInSlot(slot);
                if (!present.isEmpty() && !knownAsset(known, present)) return false;
            }
        }
        for (InventoryView inventory : inventories) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack present = inventory.getStackInSlot(slot);
                if (present.isEmpty()) continue;
                ItemStack extracted = inventory.extractItem(slot, present.getCount(), false);
                if (extracted.isEmpty() || extracted.getCount() != present.getCount()) {
                    throw new IllegalStateException("Astral recovery extraction was incomplete");
                }
                operation.returned(AEItemKey.of(extracted), extracted.getCount());
            }
        }
        for (ItemEntity entity : ownedEntities(operation)) {
            ItemStack stack = entity.getItem().copy();
            if (!stack.isEmpty()) operation.returned(AEItemKey.of(stack), stack.getCount());
            entity.discard();
        }
        recoverPatternFluidDeposits(operation, fluidDeposits);
        return true;
    }

    private @Nullable CompoundTag prepareAltar(ServerLevel level, BlockPos position, ResourceLocation recipeId,
                                               IPatternDetails pattern, KeyCounter[] inputs) {
        TileAltar altar = altarLayout(level, position);
        if (altar == null || altar.getTileData().getActiveRecipe().isPresent() || !empty(altar.getTileData().getAltarInventory()))
            return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof AltarRecipe recipe) || !this.kind.accepts(recipe))
            return null;
        AstralRecipePlanner.AstralAltarPlan plan = AstralRecipePlanner.plan(level, recipe, pattern, inputs);
        if (plan == null || !emptyRelaySlots(level, position, plan.relay()) || !allRelaySlotsEmpty(level, position))
            return null;

        var progress = new CompoundTag();
        saveStacks(progress, "grid", plan.grid(), level);
        saveStacks(progress, "relay", plan.relay(), level);
        saveStacks(progress, "additional", plan.additional(), level);
        saveStacks(progress, "returns", plan.returns(), level);
        saveStacks(progress, "slot_returns", plan.slotReturns(), level);
        saveStacks(progress, "outputs", plan.outputs(), level);
        progress.putBoolean(DELIVERED, false);
        return progress;
    }

    private boolean advanceAltar(PackagedMachineOperation operation) {
        TileAltar altar = altarLayout(operation.level(), operation.position());
        if (altar == null) return false;
        if (operation.progress().getBoolean(DELIVERED)) {
            if (altar.getTileData().getActiveRecipe().isPresent()) return false;
            // Astral may leave the delivered grid and relay inputs in place while it waits for native
            // conditions. Keep those inputs and retry the native start instead of treating them as output.
            if (!hasOwnedOutputEntities(operation) &&
                    ((!hasPreparedPhysicalInputs(operation)) || preparedInputsPresent(operation, altar))) {
                return restartAltar(operation, altar);
            }
            if (collectAltar(operation, altar)) {
                clearPatternFluidDeposits(operation);
                operation.progress().putBoolean(DELIVERED, false);
                operation.changed();
                operation.complete();
                return true;
            }
            return false;
        }
        if (altar.getTileData().getActiveRecipe().isPresent()) return false;
        return deliverAltar(operation, altar);
    }

    private boolean deliverAltar(PackagedMachineOperation operation, TileAltar altar) {
        var progress = operation.progress();
        ObjectList<ItemStack> grid = readStacks(operation, "grid");
        ObjectList<ItemStack> relay = readStacks(operation, "relay");
        ObjectList<ItemStack> additional = readStacks(operation, "additional");
        RecipeHolder<AltarRecipe> recipe = altarRecipe(operation.level(), operation.recipeId());
        if (grid.size() != 9 || relay.size() != 25 || !empty(altar.getTileData().getAltarInventory()) ||
                !emptyRelaySlots(operation.level(), operation.position(), relay) ||
                !allRelaySlotsEmpty(operation.level(), operation.position()))
            return false;
        if (recipe == null) return false;

        var required = new KeyCounter();
        for (ItemStack stack : grid) if (!stack.isEmpty()) required.add(AEItemKey.of(stack), stack.getCount());
        for (ItemStack stack : relay) if (!stack.isEmpty()) required.add(AEItemKey.of(stack), stack.getCount());
        for (ItemStack stack : additional) if (!stack.isEmpty()) required.add(AEItemKey.of(stack), stack.getCount());
        requireAvailable(operation, required);

        InventoryView altarInventory = altar.getTileData().getAltarInventory();
        for (int slot = 0; slot < grid.size(); slot++) insert(operation, altarInventory, slot, grid.get(slot));
        var offsets = TileAltar.getRelayGridOffsets();
        for (int slot = 0; slot < relay.size(); slot++) {
            ItemStack stack = relay.get(slot);
            if (stack.isEmpty()) continue;
            BlockPos offset = offsets.get(slot);
            if (offset == null || !(operation.level().getBlockEntity(operation.position().offset(offset)) instanceof TileFocusRelay focusRelay))
                return false;
            insert(operation, focusRelay.getTileData().getInventory(), 0, stack);
        }
        for (ItemStack stack : additional) spawnAdditional(operation, stack);
        deliverOptionalFluidInputs(operation, recipe.value());
        progress.putBoolean(DELIVERED, true);
        operation.changed();
        if (startableAltar(operation, altar)) startAltar(operation, altar);
        return true;
    }

    /**
     * A pattern may declare Astral's native liquid or omit it when a chalice is preloaded. Returning a declared
     * amount before the native cycle prevents the same network fluid from being reserved twice; the native draw
     * transaction still consumes nearby chalices first and digital supply interfaces only as its fallback.
     */
    private static void deliverOptionalFluidInputs(PackagedMachineOperation operation, AltarRecipe recipe) {
        for (AEFluidKey key : AstralRecipePlanner.acceptedFluidKeys(recipe)) {
            var available = operation.available(key);
            if (available.signum() <= 0) continue;
            long required = AstralRecipePlanner.requiredFluidAmount(recipe, key);
            long remaining = Math.min(available.longValueExact(), required);
            long deposited = 0;
            while (deposited < remaining) {
                int offered = (int) Math.min(remaining - deposited, Integer.MAX_VALUE);
                var transaction = new AstralLiquidSupplyTransaction();
                ObjectList<AstralLiquidSupplyTransaction.ChaliceDeposit> fills = transaction.depositToChalices(
                        operation.level(), operation.position(), key.toStack(offered));
                int inserted = 0;
                for (var fill : fills) {
                    operation.delivered(key, fill.amount());
                    recordPatternFluidDeposit(operation, key, fill.position(), fill.amount());
                    inserted = Math.addExact(inserted, fill.amount());
                }
                deposited = Math.addExact(deposited, inserted);
                if (inserted < offered) break;
            }
            long returned = available.longValueExact() - deposited;
            if (returned > 0) {
                operation.delivered(key, returned);
                operation.returned(key, returned);
            }
        }
    }

    private static void recordPatternFluidDeposit(PackagedMachineOperation operation, AEFluidKey key,
                                                  long position, int amount) {
        ListTag deposits = operation.progress().getList("pattern_fluid_deposits", Tag.TAG_COMPOUND);
        var entry = new CompoundTag();
        entry.putLong("position", position);
        entry.put("stack", GenericStack.writeTag(operation.level().registryAccess(), new GenericStack(key, amount)));
        deposits.add(entry);
        operation.progress().put("pattern_fluid_deposits", deposits);
        operation.changed();
    }

    private static void recoverPatternFluidDeposits(PackagedMachineOperation operation, ListTag deposits) {
        for (int index = 0; index < deposits.size(); index++) {
            CompoundTag entry = deposits.getCompound(index);
            GenericStack stack = GenericStack.readTag(operation.level().registryAccess(), entry.getCompound("stack"));
            if (stack == null || !(stack.what() instanceof AEFluidKey key) || stack.amount() <= 0 ||
                    stack.amount() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid persisted Astral chalice deposit");
            }
            BlockPos position = BlockPos.of(entry.getLong("position"));
            if (!(operation.level().getBlockEntity(position) instanceof TileChalice chalice)) continue;
            FluidStack requested = key.toStack((int) stack.amount());
            FluidStack drained = chalice.getTankView().drain(requested, IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty() && (!FluidStack.isSameFluidSameComponents(requested, drained) ||
                    drained.getAmount() > requested.getAmount())) {
                throw new IllegalStateException("Astral chalice returned an invalid fluid during packaged recovery");
            }
            if (!drained.isEmpty()) operation.returned(key, drained.getAmount());
        }
        deposits.clear();
        operation.progress().put("pattern_fluid_deposits", deposits);
        operation.changed();
    }

    private static void clearPatternFluidDeposits(PackagedMachineOperation operation) {
        operation.progress().put("pattern_fluid_deposits", new ListTag());
        operation.changed();
    }

    private boolean restartAltar(PackagedMachineOperation operation, TileAltar altar) {
        return startableAltar(operation, altar) && startAltar(operation, altar);
    }

    private static boolean hasPreparedPhysicalInputs(PackagedMachineOperation operation) {
        for (ItemStack stack : readStacks(operation, "grid")) if (!stack.isEmpty()) return true;
        for (ItemStack stack : readStacks(operation, "relay")) if (!stack.isEmpty()) return true;
        return false;
    }

    private static boolean preparedInputsPresent(PackagedMachineOperation operation, TileAltar altar) {
        ObjectList<ItemStack> grid = readStacks(operation, "grid");
        ObjectList<ItemStack> relay = readStacks(operation, "relay");
        if (grid.size() != 9 || relay.size() != 25) return false;

        InventoryView altarInventory = altar.getTileData().getAltarInventory();
        for (int slot = 0; slot < grid.size(); slot++) {
            if (!ItemStack.matches(grid.get(slot), altarInventory.getStackInSlot(slot))) return false;
        }
        for (int slot = 0; slot < relay.size(); slot++) {
            if (!ItemStack.matches(relay.get(slot), relayStack(operation.level(), operation.position(), slot))) return false;
        }
        return true;
    }

    private static boolean hasOwnedOutputEntities(PackagedMachineOperation operation) {
        for (ItemEntity entity : ownedEntities(operation)) {
            if (!isAltarInput(operation, entity)) return true;
        }
        return false;
    }

    private boolean startableAltar(PackagedMachineOperation operation, TileAltar altar) {
        var holder = altarRecipe(operation.level(), operation.recipeId());
        return holder != null && holder.value().matches(altar.createInput(operation.level(), operation.id()), operation.level());
    }

    private boolean startAltar(PackagedMachineOperation operation, TileAltar altar) {
        var holder = altarRecipe(operation.level(), operation.recipeId());
        if (holder == null) return false;
        altar.startCrafting(holder, operation.id());
        return altar.getTileData().getActiveRecipe().map(active -> operation.recipeId().equals(active.getRecipeId())).orElse(false);
    }

    private boolean collectAltar(PackagedMachineOperation operation, TileAltar altar) {
        ObjectList<ItemStack> expected = readStacks(operation, "outputs");
        ObjectList<ItemStack> actual = new ObjectArrayList<>();
        ObjectList<ItemStack> returns = readStacks(operation, "slot_returns");
        InventoryView altarInventory = altar.getTileData().getAltarInventory();
        for (int index = 0; index < 9; index++) {
            ItemStack expectedReturn = index < returns.size() ? returns.get(index) : ItemStack.EMPTY;
            actual.add(readPresent(expectedReturn, altarInventory, index));
        }
        var offsets = TileAltar.getRelayGridOffsets();
        int returnIndex = 9;
        for (int slot = 0; slot < 25; slot++) {
            ItemStack expectedReturn = returnIndex < returns.size() ? returns.get(returnIndex) : ItemStack.EMPTY;
            ItemStack present = ItemStack.EMPTY;
            BlockPos offset = offsets.get(slot);
            if (offset != null && operation.level().getBlockEntity(operation.position().offset(offset)) instanceof TileFocusRelay relay) {
                present = relay.getTileData().getInventory().getStackInSlot(0);
            }
            if (!expectedReturn.isEmpty() || !present.isEmpty()) actual.add(present);
            returnIndex++;
        }
        for (ItemEntity entity : ownedEntities(operation)) {
            if (isAltarInput(operation, entity)) continue;
            actual.add(entity.getItem().copy());
        }
        if (!PackagedOutputMatching.acceptsPartial(operation, expected, actual)) {
            throw new IllegalStateException("Unexpected Astral altar output");
        }
        if (!PackagedOutputMatching.matches(operation, expected, actual)) return false;

        for (int index = 0; index < 9; index++)
            harvestExpected(operation, altarInventory, index,
                    index < returns.size() ? returns.get(index) : ItemStack.EMPTY);
        returnIndex = 9;
        for (int slot = 0; slot < 25; slot++) {
            ItemStack expectedReturn = returnIndex < returns.size() ? returns.get(returnIndex) : ItemStack.EMPTY;
            BlockPos offset = offsets.get(slot);
            if (offset != null && operation.level().getBlockEntity(operation.position().offset(offset)) instanceof TileFocusRelay relay) {
                harvestExpected(operation, relay.getTileData().getInventory(), 0, expectedReturn);
            }
            returnIndex++;
        }
        for (ItemEntity entity : ownedEntities(operation)) {
            if (isAltarInput(operation, entity)) continue;
            ItemStack stack = entity.getItem().copy();
            entity.discard();
            operation.returned(AEItemKey.of(stack), stack.getCount());
        }
        return true;
    }

    private static void spawnAdditional(PackagedMachineOperation operation, ItemStack stack) {
        var position = operation.position().getCenter().add(0, 1, 0);
        var entity = new ItemEntity(operation.level(), position.x, position.y, position.z, stack.copy(), 0, 0, 0);
        entity.setNoGravity(true);
        entity.setUnlimitedLifetime();
        entity.setPickUpDelay(0);
        PackagedEntityCapture.run(operation.level(), operation.id(), () -> {
            if (!operation.level().addFreshEntity(entity))
                throw new IllegalStateException("Astral altar input entity was rejected");
        });
        operation.delivered(AEItemKey.of(stack), stack.getCount());
        var ids = operation.progress().getList("additional_entities", Tag.TAG_COMPOUND);
        var encoded = new CompoundTag();
        encoded.putUUID("id", entity.getUUID());
        ids.add(encoded);
        operation.progress().put("additional_entities", ids);
        operation.changed();
    }

    private static void insert(PackagedMachineOperation operation, InventoryView inventory, int slot, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!inventory.insertItem(slot, stack.copy(), true).isEmpty()) return;
        ItemStack rejected = inventory.insertItem(slot, stack.copy(), false);
        int delivered = stack.getCount() - rejected.getCount();
        if (delivered > 0) operation.delivered(AEItemKey.of(stack), delivered);
        if (!rejected.isEmpty()) throw new IllegalStateException("Astral inventory refused a simulated insertion");
    }

    private static void harvestExpected(PackagedMachineOperation operation, InventoryView inventory, int slot, ItemStack expected) {
        ItemStack present = inventory.getStackInSlot(slot);
        if (expected.isEmpty()) {
            if (!present.isEmpty()) throw new IllegalStateException("Unexpected Astral machine remainder");
            return;
        }
        if (!ItemStack.matches(expected, present)) throw new IllegalStateException("Astral machine remainder changed");
        ItemStack extracted = inventory.extractItem(slot, present.getCount(), false);
        if (!ItemStack.matches(expected, extracted))
            throw new IllegalStateException("Astral remainder extraction was incomplete");
        operation.returned(AEItemKey.of(extracted), extracted.getCount());
    }

    private static ItemStack readPresent(ItemStack expected, InventoryView inventory, int slot) {
        ItemStack present = inventory.getStackInSlot(slot);
        if (expected.isEmpty() && !present.isEmpty())
            throw new IllegalStateException("Unexpected Astral altar slot content");
        if (!expected.isEmpty() && !ItemStack.matches(expected, present))
            throw new IllegalStateException("Astral altar remainder changed");
        return present.copy();
    }

    private static void requireAvailable(PackagedMachineOperation operation, KeyCounter required) {
        for (var entry : required) {
            if (operation.available(entry.getKey()).compareTo(BigInteger.valueOf(entry.getLongValue())) < 0) {
                throw new IllegalStateException("Astral packaged operation exceeds owned inputs");
            }
        }
    }

    private @Nullable TileAltar altarLayout(ServerLevel level, BlockPos position) {
        if (!this.recognizes(level, position) || !loadedRadius(level, position, ALTAR_LOADED_RADIUS)) return null;
        if (!(level.getBlockEntity(position) instanceof TileAltar altar)) return null;
        return altar.hasStructure() ? altar : null;
    }

    private static boolean loadedRadius(ServerLevel level, BlockPos center, int radius) {
        for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++) {
            for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++) {
                if (!level.hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    private static boolean empty(InventoryView inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++)
            if (!inventory.getStackInSlot(slot).isEmpty()) return false;
        return true;
    }

    private static boolean emptyRelaySlots(ServerLevel level, BlockPos position, ObjectList<ItemStack> relay) {
        var offsets = TileAltar.getRelayGridOffsets();
        for (int index = 0; index < relay.size(); index++) {
            if (relay.get(index).isEmpty()) continue;
            BlockPos offset = offsets.get(index);
            if (offset == null || !(level.getBlockEntity(position.offset(offset)) instanceof TileFocusRelay focusRelay) ||
                    !empty(focusRelay.getTileData().getInventory()))
                return false;
        }
        return true;
    }

    private static boolean allRelaySlotsEmpty(ServerLevel level, BlockPos position) {
        for (BlockPos offset : TileAltar.getRelayGridOffsets().values()) {
            if (level.getBlockEntity(position.offset(offset)) instanceof TileFocusRelay relay &&
                    !empty(relay.getTileData().getInventory())) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack relayStack(ServerLevel level, BlockPos position, int slot) {
        BlockPos offset = TileAltar.getRelayGridOffsets().get(slot);
        if (offset == null || !(level.getBlockEntity(position.offset(offset)) instanceof TileFocusRelay relay)) {
            return ItemStack.EMPTY;
        }
        return relay.getTileData().getInventory().getStackInSlot(0).copy();
    }

    private static @Nullable RecipeHolder<AltarRecipe> altarRecipe(ServerLevel level, ResourceLocation id) {
        var holder = level.getRecipeManager().byKey(id);
        return holder.isPresent() && holder.get().value() instanceof AltarRecipe recipe &&
                recipe.getType() == RecipeTypesAS.ALTAR_CRAFTING_TYPE.get() ? new RecipeHolder<>(holder.get().id(), recipe) : null;
    }

    private static ObjectList<ItemEntity> ownedEntities(PackagedMachineOperation operation) {
        var result = new ObjectArrayList<ItemEntity>();
        result.addAll(operation.level().getEntitiesOfClass(ItemEntity.class,
                new AABB(operation.position()).inflate(8), item -> PackagedEntityCapture.ownedBy(item, operation.id())));
        return result;
    }

    private static boolean isAltarInput(PackagedMachineOperation operation, ItemEntity entity) {
        if (entity instanceof ItemEntityAltarInput) return true;
        ListTag ids = operation.progress().getList("additional_entities", Tag.TAG_COMPOUND);
        for (int index = 0; index < ids.size(); index++) {
            if (ids.getCompound(index).hasUUID("id") && ids.getCompound(index).getUUID("id").equals(entity.getUUID()))
                return true;
        }
        return false;
    }

    private static ObjectList<ItemStack> knownAssets(PackagedMachineOperation operation) {
        var result = new ObjectArrayList<ItemStack>();
        for (String name : new String[] { "grid", "relay", "additional", "returns", "slot_returns", "outputs" })
            result.addAll(readStacks(operation, name));
        return result;
    }

    private static boolean knownAsset(ObjectList<ItemStack> known, ItemStack actual) {
        for (ItemStack expected : known) {
            if (ItemStack.isSameItemSameComponents(expected, actual) && actual.getCount() <= expected.getCount())
                return true;
        }
        return false;
    }

    private static void saveStacks(CompoundTag progress, String name, ObjectList<ItemStack> stacks, ServerLevel level) {
        var list = new ListTag();
        for (ItemStack stack : stacks) list.add(stack.save(level.registryAccess()));
        progress.put(name, list);
    }

    private static ObjectList<ItemStack> readStacks(PackagedMachineOperation operation, String name) {
        var result = new ObjectArrayList<ItemStack>();
        ListTag list = operation.progress().getList(name, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            result.add(ItemStack.parseOptional(operation.level().registryAccess(), list.getCompound(index)));
        }
        return result;
    }
}
