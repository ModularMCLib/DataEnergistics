package com.fish_dan_.data_energistics.integration.magic.goety.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.PedestalBlockEntity;
import com.Polarice3.Goety.common.crafting.RitualRecipe;
import com.Polarice3.Goety.common.ritual.CraftItemRitual;
import com.Polarice3.Goety.common.ritual.EnchantItemRitual;
import com.Polarice3.Goety.common.ritual.LocateRitual;
import com.Polarice3.Goety.common.ritual.RitualRequirements;
import com.Polarice3.Goety.config.MainConfig;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Starts a native Goety Dark Altar ritual and lets Goety charge its soul cage and timing. */
public final class DarkAltarAdapter implements PackagedMachineAdapter {

    public static final String COMPLETED_OPERATION = "data_energistics_goety_completed_operation";
    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("goety", "ritual");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("goety_dark_altar");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof DarkAltarBlockEntity;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, position) || !(level.getBlockEntity(position) instanceof DarkAltarBlockEntity altar) || altar.getCurrentRitualRecipe() != null || !altar.itemStackHandler.getStackInSlot(0).isEmpty()) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof RitualRecipe recipe)) return null;
        if (recipe.isSummoning() || recipe.isConversion() || !itemRitual(recipe)) return null;
        ItemStack activation = take(inputs, recipe.getActivationItem());
        if (activation.isEmpty()) return null;
        var remaining = new KeyCounter();
        for (var counter : inputs) for (var entry : counter) remaining.add(entry.getKey(), entry.getLongValue());
        remaining.remove(AEItemKey.of(activation), 1);
        ListTag ingredients = new ListTag();
        for (var ingredient : recipe.getIngredients()) {
            ItemStack stack = take(remaining, ingredient);
            if (stack.isEmpty()) return null;
            var entry = new CompoundTag();
            entry.put("stack", stack.saveOptional(level.registryAccess()));
            ingredients.add(entry);
        }
        if (hasRemaining(remaining)) return null;
        ObjectList<PedestalBlockEntity> pedestals = emptyPedestals(recipe, level, position);
        if (pedestals.size() < ingredients.size()) return null;
        for (int index = 0; index < ingredients.size(); index++) {
            ingredients.getCompound(index).putLong("position", pedestals.get(index).getBlockPos().asLong());
        }
        if (!(level.getBlockEntity(position.below()) instanceof CursedCageBlockEntity cage) || cage.getItem().isEmpty()) return null;
        if (!RitualRequirements.getProperStructure(recipe.getCraftType(), altar, position, level)) return null;
        ItemStack result = declaredPrimary(pattern);
        if (result.isEmpty() || !plausibleResult(level, recipe, activation, result)) return null;
        var returns = recipe.getIngredients().stream().map(ingredient -> ItemStack.EMPTY).collect(ObjectArrayList.toList());
        for (int index = 0; index < ingredients.size(); index++) {
            ItemStack stack = ItemStack.parseOptional(level.registryAccess(), ingredients.getCompound(index).getCompound("stack"));
            if (stack.hasCraftingRemainingItem()) returns.set(index, stack.getCraftingRemainingItem());
        }
        returns.removeIf(ItemStack::isEmpty);
        if (!PackagedOutputMatching.matchesWithAdditionalReturns(pattern, result, returns)) return null;
        var progress = new CompoundTag();
        progress.put("activation", activation.save(level.registryAccess()));
        progress.put("ingredients", ingredients);
        progress.put("result", result.save(level.registryAccess()));
        return progress;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var positions = new ObjectArrayList<BlockPos>();
        positions.add(position);
        positions.add(position.below());
        ListTag ingredients = preparation.getList("ingredients", Tag.TAG_COMPOUND);
        for (int index = 0; index < ingredients.size(); index++) {
            positions.add(BlockPos.of(ingredients.getCompound(index).getLong("position")));
        }
        return positions;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!recognizes(operation.level(), operation.position()) || !(operation.level().getBlockEntity(operation.position()) instanceof DarkAltarBlockEntity altar)) return false;
        CompoundTag progress = operation.progress();
        if (!progress.getBoolean("started")) {
            RitualRecipe recipe = recipe(operation);
            if (altar.getCurrentRitualRecipe() != null || !altar.itemStackHandler.getStackInSlot(0).isEmpty()) return false;
            ItemStack activation = read(operation, "activation");
            ServerPlayer player = ritualPlayer(operation);
            if (!validResources(operation, altar, recipe, activation, player)) return false;
            AEItemKey activationKey = AEItemKey.of(activation);
            ListTag list = progress.getList("ingredients", Tag.TAG_COMPOUND);
            int delivered = progress.getInt("ingredients_delivered");
            var required = new KeyCounter();
            required.add(activationKey, activation.getCount());
            for (int index = 0; index < list.size(); index++) {
                ItemStack ingredient = ItemStack.parseOptional(operation.level().registryAccess(), list.getCompound(index).getCompound("stack"));
                BlockPos pedestalPosition = BlockPos.of(list.getCompound(index).getLong("position"));
                if (!(operation.level().getBlockEntity(pedestalPosition) instanceof PedestalBlockEntity pedestal)) return false;
                if (index < delivered) {
                    if (!ItemStack.matches(ingredient, pedestal.itemStackHandler.getStackInSlot(0))) return false;
                } else {
                    if (!pedestal.itemStackHandler.getStackInSlot(0).isEmpty()) return false;
                    required.add(AEItemKey.of(ingredient), ingredient.getCount());
                }
            }
            for (var entry : required) if (operation.available(entry.getKey()).compareTo(BigInteger.valueOf(entry.getLongValue())) < 0) return false;
            for (int index = delivered; index < list.size(); index++) {
                ItemStack ingredient = ItemStack.parseOptional(operation.level().registryAccess(), list.getCompound(index).getCompound("stack"));
                PedestalBlockEntity pedestal = (PedestalBlockEntity) operation.level().getBlockEntity(
                        BlockPos.of(list.getCompound(index).getLong("position")));
                try {
                    ItemStack rejected = pedestal.itemStackHandler.insertItem(0, ingredient.copy(), false);
                    if (!rejected.isEmpty()) throw new IllegalStateException("Goety pedestal insertion changed after validation");
                } finally {
                    if (ItemStack.matches(ingredient, pedestal.itemStackHandler.getStackInSlot(0))) {
                        operation.delivered(AEItemKey.of(ingredient), ingredient.getCount());
                        progress.putInt("ingredients_delivered", index + 1);
                        operation.changed();
                    }
                }
            }
            if (!recipe.getRitual().isValid(operation.level(), operation.position(), altar, player, activation, recipe.getIngredients())) return false;
            var journal = PackagedRecoveryJournal.get(operation.level());
            var evidence = journal.read(operation.id());
            evidence.putBoolean("observed", true);
            journal.write(operation.id(), evidence);
            try {
                altar.startRitual(player, activation.copy(), recipe, operation.recipeId());
            } finally {
                if (ItemStack.matches(activation, altar.itemStackHandler.getStackInSlot(0))) {
                    operation.delivered(activationKey, activation.getCount());
                    progress.putBoolean("started", true);
                    operation.changed();
                }
            }
            if (!operation.recipeId().equals(altar.currentRitualRecipeId)) throw new IllegalStateException("Goety altar failed to retain the selected ritual");
            return true;
        }
        if (altar.getCurrentRitualRecipe() != null) {
            if (!operation.recipeId().equals(altar.currentRitualRecipeId)) throw new IllegalStateException("Another Goety ritual replaced the packaged operation");
            if (altar.castingPlayer == null && profile(operation.id()).getId().equals(altar.castingPlayerId)) {
                altar.castingPlayer = FakePlayerFactory.get(operation.level(), profile(operation.id()));
                altar.castingPlayer.setPos(operation.position().getX() + 0.5, operation.position().getY() + 1, operation.position().getZ() + 0.5);
            }
            return false;
        }
        CompoundTag nativeState = altar.getPersistentData();
        if (!nativeState.hasUUID(COMPLETED_OPERATION) || !operation.id().equals(nativeState.getUUID(COMPLETED_OPERATION))) return false;
        if (!PackagedOutputMatching.matches(operation, read(operation, "result"), altar.itemStackHandler.getStackInSlot(0))) return false;
        ItemStack slot = altar.itemStackHandler.getStackInSlot(0);
        if (!slot.isEmpty()) {
            ItemStack extracted = altar.itemStackHandler.extractItem(0, slot.getCount(), false);
            operation.returned(AEItemKey.of(extracted), extracted.getCount());
        }
        for (ItemEntity entity : ownedEntities(operation)) {
            ItemStack stack = entity.getItem().copy();
            entity.discard();
            operation.returned(AEItemKey.of(stack), stack.getCount());
        }
        nativeState.remove(COMPLETED_OPERATION);
        altar.setChanged();
        operation.complete();
        return true;
    }

    private static ServerPlayer ritualPlayer(PackagedMachineOperation operation) {
        var player = FakePlayerFactory.get(operation.level(), profile(operation.id()));
        player.setPos(operation.position().getX() + 0.5, operation.position().getY() + 1, operation.position().getZ() + 0.5);
        return player;
    }

    @Override
    public boolean recoverRemoved(PackagedMachineOperation operation) {
        for (BlockPos position : occupiedPositions(operation.level(), operation.position(), operation.progress())) {
            if (!operation.level().isLoaded(position)) return false;
        }
        CompoundTag progress = operation.progress();
        var ledger = PackagedRecoveryJournal.get(operation.level());
        if (!progress.getBoolean("recovery_inspected")) {
            if (operation.level().getBlockEntity(operation.position()) instanceof DarkAltarBlockEntity altar) {
                if (altar.getPersistentData().hasUUID(COMPLETED_OPERATION) && operation.id().equals(altar.getPersistentData().getUUID(COMPLETED_OPERATION))) {
                    var evidence = ledger.read(operation.id());
                    evidence.putBoolean("observed", true);
                    evidence.putBoolean("completed", true);
                    ledger.write(operation.id(), evidence);
                }
                altar.clearRitual();
                altar.getPersistentData().remove(COMPLETED_OPERATION);
                altar.setChanged();
            }
            progress.putBoolean("recovery_inspected", true);
            operation.changed();
        }
        if (!progress.getBoolean("recovery_collected")) {
            if (progress.getBoolean("started") && operation.level().getBlockEntity(operation.position()) instanceof DarkAltarBlockEntity altar) {
                ItemStack stack = altar.itemStackHandler.extractItem(0, altar.itemStackHandler.getStackInSlot(0).getCount(), false);
                if (!stack.isEmpty()) operation.returned(AEItemKey.of(stack), stack.getCount());
            }
            ListTag ingredients = progress.getList("ingredients", Tag.TAG_COMPOUND);
            int count = progress.getBoolean("started") ? ingredients.size() : progress.getInt("ingredients_delivered");
            for (int index = 0; index < count; index++) {
                BlockPos position = BlockPos.of(ingredients.getCompound(index).getLong("position"));
                if (operation.level().getBlockEntity(position) instanceof PedestalBlockEntity pedestal) {
                    ItemStack stack = pedestal.itemStackHandler.extractItem(0, pedestal.itemStackHandler.getStackInSlot(0).getCount(), false);
                    if (!stack.isEmpty()) operation.returned(AEItemKey.of(stack), stack.getCount());
                }
            }
            progress.putBoolean("recovery_collected", true);
            operation.changed();
        }
        for (ItemEntity entity : ownedEntities(operation)) {
            ItemStack stack = entity.getItem().copy();
            entity.discard();
            operation.returned(AEItemKey.of(stack), stack.getCount());
        }
        var evidence = ledger.read(operation.id());
        if (progress.getBoolean("started") && !evidence.getBoolean("observed")) {
            throw new IllegalStateException("Started Dark Altar operation has no consumption evidence");
        }
        if (!evidence.getBoolean("completed")) {
            ListTag consumed = evidence.getList("consumed", Tag.TAG_COMPOUND);
            int cursor = evidence.getInt("refunded");
            if (cursor < 0 || cursor > consumed.size()) throw new IllegalStateException("Invalid Goety native refund cursor");
            for (int index = cursor; index < consumed.size(); index++) {
                CompoundTag receipt = consumed.getCompound(index);
                if (receipt.getBoolean("refund_input")) {
                    ItemStack stack = ItemStack.parse(operation.level().registryAccess(), receipt.getCompound("input")).orElseThrow();
                    operation.returned(AEItemKey.of(stack), stack.getCount());
                }
                evidence.putInt("refunded", index + 1);
                ledger.write(operation.id(), evidence);
            }
        }
        operation.changed();
        return true;
    }

    private static boolean validResources(PackagedMachineOperation operation, DarkAltarBlockEntity altar, RitualRecipe recipe,
                                          ItemStack activation, ServerPlayer player) {
        if (!(operation.level().getBlockEntity(operation.position().below()) instanceof CursedCageBlockEntity cage) || cage.getItem().isEmpty()) return false;
        if (cage.getSouls() <= 0 || cage.getSouls() < recipe.getSoulCost()) return false;
        if (!RitualRequirements.getProperStructure(recipe.getCraftType(), player, altar, operation.position(), operation.level())) return false;
        if (recipe.getRitual() instanceof EnchantItemRitual enchant) {
            if (!MainConfig.RitualEnchants.get() || !enchant.compatibleEnchant(activation)) return false;
        }
        return true;
    }

    private static List<ItemEntity> ownedEntities(PackagedMachineOperation operation) {
        return operation.level().getEntitiesOfClass(ItemEntity.class, new AABB(operation.position()).inflate(16),
                entity -> PackagedEntityCapture.ownedBy(entity, operation.id()));
    }

    private static RitualRecipe recipe(PackagedMachineOperation operation) {
        var holder = operation.level().getRecipeManager().byKey(operation.recipeId());
        if (holder.isEmpty() || !(holder.get().value() instanceof RitualRecipe recipe)) {
            throw new IllegalStateException("Goety ritual recipe changed after preparation");
        }
        return recipe;
    }

    private static ObjectList<PedestalBlockEntity> emptyPedestals(RitualRecipe recipe, ServerLevel level, BlockPos position) {
        var result = new ObjectArrayList<PedestalBlockEntity>();
        for (PedestalBlockEntity pedestal : recipe.getRitual().getPedestals(level, position)) {
            if (!level.isLoaded(pedestal.getBlockPos()) || !pedestal.itemStackHandler.getStackInSlot(0).isEmpty()) return ObjectList.of();
            result.add(pedestal);
        }
        return result;
    }

    private static boolean itemRitual(RitualRecipe recipe) {
        return recipe.getRitual() instanceof CraftItemRitual || recipe.getRitual() instanceof EnchantItemRitual || recipe.getRitual() instanceof LocateRitual;
    }

    private static boolean plausibleResult(ServerLevel level, RitualRecipe recipe, ItemStack activation, ItemStack result) {
        if (recipe.getRitual() instanceof EnchantItemRitual) return result.is(activation.getItem()) || activation.getItem() instanceof BookItem;
        ItemStack declared = recipe.getResultItem(level.registryAccess());
        return declared.isEmpty() || result.is(declared.getItem());
    }

    private static ItemStack declaredPrimary(IPatternDetails pattern) {
        if (pattern.getOutputs().isEmpty()) return ItemStack.EMPTY;
        GenericStack output = pattern.getOutputs().getFirst();
        if (!(output.what() instanceof AEItemKey key) || output.amount() <= 0 || output.amount() > Integer.MAX_VALUE) return ItemStack.EMPTY;
        return key.toStack((int) output.amount());
    }

    private static ItemStack take(KeyCounter[] inputs, Ingredient ingredient) {
        for (var counter : inputs) for (var entry : counter) if (entry.getKey() instanceof AEItemKey key && entry.getLongValue() > 0 && ingredient.test(key.toStack())) return key.toStack();
        return ItemStack.EMPTY;
    }

    private static ItemStack take(KeyCounter counters, Ingredient ingredient) {
        for (var entry : counters) if (entry.getKey() instanceof AEItemKey key && entry.getLongValue() > 0 && ingredient.test(key.toStack())) {
            counters.remove(entry.getKey(), 1);
            return key.toStack();
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasRemaining(KeyCounter counters) {
        for (var entry : counters) if (entry.getLongValue() != 0) return true;
        return false;
    }

    private static GameProfile profile(UUID operation) {
        return new GameProfile(UUID.nameUUIDFromBytes(("goety:" + operation).getBytes(StandardCharsets.UTF_8)), "[DE Goety]");
    }

    private static ItemStack read(PackagedMachineOperation operation, String key) {
        return ItemStack.parse(operation.level().registryAccess(), operation.progress().getCompound(key)).orElseThrow();
    }
}
