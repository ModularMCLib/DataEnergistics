package com.fish_dan_.data_energistics.integration.magic.malum.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRuleAdapter;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;
import com.fish_dan_.data_energistics.common.crafting.packaged.reusable.PackagedReusableState;
import com.fish_dan_.data_energistics.common.crafting.pattern.EncodedPatternRecipeReference;

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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;

import com.sammy.malum.common.block.curiosities.artifice.spirit_crucible.SpiritCrucibleCoreBlockEntity;
import com.sammy.malum.common.block.curiosities.sorcery.spirit_altar.AltarCraftingHelper;
import com.sammy.malum.common.block.curiosities.sorcery.spirit_altar.SpiritAltarBlockEntity;
import com.sammy.malum.common.block.storage.IMalumSpecialItemAccessPoint;
import com.sammy.malum.common.item.impetus.ImpetusItem;
import com.sammy.malum.common.recipe.SpiritFocusingRecipe;
import com.sammy.malum.common.recipe.SpiritInfusionRecipe;
import com.sammy.malum.core.systems.recipe.SpiritBasedRecipeInput;
import com.sammy.malum.registry.common.MalumContent;
import com.sammy.malum.registry.common.recipe.MalumRecipeTypes;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Optional;

/** Real altar/crucible inventory automation; all spirit consumption, damage and bonus outputs remain native. */
public final class MalumMachineAdapter implements PackagedMachineAdapter, ReusableInputRuleAdapter {

    private final MalumMachineKind kind;

    public MalumMachineAdapter(MalumMachineKind kind) {
        this.kind = kind;
    }

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("malum_" + this.kind.type.getPath());
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(this.kind.type);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && this.kind.accepts(level.getBlockEntity(position));
    }

    @Override
    public boolean supportsReusableInputs() {
        return this.kind == MalumMachineKind.CRUCIBLE;
    }

    @Override
    public boolean supportsReusableRecipe(ServerLevel level, BlockPos position, ResourceLocation recipeId) {
        if (!supportsReusableInputs() || !(recipe(level, recipeId) instanceof SpiritFocusingRecipe focusing)) return false;
        var layout = layout(level, position);
        if (layout == null) return false;
        var attributes = ((SpiritCrucibleCoreBlockEntity) layout.tile()).attributes;
        // Above 1, the native recipe can exceed twice its base wear.
        return focusing.getDurabilityCost() == 0 || attributes.instability.getValue(attributes) <= 1.0f;
    }

    @Override
    public boolean mayMatch(IPatternDetails pattern, Optional<ResourceLocation> recipeId) {
        return supportsReusableInputs() && recipeId.isPresent() && pattern.getOutputs().size() == 1 &&
                this.kind.type.equals(EncodedPatternRecipeReference.getProcessingRecipeType(pattern.getDefinition().getReadOnlyStack()));
    }

    @Override
    public Optional<ReusableInputRule> resolve(ReusableInputContext context) {
        if (context.target().providerScoped() || context.machineMode().filter(PackagedReusableState.MODE::equals).isEmpty() ||
                !mayMatch(context.pattern(), context.recipeId()) || context.actualInput().amount() != 1 ||
                !(context.actualInput().what() instanceof AEItemKey key) || !(key.getItem() instanceof ImpetusItem))
            return Optional.empty();
        var level = context.level();
        if (!(recipe(level, context.recipeId().orElseThrow()) instanceof SpiritFocusingRecipe focusing) ||
                !focusing.getInput().isSimple() || !focusing.getInput().test(key.getReadOnlyStack()))
            return Optional.empty();

        var supplied = new KeyCounter();
        for (var input : context.exactInputsFast()) supplied.add(input.what(), input.amount());
        if (supplied.get(key) != 1) return Optional.empty();
        var plan = MalumRecipePlan.prepare(level, focusing, context.pattern(), new KeyCounter[] { supplied });
        if (plan == null || plan.cycles() != 1 || !key.equals(AEItemKey.of(plan.main())) ||
                !selectedRecipe(level, focusing, plan.main(), plan.spirits(), plan.extras()))
            return Optional.empty();

        var stack = plan.main();
        int cost = focusing.getDurabilityCost();
        if (cost == 0 || !stack.isDamageableItem()) return Optional.of(ReusableInputRule.unchanged(id(), 1L, key));
        if (cost < 0 || cost > Integer.MAX_VALUE / 2 || stack.getDamageValue() >= stack.getMaxDamage()) return Optional.empty();
        return Optional.of(new ReusableInputRule(id(), 1L, ReusableInputRule.Kind.BOUNDED_DAMAGE,
                key, cost * 2, stack.getMaxDamage(), ObjectList.of(), ObjectList.of()));
    }

    @Override
    public LongList occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var result = new LongArrayList();
        result.add(position.asLong());
        var extras = preparation.getList("extras", Tag.TAG_COMPOUND);
        for (int index = 0; index < extras.size(); index++) result.add(extras.getCompound(index).getLong("position"));
        if (this.kind == MalumMachineKind.CRUCIBLE) result.add(position.above().asLong());
        return result;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        Layout layout = layout(level, position);
        if (layout == null || !layout.ready(this.kind)) return null;
        Recipe<?> recipe = recipe(level, recipeId);
        if (recipe == null) return null;
        var plan = MalumRecipePlan.prepare(level, recipe, pattern, inputs);
        if (plan == null || plan.spirits().size() > layout.spirits().getSlots() || plan.extras().size() > layout.pedestals().size()) return null;
        if (!layout.main().insertItem(0, plan.main(), true).isEmpty()) return null;
        var spiritEntries = new ListTag();
        for (int index = 0; index < plan.spirits().size(); index++) {
            ItemStack stack = plan.spirits().get(index);
            if (!layout.spirits().insertItem(index, stack, true).isEmpty()) return null;
            spiritEntries.add(stack.save(level.registryAccess()));
        }
        var extras = new ListTag();
        for (int index = 0; index < plan.extras().size(); index++) {
            var target = layout.pedestals().get(index);
            ItemStack stack = plan.extras().get(index);
            if (!target.getSuppliedInventory().insertItem(0, stack, true).isEmpty()) return null;
            var entry = new CompoundTag();
            entry.putLong("position", target.getAccessPointBlockPos().asLong());
            entry.put("stack", stack.save(level.registryAccess()));
            extras.add(entry);
        }
        if (!selectedRecipe(level, recipe, plan.main(), plan.spirits(), plan.extras())) return null;
        var result = new CompoundTag();
        result.put("main", plan.main().save(level.registryAccess()));
        result.put("spirits", spiritEntries);
        result.put("extras", extras);
        result.put("output", plan.output().save(level.registryAccess()));
        result.putLong("cycles", plan.cycles());
        return result;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        Layout layout = layout(operation.level(), operation.position());
        if (layout == null) return false;
        var progress = operation.progress();
        if (progress.getLong("cycles") <= 0) throw new IllegalArgumentException("Invalid Malum cycle count");
        var output = read(operation, progress.getCompound("output"));
        var extras = progress.getList("extras", Tag.TAG_COMPOUND);
        if (progress.getBoolean("delivered")) return collect(operation, layout, extras, output);
        if (!layout.ready(this.kind) && !continuingCrucible(layout, progress)) return false;
        var main = read(operation, progress.getCompound("main"));
        var spiritTags = progress.getList("spirits", Tag.TAG_COMPOUND);
        var spirits = new ObjectArrayList<ItemStack>();
        var extraStacks = new ObjectArrayList<ItemStack>();
        var targets = new ObjectArrayList<IMalumSpecialItemAccessPoint>();
        var required = new KeyCounter();
        if (!progress.getBoolean("installed")) required.add(AEItemKey.of(main), main.getCount());
        for (int index = 0; index < spiritTags.size(); index++) {
            ItemStack stack = read(operation, spiritTags.getCompound(index));
            spirits.add(stack);
            if (index >= layout.spirits().getSlots() || !layout.spirits().insertItem(index, stack, true).isEmpty()) return false;
            required.add(AEItemKey.of(stack), stack.getCount());
        }
        for (int index = 0; index < extras.size(); index++) {
            var encoded = extras.getCompound(index);
            BlockPos position = BlockPos.of(encoded.getLong("position"));
            var found = layout.pedestals().stream().filter(target -> target.getAccessPointBlockPos().equals(position)).findFirst();
            if (found.isEmpty() || targets.contains(found.get())) return false;
            ItemStack stack = read(operation, encoded.getCompound("stack"));
            if (!found.get().getSuppliedInventory().insertItem(0, stack, true).isEmpty()) return false;
            targets.add(found.get());
            extraStacks.add(stack);
            required.add(AEItemKey.of(stack), stack.getCount());
        }
        for (var entry : required) if (operation.available(entry.getKey()).compareTo(BigInteger.valueOf(entry.getLongValue())) < 0) throw new IllegalStateException("Missing owned Malum ingredient");
        var recipe = recipe(operation.level(), operation.recipeId());
        ItemStack actualMain = progress.getBoolean("installed") ? layout.main().getStackInSlot(0) : main;
        if (recipe == null || !selectedRecipe(operation.level(), recipe, actualMain, spirits, extraStacks)) return false;
        ItemStack actualOutput = recipe instanceof SpiritInfusionRecipe infusion ? infusion.getOutput(operation.level(), actualMain.copy()) : ((SpiritFocusingRecipe) recipe).createOutput();
        if (!PackagedOutputMatching.matches(operation, output, actualOutput)) throw new IllegalStateException("Malum base output changed");
        for (int index = 0; index < targets.size(); index++) insert(operation, targets.get(index).getSuppliedInventory(), 0, extraStacks.get(index));
        for (int index = 0; index < spirits.size(); index++) insert(operation, layout.spirits(), index, spirits.get(index));
        if (!progress.getBoolean("installed")) {
            insert(operation, layout.main(), 0, main);
            progress.putBoolean("installed", true);
        }
        if (layout.tile() instanceof SpiritAltarBlockEntity altar && altar.recipe != recipe) throw new IllegalStateException("Malum altar selected another recipe");
        if (layout.tile() instanceof SpiritCrucibleCoreBlockEntity crucible && crucible.recipe != null && crucible.recipe != recipe)
            throw new IllegalStateException("Malum crucible selected another recipe");
        progress.putBoolean("delivered", true);
        operation.changed();
        return true;
    }

    private boolean collect(PackagedMachineOperation operation, Layout layout, ListTag extras, ItemStack output) {
        Vec3 nativeDropPosition = layout.tile() instanceof SpiritAltarBlockEntity altar ? altar.inventory.getDisplayData().getDisplayCenter(0) : operation.position().getCenter();
        // Malum gives the altar drop an ordinary ItemEntity with its own motion. It may move away from
        // the inventory display center before the next packaged tick, so use the whole native work area for owned
        // drops.
        // The fallback output-key match remains restricted to the altar and is claimed immediately below.
        var drops = operation.level().getEntitiesOfClass(ItemEntity.class, new AABB(nativeDropPosition, nativeDropPosition).inflate(8),
                entity -> PackagedEntityCapture.ownedBy(entity, operation.id()) ||
                        layout.tile() instanceof SpiritAltarBlockEntity &&
                                PackagedOutputMatching.sameKey(operation, output, entity.getItem()));
        long baseCount = 0;
        for (var drop : drops) if (PackagedOutputMatching.sameKey(operation, output, drop.getItem())) baseCount += drop.getItem().getCount();
        if (baseCount < output.getCount()) return false;
        if (this.kind == MalumMachineKind.CRUCIBLE) {
            var remaining = layout.main().getStackInSlot(0);
            if (!remaining.isEmpty()) {
                var recovered = layout.main().extractItem(0, remaining.getCount(), false);
                if (recovered.isEmpty()) return false;
                var key = AEItemKey.of(recovered);
                operation.returned(key, recovered.getCount());
                if (operation.progress().getLong("cycles") == 1) {
                    operation.progress().put(PackagedMachineOperation.RETAINED_TOOL_TAG,
                            GenericStack.writeTag(operation.level().registryAccess(), new GenericStack(key, recovered.getCount())));
                }
            }
        }
        if (layout.tile() instanceof SpiritAltarBlockEntity altar) {
            // Malum can leave an inventory cache non-empty until its next native tick. The owned
            // output proves that this craft completed, so return residual assets instead of waiting
            // for a save/reload cycle to refresh those caches.
            drain(operation, altar.inventory);
            drain(operation, altar.spiritInventory);
            drain(operation, altar.extrasInventory);
            for (int index = 0; index < extras.size(); index++) {
                var position = BlockPos.of(extras.getCompound(index).getLong("position"));
                var found = layout.pedestals().stream()
                        .filter(target -> target.getAccessPointBlockPos().equals(position)).findFirst();
                if (found.isPresent()) drain(operation, found.orElseThrow().getSuppliedInventory());
            }
        }
        // Luck/augment bonuses are actual owned drops, never promised by the static pattern or discarded.
        for (var drop : drops) {
            if (!PackagedEntityCapture.ownedBy(drop, operation.id())) PackagedEntityCapture.claim(drop, operation.id());
            ItemStack stack = drop.getItem().copy();
            drop.discard();
            operation.returned(AEItemKey.of(stack), stack.getCount());
        }
        if (layout.tile() instanceof SpiritAltarBlockEntity altar) resetAltarState(altar);
        long cycles = operation.progress().getLong("cycles") - 1;
        operation.progress().putLong("cycles", cycles);
        operation.progress().putBoolean("installed", false);
        operation.progress().putBoolean("delivered", false);
        operation.changed();
        if (cycles == 0) operation.complete();
        return true;
    }

    private static void resetAltarState(SpiritAltarBlockEntity altar) {
        altar.recipe = null;
        altar.possibleRecipes.clear();
        altar.isCrafting = false;
        altar.progress = 0;
        altar.idleProgress = 0;
        altar.setChanged();
    }

    private static void drain(PackagedMachineOperation operation, IItemHandler inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack present = inventory.getStackInSlot(slot);
            if (present.isEmpty()) continue;
            ItemStack remaining = inventory.extractItem(slot, present.getCount(), false);
            if (!remaining.isEmpty()) operation.returned(AEItemKey.of(remaining), remaining.getCount());
        }
    }

    private boolean continuingCrucible(Layout layout, CompoundTag progress) {
        if (!progress.getBoolean("installed") || !(layout.tile() instanceof SpiritCrucibleCoreBlockEntity crucible)) return false;
        return !Layout.empty(layout.main()) && Layout.empty(layout.spirits()) && crucible.recipe == null && !crucible.isCrafting;
    }

    private static void insert(PackagedMachineOperation operation, IItemHandler inventory, int slot, ItemStack stack) {
        var rejected = inventory.insertItem(slot, stack.copy(), false);
        int delivered = stack.getCount() - rejected.getCount();
        if (delivered > 0) operation.delivered(AEItemKey.of(stack), delivered);
        if (!rejected.isEmpty()) throw new IllegalStateException("Malum inventory refused a simulated insertion");
    }

    private @Nullable Recipe<?> recipe(ServerLevel level, ResourceLocation id) {
        var holder = level.getRecipeManager().byKey(id);
        if (holder.isEmpty()) return null;
        Recipe<?> recipe = holder.get().value();
        if (this.kind == MalumMachineKind.ALTAR && recipe instanceof SpiritInfusionRecipe && recipe.getType() == MalumRecipeTypes.SPIRIT_INFUSION.get()) return recipe;
        if (this.kind == MalumMachineKind.CRUCIBLE && recipe instanceof SpiritFocusingRecipe && recipe.getType() == MalumRecipeTypes.SPIRIT_FOCUSING.get()) return recipe;
        return null;
    }

    private static boolean selectedRecipe(ServerLevel level, Recipe<?> recipe, ItemStack main,
                                          ObjectList<ItemStack> spirits, ObjectList<ItemStack> extras) {
        var input = new SpiritBasedRecipeInput(main.copy(), spirits);
        if (recipe instanceof SpiritFocusingRecipe focusing) {
            var selected = level.getRecipeManager().getRecipeFor(MalumRecipeTypes.SPIRIT_FOCUSING.get(), input, level);
            return selected.isPresent() && selected.get().value() == focusing;
        }
        var infusion = (SpiritInfusionRecipe) recipe;
        if (!infusion.matches(input, level)) return false;
        var spiritInventory = frozen(spirits);
        var extraInventory = frozen(extras);
        var consumed = new ItemStackHandler(8);
        var rank = AltarCraftingHelper.rankRecipe(infusion, main, spiritInventory, extraInventory, consumed);
        if (rank == null) return false;
        for (var candidate : level.getRecipeManager().getAllRecipesFor(MalumRecipeTypes.SPIRIT_INFUSION.get())) {
            if (candidate.value() == recipe || !candidate.value().matches(input, level)) continue;
            var alternative = AltarCraftingHelper.rankRecipe(candidate.value(), main, spiritInventory, extraInventory, consumed);
            if (alternative != null && alternative.compareTo(rank) >= 0) return false;
        }
        return true;
    }

    private static IItemHandlerModifiable frozen(ObjectList<ItemStack> stacks) {
        var inventory = new ItemStackHandler(stacks.size());
        for (int index = 0; index < stacks.size(); index++) inventory.setStackInSlot(index, stacks.get(index).copy());
        return inventory;
    }

    private @Nullable Layout layout(ServerLevel level, BlockPos position) {
        if (!recognizes(level, position)) return null;
        for (int x = (position.getX() - 4) >> 4; x <= (position.getX() + 4) >> 4; x++) {
            for (int z = (position.getZ() - 4) >> 4; z <= (position.getZ() + 4) >> 4; z++) if (!level.hasChunk(x, z)) return null;
        }
        BlockEntity tile = level.getBlockEntity(position);
        if (tile instanceof SpiritAltarBlockEntity altar) {
            return new Layout(altar, altar.inventory, altar.spiritInventory,
                    new ObjectArrayList<>(AltarCraftingHelper.capturePedestals(level, position)));
        }
        var crucible = (SpiritCrucibleCoreBlockEntity) tile;
        if (!level.getBlockState(position.above()).is(MalumContent.Focusing.SPIRIT_CRUCIBLE_COMPONENT.get())) return null;
        for (BlockPos modifier : crucible.attributes.modifierPositions) if (!level.isLoaded(modifier)) return null;
        return new Layout(crucible, crucible.inventory, crucible.spiritInventory, ObjectList.of());
    }

    private static ItemStack read(PackagedMachineOperation operation, CompoundTag tag) {
        return ItemStack.parse(operation.level().registryAccess(), tag).orElseThrow(() -> new IllegalArgumentException("Invalid Malum persisted item"));
    }

    private record Layout(BlockEntity tile, IItemHandler main, IItemHandler spirits,
                          ObjectList<IMalumSpecialItemAccessPoint> pedestals) {

        boolean ready(MalumMachineKind kind) {
            if (!empty(this.spirits)) return false;
            if (kind == MalumMachineKind.ALTAR) return empty(this.main) && empty(((SpiritAltarBlockEntity) this.tile).extrasInventory) &&
                    this.pedestals.stream().allMatch(pedestal -> empty(pedestal.getSuppliedInventory()));
            var crucible = (SpiritCrucibleCoreBlockEntity) this.tile;
            return empty(this.main) && crucible.recipe == null && !crucible.isCrafting;
        }

        private static boolean empty(IItemHandler inventory) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                if (!inventory.getStackInSlot(slot).isEmpty()) return false;
            }
            return true;
        }
    }
}
