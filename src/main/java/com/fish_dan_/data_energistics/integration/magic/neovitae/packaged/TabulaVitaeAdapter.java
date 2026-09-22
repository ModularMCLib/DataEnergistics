package com.fish_dan_.data_energistics.integration.magic.neovitae.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.AABB;

import com.breakinblocks.neovitae.common.blockentity.TabulaVitaeBlockEntity;
import com.breakinblocks.neovitae.common.recipe.NVRecipes;
import com.breakinblocks.neovitae.common.recipe.tabulavitae.TabulaVitaeRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/** Supplies only Tabula Vitae recipe items; the player's pre-installed blood orb remains untouched. */
public final class TabulaVitaeAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation RECIPE_TYPE = ResourceLocation.fromNamespaceAndPath("neovitae", "alchemytable");
    private static final String INPUTS = "inputs";
    private static final String OUTPUT = "output";
    private static final String LOADED = "loaded";
    private static final String OWNER = "data_energistics_tabula_operation";
    private static final String INSERTED = "inserted_slots";

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("neovitae_tabula_vitae");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(RECIPE_TYPE, ResourceLocation.fromNamespaceAndPath("neovitae", "tabula_vitae"));
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof TabulaVitaeBlockEntity table &&
                !table.isSlave();
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, position) || !(level.getBlockEntity(position) instanceof TabulaVitaeBlockEntity table) ||
                !emptyInputsAndOutput(table) || !safePartner(level, table))
            return null;
        TabulaVitaeRecipe recipe = recipe(level, recipeId);
        if (recipe == null || recipe.getInput().size() > TabulaVitaeBlockEntity.ORB_SLOT) return null;
        ObjectList<ItemStack> assigned = PackagedIngredientAssignment.match(
                new ObjectArrayList<Ingredient>(recipe.getInput()), inputs);
        if (assigned == null) return null;
        if (pattern.getOutputs().size() != 1 ||
                !PackagedOutputMatching.matches(pattern, recipe.getOutput(), recipe.getOutput().getCount()))
            return null;
        var result = new CompoundTag();
        result.put(INPUTS, saveStacks(assigned, level));
        result.put(OUTPUT, recipe.getOutput().save(level.registryAccess()));
        return result;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var positions = new ObjectArrayList<BlockPos>();
        positions.add(position);
        if (level.getBlockEntity(position) instanceof TabulaVitaeBlockEntity table) {
            BlockPos partnerPosition = table.getConnectedPos();
            if (level.isLoaded(partnerPosition) &&
                    level.getBlockEntity(partnerPosition) instanceof TabulaVitaeBlockEntity partner && partner.isSlave() &&
                    partner.getMaster() == table)
                positions.add(partnerPosition);
        }
        return positions;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!recognizes(operation.level(), operation.position()) ||
                !(operation.level().getBlockEntity(operation.position()) instanceof TabulaVitaeBlockEntity table))
            return false;
        return operation.progress().getBoolean(LOADED) ? harvest(operation, table) : load(operation, table);
    }

    @Override
    public boolean recoverRemoved(PackagedMachineOperation operation) {
        if (!operation.level().isLoaded(operation.position())) return false;
        if (operation.level().getBlockEntity(operation.position()) instanceof TabulaVitaeBlockEntity table &&
                table.getPersistentData().hasUUID(OWNER) && operation.id().equals(table.getPersistentData().getUUID(OWNER))) {
            table.burnTime = 0;
            int inserted = operation.progress().getInt(INSERTED);
            for (int slot = 0; slot <= TabulaVitaeBlockEntity.OUTPUT_SLOT; slot++) {
                if (slot == TabulaVitaeBlockEntity.ORB_SLOT) continue;
                if ((inserted & 1 << slot) == 0 && !(slot == TabulaVitaeBlockEntity.OUTPUT_SLOT && operation.progress().getBoolean(LOADED))) continue;
                ItemStack actual = table.inv.getStackInSlot(slot);
                if (actual.isEmpty()) continue;
                ItemStack recovered = table.inv.extractItem(slot, actual.getCount(), false);
                if (!sameStack(actual, recovered)) throw new IllegalStateException("Tabula Vitae recovery extraction was incomplete");
                operation.returned(AEItemKey.of(recovered), recovered.getCount());
            }
            table.getPersistentData().remove(OWNER);
            table.setChanged();
        }
        for (var drop : operation.level().getEntitiesOfClass(ItemEntity.class, new AABB(operation.position()).inflate(2),
                item -> PackagedEntityCapture.ownedBy(item, operation.id()))) {
            ItemStack actual = drop.getItem();
            if (!actual.isEmpty()) operation.returned(AEItemKey.of(actual), actual.getCount());
            drop.discard();
        }
        return true;
    }

    private static boolean load(PackagedMachineOperation operation, TabulaVitaeBlockEntity table) {
        if (!emptyInputsAndOutput(table) || !safePartner(operation.level(), table)) return false;
        TabulaVitaeRecipe recipe = recipe(operation.level(), operation.recipeId());
        if (recipe == null) return false;
        ObjectList<ItemStack> inputs = readStacks(operation, operation.progress().getList(INPUTS, Tag.TAG_COMPOUND));
        if (inputs.size() > TabulaVitaeBlockEntity.ORB_SLOT) return false;

        for (int slot = 0; slot < inputs.size(); slot++) {
            ItemStack stack = inputs.get(slot);
            AEItemKey key = AEItemKey.of(stack);
            if (stack.isEmpty() || operation.available(key).compareTo(BigInteger.ONE) < 0 ||
                    !table.inv.insertItem(slot, stack.copy(), true).isEmpty())
                return false;
        }
        table.getPersistentData().putUUID(OWNER, operation.id());
        table.setChanged();
        for (int slot = 0; slot < inputs.size(); slot++) {
            ItemStack stack = inputs.get(slot);
            ItemStack remainder = table.inv.insertItem(slot, stack.copy(), false);
            if (!remainder.isEmpty() || !sameStack(stack, table.inv.getStackInSlot(slot)))
                throw new IllegalStateException("Tabula Vitae rejected a preflighted ingredient");
            operation.delivered(AEItemKey.of(stack), 1);
            operation.progress().putInt(INSERTED, operation.progress().getInt(INSERTED) | 1 << slot);
        }
        operation.progress().putBoolean(LOADED, true);
        operation.changed();
        return true;
    }

    private static boolean harvest(PackagedMachineOperation operation, TabulaVitaeBlockEntity table) {
        ItemStack expected = readStack(operation, OUTPUT);
        ItemStack output = table.inv.getStackInSlot(TabulaVitaeBlockEntity.OUTPUT_SLOT);
        if (!PackagedOutputMatching.matches(operation, expected, output))
            return false;
        ItemStack extractedOutput = table.inv.extractItem(TabulaVitaeBlockEntity.OUTPUT_SLOT, output.getCount(), false);
        if (!sameStack(output, extractedOutput)) throw new IllegalStateException("Tabula Vitae output extraction was incomplete");
        operation.returned(AEItemKey.of(extractedOutput), extractedOutput.getCount());
        for (int slot = 0; slot < TabulaVitaeBlockEntity.ORB_SLOT; slot++) {
            ItemStack remainder = table.inv.getStackInSlot(slot);
            if (remainder.isEmpty()) continue;
            ItemStack extracted = table.inv.extractItem(slot, remainder.getCount(), false);
            if (!sameStack(remainder, extracted))
                throw new IllegalStateException("Tabula Vitae remainder extraction was incomplete");
            operation.returned(AEItemKey.of(extracted), extracted.getCount());
        }
        table.getPersistentData().remove(OWNER);
        table.setChanged();
        operation.complete();
        return true;
    }

    private static boolean safePartner(ServerLevel level, TabulaVitaeBlockEntity table) {
        BlockPos position = table.getConnectedPos();
        if (!level.isLoaded(position) || !(level.getBlockEntity(position) instanceof TabulaVitaeBlockEntity partner) ||
                !partner.isSlave() || partner.getMaster() != table)
            return false;
        // On its first server tick the master reclaims every slave slot into the recipe inputs.
        for (int slot = 0; slot < partner.inv.getSlots(); slot++) {
            if (!partner.inv.getStackInSlot(slot).isEmpty()) return false;
        }
        return true;
    }

    private static @Nullable TabulaVitaeRecipe recipe(ServerLevel level, ResourceLocation recipeId) {
        var holder = level.getRecipeManager().byKey(recipeId);
        return holder.isPresent() && holder.get().value() instanceof TabulaVitaeRecipe recipe &&
                recipe.getType() == NVRecipes.TABULA_VITAE_TYPE.get() ? recipe : null;
    }

    private static boolean emptyInputsAndOutput(TabulaVitaeBlockEntity table) {
        for (int slot = 0; slot < TabulaVitaeBlockEntity.ORB_SLOT; slot++) {
            if (!table.inv.getStackInSlot(slot).isEmpty()) return false;
        }
        return table.inv.getStackInSlot(TabulaVitaeBlockEntity.OUTPUT_SLOT).isEmpty();
    }

    private static boolean sameStack(ItemStack expected, ItemStack actual) {
        return expected.getCount() == actual.getCount() && ItemStack.isSameItemSameComponents(expected, actual);
    }

    private static ListTag saveStacks(ObjectList<ItemStack> stacks, ServerLevel level) {
        var result = new ListTag();
        for (ItemStack stack : stacks) result.add(stack.saveOptional(level.registryAccess()));
        return result;
    }

    private static ObjectList<ItemStack> readStacks(PackagedMachineOperation operation, ListTag encoded) {
        var result = new ObjectArrayList<ItemStack>();
        for (int index = 0; index < encoded.size(); index++) {
            result.add(ItemStack.parseOptional(operation.level().registryAccess(), encoded.getCompound(index)));
        }
        return result;
    }

    private static ItemStack readStack(PackagedMachineOperation operation, String key) {
        return ItemStack.parse(operation.level().registryAccess(), operation.progress().getCompound(key))
                .orElseThrow(() -> new IllegalArgumentException("Invalid Neo Vitae Tabula Vitae stack: " + key));
    }
}
