package com.fish_dan_.data_energistics.integration.crafting.packaged.draconicevolution;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.brandonscore.utils.FacingUtils;
import com.brandon3055.brandonscore.utils.Utils;
import com.brandon3055.draconicevolution.DEConfig;
import com.brandon3055.draconicevolution.api.DraconicAPI;
import com.brandon3055.draconicevolution.api.crafting.IFusionRecipe;
import com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingCore;
import com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingInjector;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/** Drives the real DE core and injectors while retaining exact ownership of every physical stack. */
final class DraconicFusionAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(
            "draconicevolution", "fusion_crafting");
    private static final String PHASE = "phase";
    private static final int READY = 0;
    private static final int RUNNING = 1;

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("draconic_evolution_fusion_crafting");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof TileFusionCraftingCore;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        Layout layout = discover(level, position);
        if (layout == null || layout.core().isCrafting() || !layout.empty()) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof IFusionRecipe recipe) ||
                recipe.getType() != DraconicAPI.FUSION_RECIPE_TYPE.get())
            return null;
        int requiredInjectors = recipe.fusionIngredients().size();
        var eligible = new ObjectArrayList<TileFusionCraftingInjector>();
        for (var injector : layout.injectors()) {
            if (injector.getInjectorTier().index >= recipe.getRecipeTier().index) eligible.add(injector);
        }
        if (eligible.size() < requiredInjectors) return null;
        eligible.sort(Comparator.comparingLong(injector -> injector.getBlockPos().asLong()));
        while (eligible.size() > requiredInjectors) eligible.removeLast();
        TechLevel minimumTier = eligible.stream()
                .map(TileFusionCraftingInjector::getInjectorTier)
                .min(Comparator.comparingInt(tier -> tier.index))
                .orElse(TechLevel.DRACONIUM);
        FusionRecipePlan.Plan plan;
        try {
            plan = FusionRecipePlan.prepare(level, recipe, minimumTier, pattern, inputs);
            if (plan == null || plan.injectors().size() != eligible.size()) {
                return null;
            }
            // Validate physical slot limits and the recipe the real core will select before accepting materials.
            var snapshot = FusionSnapshot.deliveredInventory(plan, minimumTier);
            var selected = level.getRecipeManager().getRecipeFor(DraconicAPI.FUSION_RECIPE_TYPE.get(), snapshot, level);
            if (selected.isEmpty() || !selected.get().id().equals(recipeId)) {
                return null;
            }
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return null;
        }
        CompoundTag progress = FusionRecipePlan.save(plan, level.registryAccess());
        progress.putInt(PHASE, READY);
        progress.put("selected", positions(eligible));
        progress.put("layout", positions(layout.injectors()));
        return progress;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var positions = new ObjectArrayList<BlockPos>();
        positions.add(position.immutable());
        ListTag selected = preparation.getList("selected", Tag.TAG_LONG);
        for (int index = 0; index < selected.size(); index++) {
            positions.add(BlockPos.of(((LongTag) selected.get(index)).getAsLong()));
        }
        return positions;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        Layout layout = discover(operation.level(), operation.position());
        if (layout == null) return false;
        if (!matchesPositions(operation.progress().getList("layout", Tag.TAG_LONG), layout.injectors())) return false;
        var selected = selected(operation, layout);
        if (selected == null) return false;
        FusionRecipePlan.Plan plan = FusionRecipePlan.load(operation);
        IFusionRecipe recipe = recipe(operation, plan);
        int phase = operation.progress().getInt(PHASE);
        if (phase == READY) return deliver(operation, layout, selected, plan, recipe);
        if (phase != RUNNING) throw new IllegalArgumentException("Unknown Draconic fusion operation phase");
        return collect(operation, layout, selected, plan, recipe);
    }

    private static boolean deliver(PackagedMachineOperation operation, Layout layout,
                                   ObjectList<TileFusionCraftingInjector> selected,
                                   FusionRecipePlan.Plan plan, IFusionRecipe recipe) {
        if (layout.core().isCrafting() || !layout.core().getOutputStack().isEmpty()) return false;
        CompoundTag progress = operation.progress();
        int deliveredInjectors = progress.getInt("delivered_injectors");
        boolean catalystDelivered = progress.getBoolean("catalyst_delivered");
        if (deliveredInjectors < 0 || deliveredInjectors > selected.size()) {
            throw new IllegalArgumentException("Invalid persisted Draconic injector delivery count");
        }
        if (deliveredInjectors == 0 && !catalystDelivered && !layout.core().updateInjectors()) return false;
        if (!matchesCoreInjectors(layout.core(), layout.injectors())) return false;
        for (var injector : layout.injectors()) {
            if (!selected.contains(injector) && !injector.getInjectorStack().isEmpty()) return false;
        }
        ItemStack catalyst = FusionRecipePlan.deliveredCatalyst(plan);
        var physical = new ObjectArrayList<ItemStack>();
        for (int index = 0; index < selected.size(); index++) {
            ItemStack stack = FusionRecipePlan.deliveredInjector(plan, plan.injectors().get(index));
            physical.add(stack);
            ItemStack actual = selected.get(index).getInjectorStack();
            if (index < deliveredInjectors) {
                if (!ItemStack.matches(stack, actual)) {
                    throw new IllegalStateException("Previously delivered Draconic injector input changed");
                }
            } else if (!actual.isEmpty()) {
                return false;
            }
        }
        ItemStack actualCatalyst = layout.core().getCatalystStack();
        if (catalystDelivered) {
            if (!ItemStack.matches(catalyst, actualCatalyst)) {
                throw new IllegalStateException("Previously delivered Draconic catalyst changed");
            }
        } else if (!actualCatalyst.isEmpty()) {
            return false;
        }
        for (int index = deliveredInjectors; index < selected.size(); index++) {
            ItemStack stack = physical.get(index);
            FusionRecipePlan.requireAvailable(operation, stack);
            selected.get(index).setInjectorStack(stack.copy());
            operation.delivered(AEItemKey.of(stack), stack.getCount());
            progress.putInt("delivered_injectors", index + 1);
            operation.changed();
        }
        if (!catalystDelivered) {
            FusionRecipePlan.requireAvailable(operation, catalyst);
            layout.core().setCatalystStack(catalyst.copy());
            operation.delivered(AEItemKey.of(catalyst), catalyst.getCount());
            progress.putBoolean("catalyst_delivered", true);
            operation.changed();
        }
        requireRecipe(operation.level(), layout.core(), operation.recipeId(), recipe, plan);
        progress.putInt(PHASE, RUNNING);
        operation.changed();
        start(operation, layout.core());
        return true;
    }

    private static boolean collect(PackagedMachineOperation operation, Layout layout,
                                   ObjectList<TileFusionCraftingInjector> selected,
                                   FusionRecipePlan.Plan plan, IFusionRecipe recipe) {
        if (layout.core().isCrafting()) {
            var active = layout.core().getActiveRecipe();
            if (active == null || !active.id().equals(operation.recipeId())) {
                throw new IllegalStateException("Draconic core switched to another fusion recipe");
            }
            return false;
        }
        ItemStack result = layout.core().getOutputStack();
        if (result.isEmpty()) {
            validateReadyCycle(layout, selected, plan);
            requireRecipe(operation.level(), layout.core(), operation.recipeId(), recipe, plan);
            start(operation, layout.core());
            return true;
        }
        if (!ItemStack.matches(result, plan.result())) {
            throw new IllegalStateException("Unexpected Draconic fusion output");
        }
        long cycles = plan.cycles();
        long nextCycles = cycles - 1;
        ItemStack expectedCatalyst = plan.catalyst().copy();
        expectedCatalyst.setCount(Math.toIntExact(Math.multiplyExact(expectedCatalyst.getCount(), nextCycles)));
        if (!ItemStack.matches(expectedCatalyst, layout.core().getCatalystStack())) {
            throw new IllegalStateException("Draconic fusion catalyst changed outside this operation");
        }
        var normalizedStacks = new ObjectArrayList<ItemStack>();
        var remainders = new Object2LongLinkedOpenHashMap<AEItemKey>();
        for (int index = 0; index < selected.size(); index++) {
            var ingredient = plan.injectors().get(index);
            ItemStack actual = selected.get(index).getInjectorStack();
            ItemStack normalized = normalizeIngredient(actual, ingredient, cycles);
            ItemStack expected = expectedAfterCycle(ingredient, nextCycles);
            if (!ItemStack.matches(expected, normalized)) {
                throw new IllegalStateException("Draconic fusion injector changed outside this operation");
            }
            if (!ingredient.retained() && !ingredient.remaining().isEmpty()) {
                long remainderAmount = Math.multiplyExact((long) ingredient.remaining().getCount(), ingredient.count());
                remainders.addTo(AEItemKey.of(ingredient.remaining()), remainderAmount);
            }
            normalizedStacks.add(normalized.copy());
        }
        for (var injector : layout.injectors()) {
            if (!selected.contains(injector) && !injector.getInjectorStack().isEmpty()) {
                throw new IllegalStateException("Unselected Draconic injector contains a foreign item");
            }
        }
        for (int index = 0; index < selected.size(); index++) {
            selected.get(index).setInjectorStack(normalizedStacks.get(index));
        }
        for (var entry : remainders.object2LongEntrySet()) {
            operation.returned(entry.getKey(), entry.getLongValue());
        }
        layout.core().setOutputStack(ItemStack.EMPTY);
        operation.returned(AEItemKey.of(result), result.getCount());
        operation.progress().putLong("cycles", nextCycles);
        operation.changed();
        if (nextCycles > 0) {
            requireRecipe(operation.level(), layout.core(), operation.recipeId(), recipe,
                    FusionRecipePlan.load(operation));
            start(operation, layout.core());
            return true;
        }
        for (var injector : selected) {
            ItemStack returned = injector.getInjectorStack().copy();
            injector.setInjectorStack(ItemStack.EMPTY);
            if (!returned.isEmpty()) operation.returned(AEItemKey.of(returned), returned.getCount());
        }
        if (!layout.core().getCatalystStack().isEmpty()) {
            throw new IllegalStateException("Completed Draconic fusion retained catalyst material");
        }
        operation.complete();
        return true;
    }

    private static void validateReadyCycle(Layout layout,
                                           ObjectList<TileFusionCraftingInjector> selected,
                                           FusionRecipePlan.Plan plan) {
        ItemStack catalyst = FusionRecipePlan.deliveredCatalyst(plan);
        if (!ItemStack.matches(catalyst, layout.core().getCatalystStack())) {
            throw new IllegalStateException("Draconic fusion catalyst changed before restart");
        }
        for (int index = 0; index < selected.size(); index++) {
            ItemStack expected = FusionRecipePlan.deliveredInjector(plan, plan.injectors().get(index));
            if (!ItemStack.matches(expected, selected.get(index).getInjectorStack())) {
                throw new IllegalStateException("Draconic fusion injector changed before restart");
            }
        }
        for (var injector : layout.injectors()) {
            if (!selected.contains(injector) && !injector.getInjectorStack().isEmpty()) {
                throw new IllegalStateException("Unselected Draconic injector contains a foreign item");
            }
        }
    }

    private static void start(PackagedMachineOperation operation, TileFusionCraftingCore core) {
        core.startCraft();
        var active = core.getActiveRecipe();
        if (!core.isCrafting() || active == null || !active.id().equals(operation.recipeId())) {
            throw new IllegalStateException("Draconic core refused the validated fusion craft");
        }
    }

    private static ItemStack expectedAfterCycle(FusionRecipePlan.PlannedIngredient ingredient, long cycles) {
        if (ingredient.retained()) return ingredient.input().copy();
        if (cycles == 0) return ItemStack.EMPTY;
        ItemStack stack = ingredient.input().copy();
        stack.setCount(Math.toIntExact(Math.multiplyExact(stack.getCount(), cycles)));
        return stack;
    }

    /**
     * DE consumes one physical item from an injector per cycle even when a custom
     * ingredient matches a larger stack. Its native remainder path replaces the
     * entire injector stack, so restore the remaining batch explicitly and return
     * one remainder for every item the custom ingredient consumed.
     */
    private static ItemStack normalizeIngredient(ItemStack actual,
                                                 FusionRecipePlan.PlannedIngredient ingredient,
                                                 long cycles) {
        if (ingredient.retained()) return actual;
        long expectedBefore = Math.multiplyExact(ingredient.input().getCount(), cycles);
        if (!ingredient.remaining().isEmpty()) {
            if (!ItemStack.matches(actual, ingredient.remaining())) {
                throw new IllegalStateException("Draconic core produced an unexpected fusion crafting remainder");
            }
            ItemStack next = ingredient.input().copy();
            next.setCount(Math.toIntExact(Math.multiplyExact(ingredient.input().getCount(), cycles - 1)));
            return next;
        }
        long consumedByNativeCore = expectedBefore - 1;
        if (actual.getCount() != consumedByNativeCore ||
                consumedByNativeCore > 0 && !ItemStack.isSameItemSameComponents(actual, ingredient.input())) {
            throw new IllegalStateException("Draconic core consumed an unexpected fusion ingredient amount");
        }
        if (consumedByNativeCore == 0) return ItemStack.EMPTY;
        ItemStack normalized = actual.copy();
        if (ingredient.count() > 1) normalized.shrink(ingredient.count() - 1);
        return normalized;
    }

    private static IFusionRecipe recipe(PackagedMachineOperation operation, FusionRecipePlan.Plan plan) {
        var holder = operation.level().getRecipeManager().byKey(operation.recipeId());
        if (holder.isEmpty() || !(holder.get().value() instanceof IFusionRecipe recipe) ||
                recipe.getType() != DraconicAPI.FUSION_RECIPE_TYPE.get()) {
            throw new IllegalStateException("Draconic fusion recipe disappeared");
        }
        if (recipe.getEnergyCost() != plan.energy() || !recipe.getRecipeTier().name().equals(plan.tier()) ||
                recipe.fusionIngredients().size() != plan.injectors().size()) {
            throw new IllegalStateException("Draconic fusion recipe changed after preparation");
        }
        return recipe;
    }

    private static void requireRecipe(ServerLevel level, TileFusionCraftingCore core, ResourceLocation recipeId,
                                      IFusionRecipe recipe, FusionRecipePlan.Plan plan) {
        if (!recipe.matches(core, level) || !recipe.canStartCraft(core, level, null) ||
                !ItemStack.matches(plan.result(), recipe.assemble(core, level.registryAccess()))) {
            throw new IllegalStateException("Draconic fusion recipe no longer matches its physical inventory");
        }
        var selected = level.getRecipeManager().getRecipeFor(DraconicAPI.FUSION_RECIPE_TYPE.get(), core, level);
        if (selected.isEmpty() || !selected.get().id().equals(recipeId)) {
            throw new IllegalStateException("Draconic core selected a different fusion recipe");
        }
    }

    private static @Nullable ObjectList<TileFusionCraftingInjector> selected(
                                                                             PackagedMachineOperation operation,
                                                                             Layout layout) {
        ListTag positions = operation.progress().getList("selected", Tag.TAG_LONG);
        var byPosition = new Object2ObjectLinkedOpenHashMap<BlockPos, TileFusionCraftingInjector>();
        for (var injector : layout.injectors()) byPosition.put(injector.getBlockPos(), injector);
        var selected = new ObjectArrayList<TileFusionCraftingInjector>();
        for (int index = 0; index < positions.size(); index++) {
            BlockPos position = BlockPos.of(((LongTag) positions.get(index)).getAsLong());
            var injector = byPosition.get(position);
            if (injector == null || selected.contains(injector)) return null;
            selected.add(injector);
        }
        return selected;
    }

    private static ListTag positions(ObjectList<TileFusionCraftingInjector> injectors) {
        var positions = new ListTag();
        for (var injector : injectors) positions.add(LongTag.valueOf(injector.getBlockPos().asLong()));
        return positions;
    }

    private static boolean matchesPositions(ListTag expected, ObjectList<TileFusionCraftingInjector> injectors) {
        if (expected.size() != injectors.size()) return false;
        var positions = new LongOpenHashSet();
        for (var injector : injectors) positions.add(injector.getBlockPos().asLong());
        for (int index = 0; index < expected.size(); index++) {
            if (!positions.remove(((LongTag) expected.get(index)).getAsLong())) return false;
        }
        return positions.isEmpty();
    }

    private static boolean matchesCoreInjectors(TileFusionCraftingCore core,
                                                ObjectList<TileFusionCraftingInjector> injectors) {
        if (core.getInjectors().size() != injectors.size()) return false;
        var expected = new LongOpenHashSet();
        for (var injector : injectors) expected.add(injector.getBlockPos().asLong());
        for (var injector : core.getInjectors()) {
            if (!(injector instanceof TileFusionCraftingInjector tile) ||
                    !expected.remove(tile.getBlockPos().asLong()))
                return false;
        }
        return expected.isEmpty();
    }

    private static @Nullable Layout discover(ServerLevel level, BlockPos position) {
        if (!level.isLoaded(position) || !(level.getBlockEntity(position) instanceof TileFusionCraftingCore core)) {
            return null;
        }
        int range = DEConfig.fusionInjectorRange;
        if (!loaded(level, position, range)) return null;
        var seen = new LongOpenHashSet();
        var injectors = new ObjectArrayList<TileFusionCraftingInjector>();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    if (!axisCandidate(x, y, z)) continue;
                    BlockPos injectorPosition = position.offset(x, y, z);
                    if (!seen.add(injectorPosition.asLong()) ||
                            !(level.getBlockEntity(injectorPosition) instanceof TileFusionCraftingInjector injector))
                        continue;
                    if (Utils.getCardinalDistance(injectorPosition, position) <= DEConfig.fusionInjectorMinDist ||
                            Direction.getNearest(x, y, z) != injector.getRotation().getOpposite() ||
                            obstructed(level, injectorPosition, position, injector.getRotation()))
                        continue;
                    BlockPos linkedCore = injector.corePos.get().getPos();
                    // An injector may retain a stale link to another core within the scan range.
                    // It is not part of this structure and must not invalidate the current core.
                    if (linkedCore.getY() != -9999 && !linkedCore.equals(position)) continue;
                    injectors.add(injector);
                }
            }
        }
        injectors.sort(Comparator.comparingLong(injector -> injector.getBlockPos().asLong()));
        return new Layout(core, injectors);
    }

    private static boolean axisCandidate(int x, int y, int z) {
        return Math.abs(y) <= 1 && Math.abs(z) <= 1 ||
                Math.abs(x) <= 1 && Math.abs(z) <= 1 ||
                Math.abs(x) <= 1 && Math.abs(y) <= 1;
    }

    private static boolean loaded(ServerLevel level, BlockPos position, int range) {
        int minimumX = (position.getX() - range) >> 4;
        int maximumX = (position.getX() + range) >> 4;
        int minimumZ = (position.getZ() - range) >> 4;
        int maximumZ = (position.getZ() + range) >> 4;
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) if (!level.hasChunk(x, z)) return false;
        }
        return true;
    }

    private static boolean obstructed(ServerLevel level, BlockPos injector, BlockPos core, Direction facing) {
        int distance = FacingUtils.distanceInDirection(injector, core, facing);
        if (distance <= 1) return false;
        for (BlockPos position : BlockPos.betweenClosed(
                injector.relative(facing), injector.relative(facing, distance - 1))) {
            if (!level.isEmptyBlock(position) &&
                    (level.getBlockState(position).canOcclude() ||
                            level.getBlockEntity(position) instanceof TileFusionCraftingInjector))
                return true;
        }
        return false;
    }

    private record Layout(TileFusionCraftingCore core, ObjectList<TileFusionCraftingInjector> injectors) {

        boolean empty() {
            if (!this.core.getCatalystStack().isEmpty() || !this.core.getOutputStack().isEmpty()) return false;
            return this.injectors.stream().allMatch(injector -> injector.getInjectorStack().isEmpty());
        }
    }
}
