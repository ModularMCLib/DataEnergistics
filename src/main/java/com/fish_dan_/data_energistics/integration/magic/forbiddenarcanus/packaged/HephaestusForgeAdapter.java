package com.fish_dan_.data_energistics.integration.magic.forbiddenarcanus.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;
import com.fish_dan_.data_energistics.mixin.magic.forbiddenarcanus.RitualManagerStateAccessor;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import com.mojang.authlib.GameProfile;
import com.stal111.forbidden_arcanus.common.block.HephaestusForgeBlock;
import com.stal111.forbidden_arcanus.common.block.entity.PedestalBlockEntity;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ForgeDataCache;
import com.stal111.forbidden_arcanus.common.block.entity.forge.HephaestusForgeBlockEntity;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceModifier;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.Ritual;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualInput;
import com.stal111.forbidden_arcanus.common.block.pedestal.effect.PedestalEffectTrigger;
import com.stal111.forbidden_arcanus.common.item.enhancer.EnhancerTarget;
import com.stal111.forbidden_arcanus.core.init.other.ModPOITypes;
import com.stal111.forbidden_arcanus.core.registry.FARegistries;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

/** Feeds the actual forge tier and leaves essence consumption and processing to its native ritual. */
public final class HephaestusForgeAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", "hephaestus_forge");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("forbidden_arcanus_hephaestus_forge");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE, ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", "hephaestus_smithing"));
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof HephaestusForgeBlockEntity && level.getBlockState(position).getBlock() instanceof HephaestusForgeBlock;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, position) || !(level.getBlockEntity(position) instanceof HephaestusForgeBlockEntity forge)) return null;
        var holder = level.registryAccess().lookupOrThrow(FARegistries.RITUAL).get(ResourceKey.create(FARegistries.RITUAL, recipeId));
        if (holder.isEmpty()) return null;
        Ritual ritual = holder.get().value();
        int tier = ((HephaestusForgeBlock) forge.getBlockState().getBlock()).getLevel().getAsInt();
        if (!ritual.requirements().tier().test(tier)) return null;
        ItemStack main = findMain(inputs, ritual.mainIngredient());
        if (main.isEmpty() || !forge.getStack(HephaestusForgeBlockEntity.MAIN_SLOT).isEmpty() || forge.getRitualManager().isRitualActive()) return null;
        var remaining = new KeyCounter();
        for (var counter : inputs) for (var entry : counter) remaining.add(entry.getKey(), entry.getLongValue());
        remaining.remove(AEItemKey.of(main), main.getCount());
        ListTag ingredients = new ListTag();
        var stacks = new ArrayList<ItemStack>();
        for (RitualInput input : ritual.inputs()) {
            for (int count = 0; count < input.amount(); count++) {
                ItemStack stack = take(remaining, input.ingredient());
                if (stack.isEmpty()) return null;
                stacks.add(stack);
                var entry = new CompoundTag();
                entry.put("stack", stack.saveOptional(level.registryAccess()));
                ingredients.add(entry);
            }
        }
        if (hasRemaining(remaining) || !ritual.checkIngredients(stacks, main)) return null;
        ObjectList<BlockPos> pedestals = pedestalPositions(level, position);
        if (pedestals.size() < stacks.size()) return null;
        for (int index = 0; index < stacks.size(); index++) {
            if (!(level.getBlockEntity(pedestals.get(index)) instanceof PedestalBlockEntity pedestal) || pedestal.hasStack()) return null;
            ingredients.getCompound(index).putLong("position", pedestals.get(index).asLong());
        }
        ItemStack result = ritual.result().getResultItem(main);
        if (result.isEmpty() || pattern.getOutputs().size() != 1 || !PackagedOutputMatching.matches(pattern, result, result.getCount())) return null;
        var progress = new CompoundTag();
        progress.put("main", main.save(level.registryAccess()));
        progress.put("ingredients", ingredients);
        progress.put("result", result.save(level.registryAccess()));
        return progress;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var result = new ObjectArrayList<BlockPos>();
        result.add(position);
        ListTag list = preparation.getList("ingredients", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) result.add(BlockPos.of(list.getCompound(index).getLong("position")));
        return result;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!recognizes(operation.level(), operation.position()) || !(operation.level().getBlockEntity(operation.position()) instanceof HephaestusForgeBlockEntity forge)) return false;
        CompoundTag progress = operation.progress();
        ItemStack expected = read(operation, "result");
        if (!progress.getBoolean("started")) {
            ItemStack main = read(operation, "main");
            ListTag list = progress.getList("ingredients", Tag.TAG_COMPOUND);
            var required = new KeyCounter();
            required.add(AEItemKey.of(main), main.getCount());
            for (int index = 0; index < list.size(); index++) {
                ItemStack ingredient = ItemStack.parseOptional(operation.level().registryAccess(), list.getCompound(index).getCompound("stack"));
                required.add(AEItemKey.of(ingredient), ingredient.getCount());
                BlockPos pedestalPosition = BlockPos.of(list.getCompound(index).getLong("position"));
                var blockEntity = operation.level().getBlockEntity(pedestalPosition);
                if (!(blockEntity instanceof PedestalBlockEntity pedestal) || pedestal.hasStack()) return false;
            }
            for (var entry : required) {
                if (operation.available(entry.getKey()).compareTo(BigInteger.valueOf(entry.getLongValue())) < 0) return false;
            }
            if (!forge.getStack(HephaestusForgeBlockEntity.MAIN_SLOT).isEmpty() || forge.getRitualManager().isRitualActive()) return false;
            if (!canStart(operation, forge, main, list)) return false;
            try {
                forge.setStack(HephaestusForgeBlockEntity.MAIN_SLOT, main.copy());
            } finally {
                if (ItemStack.matches(main, forge.getStack(HephaestusForgeBlockEntity.MAIN_SLOT))) {
                    operation.delivered(AEItemKey.of(main), main.getCount());
                    progress.putBoolean("main_delivered", true);
                    operation.changed();
                }
            }
            for (int index = 0; index < list.size(); index++) {
                ItemStack ingredient = ItemStack.parseOptional(operation.level().registryAccess(), list.getCompound(index).getCompound("stack"));
                BlockPos pedestalPosition = BlockPos.of(list.getCompound(index).getLong("position"));
                PedestalBlockEntity pedestal = (PedestalBlockEntity) operation.level().getBlockEntity(pedestalPosition);
                try {
                    pedestal.setStack(ingredient.copy(), null, PedestalEffectTrigger.PLAYER_PLACE_ITEM);
                } finally {
                    if (ItemStack.matches(ingredient, pedestal.getStack())) {
                        operation.delivered(AEItemKey.of(ingredient), ingredient.getCount());
                        progress.putInt("ingredients_delivered", index + 1);
                        operation.changed();
                    }
                }
                forge.updatePedestalStack(pedestalPosition, ingredient.copy());
            }
            ResourceKey<Ritual> ritualKey = ResourceKey.create(FARegistries.RITUAL, operation.recipeId());
            var valid = forge.getRitualManager().getValidRitual();
            var player = FakePlayerFactory.get(operation.level(), profile(operation.id()));
            player.setPos(operation.position().getX() + 0.5, operation.position().getY() + 1, operation.position().getZ() + 0.5);
            if (valid.isEmpty() || !valid.get().is(ritualKey) || !forge.getRitualManager().startRitual(player, forge.getEssenceManager().getStorage())) {
                throw new IllegalStateException("Hephaestus Forge changed after native resource preflight; physical inputs remain tracked");
            }
            progress.putBoolean("started", true);
            operation.changed();
            return true;
        }
        ItemStack actual = forge.getStack(HephaestusForgeBlockEntity.MAIN_SLOT);
        if (forge.getRitualManager().isRitualActive()) return false;
        if (!PackagedOutputMatching.matches(operation, expected, actual)) {
            if (!actual.isEmpty()) throw new IllegalStateException("Hephaestus Forge completed with an unexpected result");
            return false;
        }
        ItemStack extracted = forge.getItemStackHandler().extractItem(HephaestusForgeBlockEntity.MAIN_SLOT, actual.getCount(), false);
        operation.returned(AEItemKey.of(extracted), extracted.getCount());
        operation.complete();
        return true;
    }

    private static boolean canStart(PackagedMachineOperation operation, HephaestusForgeBlockEntity forge,
                                    ItemStack main, ListTag ingredients) {
        var nativeCache = ((RitualManagerStateAccessor) forge.getRitualManager()).dataEnergistics$dataCache();
        var cache = new ForgeDataCache(new ArrayList<>(nativeCache.cachedIngredients()), main, nativeCache.enhancers());
        for (int index = 0; index < ingredients.size(); index++) {
            CompoundTag entry = ingredients.getCompound(index);
            cache.setIngredient(BlockPos.of(entry.getLong("position")),
                    ItemStack.parseOptional(operation.level().registryAccess(), entry.getCompound("stack")));
        }
        var modifiers = cache.getEnhancers().stream()
                .flatMap(enhancer -> enhancer.value().getEffects(EnhancerTarget.HEPHAESTUS_FORGE))
                .filter(effect -> effect instanceof EssenceModifier).map(effect -> (EssenceModifier) effect).toList();
        int tier = ((HephaestusForgeBlock) forge.getBlockState().getBlock()).getLevel().getAsInt();
        for (var holder : operation.level().registryAccess().lookupOrThrow(FARegistries.RITUAL).listElements().toList()) {
            Ritual ritual = holder.value();
            if (forge.getEssences().hasMoreThan(ritual.requirements().essences().applyModifiers(modifiers)) && ritual.canStart(cache, tier)) {
                return holder.is(ResourceKey.create(FARegistries.RITUAL, operation.recipeId()));
            }
        }
        return false;
    }

    @Override
    public boolean recoverRemoved(PackagedMachineOperation operation) {
        for (BlockPos position : occupiedPositions(operation.level(), operation.position(), operation.progress())) {
            if (!operation.level().isLoaded(position)) return false;
        }
        CompoundTag progress = operation.progress();
        if (operation.level().getBlockEntity(operation.position()) instanceof HephaestusForgeBlockEntity forge) {
            ((RitualManagerStateAccessor) forge.getRitualManager()).dataEnergistics$setActiveRitual(null, null);
            forge.getMagicCircleController().removeMagicCircle(operation.level(), operation.position());
            if (progress.getBoolean("started") || progress.getBoolean("main_delivered")) {
                ItemStack stack = forge.getItemStackHandler().extractItem(HephaestusForgeBlockEntity.MAIN_SLOT,
                        forge.getStack(HephaestusForgeBlockEntity.MAIN_SLOT).getCount(), false);
                if (!stack.isEmpty()) operation.returned(AEItemKey.of(stack), stack.getCount());
            }
            forge.setChanged();
        }
        ListTag ingredients = progress.getList("ingredients", Tag.TAG_COMPOUND);
        int count = progress.getBoolean("started") ? ingredients.size() : progress.getInt("ingredients_delivered");
        for (int index = 0; index < count; index++) {
            BlockPos position = BlockPos.of(ingredients.getCompound(index).getLong("position"));
            if (operation.level().getBlockEntity(position) instanceof PedestalBlockEntity pedestal) {
                ItemStack stack = pedestal.getStack().copy();
                pedestal.clearStack(null, PedestalEffectTrigger.PLAYER_REMOVE_ITEM);
                if (!stack.isEmpty()) operation.returned(AEItemKey.of(stack), stack.getCount());
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

    private static ObjectList<BlockPos> pedestalPositions(ServerLevel level, BlockPos center) {
        var positions = new ObjectArrayList<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -4, -4), center.offset(4, 4, 4))) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof PedestalBlockEntity)) continue;
            var nativeForge = level.getPoiManager().getInRange(holder -> holder.value() == ModPOITypes.HEPHAESTUS_FORGE.get(),
                    pos, 4, PoiManager.Occupancy.ANY).map(PoiRecord::getPos).findFirst();
            if (nativeForge.isPresent() && nativeForge.get().equals(center)) positions.add(pos.immutable());
        }
        return positions;
    }

    private static ItemStack findMain(KeyCounter[] inputs, Ingredient ingredient) {
        for (var counter : inputs) for (var entry : counter) if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key && ingredient.test(key.toStack())) return key.toStack();
        return ItemStack.EMPTY;
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

    private static GameProfile profile(UUID operation) {
        return new GameProfile(UUID.nameUUIDFromBytes(("hephaestus:" + operation).getBytes(StandardCharsets.UTF_8)), "[DE Hephaestus]");
    }

    private static ItemStack read(PackagedMachineOperation operation, String key) {
        return ItemStack.parse(operation.level().registryAccess(), operation.progress().getCompound(key)).orElseThrow();
    }
}
